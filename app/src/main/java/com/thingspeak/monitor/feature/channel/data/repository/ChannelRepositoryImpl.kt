package com.thingspeak.monitor.feature.channel.data.repository

import com.thingspeak.monitor.core.network.ThingSpeakApiService
import com.thingspeak.monitor.feature.channel.data.local.ChannelFeedDao
import com.thingspeak.monitor.feature.channel.data.mapper.toDomain
import com.thingspeak.monitor.feature.channel.data.mapper.toEntity
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.alert.data.local.AlertDao
import com.thingspeak.monitor.feature.alert.data.local.AlertEntity
import com.thingspeak.monitor.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ChannelRepository] using the Offline-First pattern.
 *
 * 1. [observeFeed] — returns reactive Flow from Room (immediate local data).
 * 2. [refreshFeed] — fetches latest data from API and saves to Room.
 *    Flow from point (1) automatically emits new values after saving.
 * 3. [getHistoricalFeed] — fetches data for a date range directly from API (not cached).
 */
@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val api: ThingSpeakApiService,
    private val feedDao: ChannelFeedDao,
    private val alertDao: AlertDao,
    private val firedAlertDao: com.thingspeak.monitor.feature.alert.data.local.FiredAlertDao,
    private val channelPrefs: ChannelPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ChannelRepository {

    override fun observeFeed(channelId: Long): Flow<List<FeedEntry>> {
        return feedDao.observeFeedEntries(channelId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeChannel(channelId: Long): Flow<com.thingspeak.monitor.feature.channel.domain.model.Channel?> {
        return channelPrefs.observe().map { channels ->
            channels.find { it.id == channelId }?.let { saved ->
                com.thingspeak.monitor.feature.channel.domain.model.Channel(
                    id = saved.id,
                    name = saved.name,
                    apiKey = saved.apiKey,
                    fieldNames = saved.fieldNames,
                    lastSyncStatus = try {
                        com.thingspeak.monitor.feature.channel.domain.model.SyncStatus.valueOf(saved.lastSyncStatus)
                    } catch (e: Exception) {
                        com.thingspeak.monitor.feature.channel.domain.model.SyncStatus.NONE
                    },
                    widgetBgColorHex = saved.widgetBgColorHex,
                    widgetTransparency = saved.widgetTransparency,
                    widgetFontSize = saved.widgetFontSize,
                    isGlassmorphismEnabled = saved.isGlassmorphismEnabled,
                    chartRounding = saved.chartRounding,
                    chartProcessingType = saved.chartProcessingType,
                    chartProcessingPeriod = saved.chartProcessingPeriod,
                    preferredChartFields = saved.preferredChartFields,
                    lastSyncTime = saved.lastSyncTime,
                    widgetVisibleFields = saved.widgetVisibleFields
                )
            }
        }
    }

    override suspend fun updateChannel(channel: com.thingspeak.monitor.feature.channel.domain.model.Channel) {
        withContext(ioDispatcher) {
            val savedChannels = channelPrefs.observe().first()
            savedChannels.find { it.id == channel.id }?.let { existing ->
                channelPrefs.save(
                    existing.copy(
                        name = channel.name,
                        apiKey = channel.apiKey,
                        fieldNames = channel.fieldNames,
                        widgetBgColorHex = channel.widgetBgColorHex,
                        widgetTransparency = channel.widgetTransparency,
                        widgetFontSize = channel.widgetFontSize,
                        isGlassmorphismEnabled = channel.isGlassmorphismEnabled,
                        chartRounding = channel.chartRounding,
                        chartProcessingType = channel.chartProcessingType,
                        chartProcessingPeriod = channel.chartProcessingPeriod,
                        preferredChartFields = channel.preferredChartFields
                    )
                )
            }
        }
    }

    override suspend fun refreshFeed(channelId: Long, apiKey: String?, results: Int) {
        withContext(ioDispatcher) {
            val startTime = System.currentTimeMillis()
            var lastException: Exception? = null
            
            // Retry loop for 429 (Rate Limit) - up to 15 seconds as requested
            while (System.currentTimeMillis() - startTime < 15000) {
                try {
                    val response = api.getChannelFeed(channelId = channelId, apiKey = apiKey, results = results)
                    
                    if (!response.isSuccessful) {
                        if (response.code() == 429) {
                            val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 2
                            lastException = RateLimitException(retryAfter)
                            kotlinx.coroutines.delay(retryAfter * 1000L)
                            continue // Retry
                        }
                        throw retrofit2.HttpException(response)
                    }

                    val body = response.body() ?: throw IllegalStateException("Empty response body")
                    val entities = body.feeds.map { it.toEntity(channelId) }

                    if (entities.isNotEmpty()) {
                        feedDao.upsertFeed(entities)
                    }

                    // Update channel metadata with SUCCESS status using merge pattern to PRESERVE widget styles
                    val channelDomain = body.channel.toDomain(apiKey)
                    val existingChannels = channelPrefs.observe().first()
                    val existing = existingChannels.find { it.id == channelId }
                    
                    val updatedChannel = (existing ?: ChannelPreferences.SavedChannel(id = channelId, name = channelDomain.name)).copy(
                        name = channelDomain.name,
                        apiKey = apiKey,
                        fieldNames = channelDomain.fieldNames,
                        lastSyncStatus = "SUCCESS",
                        lastSyncTime = System.currentTimeMillis()
                    )
                    channelPrefs.save(updatedChannel)
                    return@withContext // Success!
                } catch (e: Exception) {
                    lastException = e
                    if (e is RateLimitException) {
                        // Delay already handled in 429 check above, but for safety:
                        continue
                    }
                    if (e is java.io.IOException) {
                        // Network error - worth a quick retry? Maybe just once or twice.
                        // For now, let's treat network errors as fatal to avoid infinite loops on no-connection
                        break 
                    }
                    throw e // Other errors are fatal
                }
            }
            // If we reached here, it means we timed out or had a fatal error
            val e = lastException ?: IllegalStateException("Timeout after 15s")
            
            val status = when {
                e is RateLimitException -> "ERROR_API" // 429
                e is retrofit2.HttpException && e.code() == 401 -> "ERROR_AUTH"
                e is retrofit2.HttpException && e.code() == 403 -> "ERROR_AUTH"
                e is retrofit2.HttpException && e.code() == 429 -> "ERROR_API"
                e is java.io.IOException -> "ERROR_NETWORK"
                else -> "ERROR_API"
            }
            // Update only the status in DataStore
            try {
                val savedChannels: List<ChannelPreferences.SavedChannel> = channelPrefs.observe().first()
                savedChannels.find { it.id == channelId }?.let { existing ->
                    channelPrefs.save(existing.copy(lastSyncStatus = status))
                }
            } catch (_: Exception) {
                // Ignore secondary errors in error handler
            }
            throw e
        }
    }

    override suspend fun getHistoricalFeed(
        channelId: Long,
        apiKey: String?,
        start: String,
        end: String,
        average: Int?,
    ): List<FeedEntry> = withContext(ioDispatcher) {
        val response = api.getChannelFeedByDateRange(
            channelId = channelId,
            start = start,
            end = end,
            apiKey = apiKey,
            average = average
        )
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
            android.util.Log.e("ChannelRepository", "API Error: $errorMsg")
            throw retrofit2.HttpException(response)
        }
        
        return@withContext response.body()?.feeds?.map { dto ->
            dto.toEntity(channelId).toDomain()
        } ?: emptyList()
    }

    override fun observeAlerts(channelId: Long): Flow<List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold>> {
        return alertDao.observeAlertsForChannel(channelId).map { entities ->
            entities.map { entity ->
                com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold(
                    channelId = entity.channelId,
                    fieldNumber = entity.fieldNumber,
                    fieldName = entity.fieldName,
                    minValue = entity.minValue,
                    maxValue = entity.maxValue,
                    isEnabled = entity.isEnabled
                )
            }
        }
    }

    override suspend fun saveAlert(alert: com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold) {
        alertDao.insertAlert(
            AlertEntity(
                channelId = alert.channelId,
                fieldNumber = alert.fieldNumber,
                fieldName = alert.fieldName,
                minValue = alert.minValue,
                maxValue = alert.maxValue,
                isEnabled = alert.isEnabled
            )
        )
    }

    override suspend fun deleteAlert(alert: com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold) {
        alertDao.deleteSpecificAlert(alert.channelId, alert.fieldNumber)
    }

    override suspend fun refreshAll() {
        // Implementation will iterate over all saved channels and refresh them
        // This is a bridge to DataSyncWorker logic
        val savedChannels: List<ChannelPreferences.SavedChannel> = channelPrefs.observe().first()
        savedChannels.forEach { channel: ChannelPreferences.SavedChannel ->
            try {
                refreshFeed(channel.id, channel.apiKey)
            } catch (e: Exception) {
                // Individual refresh failure shouldn't stop others
            }
        }
    }

    override suspend fun clearCache() = withContext(ioDispatcher) {
        // Atomic wipe of all data layers
        channelPrefs.clearAll()
        feedDao.deleteAll()
        alertDao.deleteAll()
        firedAlertDao.deleteAll()
    }

    override suspend fun removeChannel(channelId: Long) = withContext(ioDispatcher) {
        channelPrefs.remove(channelId)
        feedDao.deleteByChannel(channelId)
        alertDao.deleteAlertsForChannel(channelId)
        firedAlertDao.deleteForChannel(channelId)
    }

    override suspend fun searchChannels(query: String, page: Int): List<com.thingspeak.monitor.feature.channel.domain.model.Channel> = withContext(ioDispatcher) {
        val response = api.searchPublicChannels(query = query, page = page)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
        
        return@withContext response.body()?.channels?.map { dto ->
            dto.toDomain()
        } ?: emptyList()
    }

    override suspend fun deleteOldEntries(dateCutoff: String) = withContext(ioDispatcher) {
        feedDao.deleteOldEntries(dateCutoff)
    }
}

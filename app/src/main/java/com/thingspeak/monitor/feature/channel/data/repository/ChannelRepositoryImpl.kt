package com.thingspeak.monitor.feature.channel.data.repository

import com.thingspeak.monitor.core.network.ThingSpeakApiService
import com.thingspeak.monitor.feature.channel.data.local.ChannelFeedDao
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleDao
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
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.Channel
import com.thingspeak.monitor.feature.channel.domain.model.SyncStatus
import com.thingspeak.monitor.feature.channel.domain.model.AlertRule
import com.thingspeak.monitor.feature.alert.domain.model.FiredAlert
import com.thingspeak.monitor.feature.alert.data.local.FiredAlertEntity
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity
import retrofit2.HttpException
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
    private val alertRuleDao: AlertRuleDao,
    private val firedAlertDao: com.thingspeak.monitor.feature.alert.data.local.FiredAlertDao,
    private val channelPrefs: ChannelPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ChannelRepository {

    override fun observeFeed(channelId: Long): Flow<List<FeedEntry>> {
        return feedDao.observeFeedEntries(channelId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getLatestFeedEntry(channelId: Long): FeedEntry? =
        withContext(ioDispatcher) {
            feedDao.getLatestEntry(channelId)?.toDomain()
        }

    override fun observeChannelList(): Flow<List<Channel>> {
        return channelPrefs.observe().map { channels ->
            channels.map { it.toDomain() }
        }
    }

    override fun observeChannel(channelId: Long): Flow<com.thingspeak.monitor.feature.channel.domain.model.Channel?> {
        return channelPrefs.observe().map { channels ->
            channels.find { it.id == channelId }?.let { saved ->
                saved.toDomain()
            }
        }
    }

    private fun SavedChannel.toDomain(): Channel = Channel(
        id = id,
        name = name,
        apiKey = apiKey,
        fieldNames = fieldNames,
        widgetBgColorHex = widgetBgColorHex,
        widgetTransparency = widgetTransparency,
        widgetFontSize = widgetFontSize,
        isGlassmorphismEnabled = isGlassmorphismEnabled,
        chartRounding = chartRounding,
        chartProcessingType = chartProcessingType,
        chartProcessingPeriod = chartProcessingPeriod,
        chartField = chartField,
        preferredChartFields = preferredChartFields,
        chartType = chartType,
        chartResults = chartResults,
        chartColor = chartColor ?: "#2196F3",
        chartBgColor = chartBgColor ?: "#FFFFFF",
        fieldColors = fieldColors,
        fieldYMin = fieldYMin,
        fieldYMax = fieldYMax,
        textColor = textColor ?: "#000000",
        widgetVisibleFields = widgetVisibleFields,
        displayNameMode = displayNameMode,
        displayFieldMode = displayFieldMode,
        lastProcessedEntryId = lastProcessedEntryId,
        lastSyncStatus = try { SyncStatus.valueOf(lastSyncStatus) } catch (e: Exception) { SyncStatus.NONE },
        lastSyncTime = lastSyncTime,
        chartTimespan = chartTimespan,
        isNormalized = isNormalized,
        isMergingEnabled = isMergingEnabled,
        drawingStyle = drawingStyle,
        timezone = timezone
    )

    private fun Channel.toSaved(): SavedChannel = SavedChannel(
        id = id,
        name = name,
        apiKey = apiKey,
        fieldNames = fieldNames,
        widgetBgColorHex = widgetBgColorHex,
        widgetTransparency = widgetTransparency,
        widgetFontSize = widgetFontSize,
        isGlassmorphismEnabled = isGlassmorphismEnabled,
        chartRounding = chartRounding,
        chartProcessingType = chartProcessingType,
        chartProcessingPeriod = chartProcessingPeriod,
        chartField = chartField,
        preferredChartFields = preferredChartFields,
        chartType = chartType,
        chartResults = chartResults,
        chartColor = chartColor,
        chartBgColor = chartBgColor,
        fieldColors = fieldColors,
        fieldYMin = fieldYMin,
        fieldYMax = fieldYMax,
        textColor = textColor,
        widgetVisibleFields = widgetVisibleFields,
        displayNameMode = displayNameMode,
        displayFieldMode = displayFieldMode,
        lastProcessedEntryId = lastProcessedEntryId,
        lastSyncStatus = lastSyncStatus.name,
        lastSyncTime = lastSyncTime,
        chartTimespan = chartTimespan,
        isNormalized = isNormalized,
        isMergingEnabled = isMergingEnabled,
        drawingStyle = drawingStyle,
        timezone = timezone
    )

    override suspend fun updateChannel(channel: Channel) {
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
                        chartField = channel.chartField,
                        preferredChartFields = channel.preferredChartFields,
                        chartType = channel.chartType,
                        chartResults = channel.chartResults,
                        chartColor = channel.chartColor,
                        chartBgColor = channel.chartBgColor,
                        fieldColors = channel.fieldColors,
                        fieldYMin = channel.fieldYMin,
                        fieldYMax = channel.fieldYMax,
                        textColor = channel.textColor,
                        widgetVisibleFields = channel.widgetVisibleFields,
                        chartTimespan = channel.chartTimespan,
                        isNormalized = channel.isNormalized,
                        isMergingEnabled = channel.isMergingEnabled,
                        drawingStyle = channel.drawingStyle,
                        timezone = channel.timezone
                    )
                )
            }
        }
    }

    override suspend fun refreshFeed(
        channelId: Long,
        apiKey: String?,
        results: Int?,
        chartTimespan: String?
    ) {
        withContext(ioDispatcher) {
            val startTime = System.currentTimeMillis()
            var lastException: Exception? = null
            
            // Map chartTimespan to API parameters
            // TS_DEBUG: Log context of refresh
            android.util.Log.d("TS_DEBUG", "refreshFeed START: id=$channelId, timespan=$chartTimespan, reqResults=$results")

            val finalResults = if (chartTimespan == null || chartTimespan == "1D") {
                // For 1D we prefer temporal limit 'days=1' over 'results' to avoid date regression,
                // but if results is specifically requested (e.g. from a widget with Analysis Depth), we use it.
                // UNLESS it's the default background refresh where we want most recent data.
                if (results == null) null else results
            } else {
                null // Use 'days' for longer periods
            }
            
            val finalDays = when (chartTimespan) {
                "7D" -> 7
                "30D" -> 30
                else -> 1 // Default to 1 day for background sync to avoid ancient data regression
            }

            android.util.Log.d("TS_DEBUG", "refreshFeed params: finalResults=$finalResults, finalDays=$finalDays")

            // Retry loop for 429 (Rate Limit) - up to 15 seconds as requested
            while (System.currentTimeMillis() - startTime < 15000) {
                try {
                    android.util.Log.v("TS_DEBUG", "refreshFeed calling API for $channelId...")
                    var response = api.getChannelFeed(
                        channelId = channelId,
                        apiKey = apiKey,
                        results = finalResults,
                        days = finalDays
                    )
                    
                    if (!response.isSuccessful) {
                        android.util.Log.w("TS_DEBUG", "refreshFeed API FAILED: code=${response.code()} for $channelId")
                        if (response.code() == 429) {
                            val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 2
                            lastException = RateLimitException(retryAfter)
                            android.util.Log.w("TS_DEBUG", "Rate Limited (429). Retrying after ${retryAfter}s...")
                            kotlinx.coroutines.delay(retryAfter * 1000L)
                            continue // Retry
                        }
                        
                        // FALLBACK for refresh: try results=1 if 100 failed
                        if (finalResults != 1) {
                            android.util.Log.w("TS_DEBUG", "refreshFeed falling back to results=1 for $channelId")
                            response = api.getChannelFeed(
                                channelId = channelId,
                                apiKey = apiKey,
                                results = 1,
                                days = null,
                                average = null
                            )
                        }
                        
                        if (!response.isSuccessful) {
                            throw Exception("API Error: ${response.code()}")
                        }
                    }

                    val body = response.body() ?: throw IllegalStateException("Empty response body")
                    val entities = body.feeds.map { it.toEntity(channelId) }

                    android.util.Log.d("TS_DEBUG", "refreshFeed received ${entities.size} entries for $channelId")

                    if (entities.isNotEmpty()) {
                        feedDao.upsertFeed(entities)
                        android.util.Log.v("TS_DEBUG", "refreshFeed database UPSERT complete for $channelId")
                    }

                    // Update channel metadata with SUCCESS status using merge pattern to PRESERVE widget styles
                    val channelDomain = body.channel.toDomain(apiKey)
                    val existingChannels = channelPrefs.observe().first()
                    val existing = existingChannels.find { it.id == channelId }
                    
                    // PROTECTION: If existing channel has a manual timezone override, and API returns null/empty, PRESERVE existing.
                    val finalTimezone = if (channelDomain.timezone.isNullOrBlank()) {
                        existing?.timezone
                    } else {
                        channelDomain.timezone
                    }

                    val updatedChannel = (existing ?: SavedChannel(id = channelId, name = channelDomain.name)).copy(
                        name = channelDomain.name,
                        apiKey = apiKey,
                        fieldNames = channelDomain.fieldNames,
                        lastSyncStatus = "SUCCESS",
                        lastSyncTime = System.currentTimeMillis(),
                        chartTimespan = chartTimespan ?: existing?.chartTimespan ?: "1D",
                        timezone = finalTimezone
                    )
                    channelPrefs.save(updatedChannel)
                    android.util.Log.i("TS_DEBUG", "refreshFeed SUCCESS for $channelId. Took ${System.currentTimeMillis() - startTime}ms")
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
                e is java.io.IOException -> "ERROR_NETWORK"
                else -> "ERROR_API"
            }
            // Update only the status in DataStore
            try {
                val existingChannels: List<SavedChannel> = channelPrefs.observe().first()
                existingChannels.find { it.id == channelId }?.let { existing ->
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
        start: String?,
        end: String?,
        average: Int?,
        results: Int?,
        days: Int?,
    ): List<FeedEntry> = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        // TS_DEBUG: Log exact API parameters for historical data
        android.util.Log.d("TS_DEBUG", "getHistoricalFeed START: id=$channelId, results=$results, days=$days, average=$average, start=$start, end=$end")
        
        val response = api.getChannelFeed(
            channelId = channelId,
            apiKey = apiKey,
            results = results,
            days = days,
            average = average,
            start = start,
            end = end
        )
        
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "HTTP ${response.code()}"
            android.util.Log.e("TS_DEBUG", "getHistoricalFeed API ERROR: id=$channelId, error=$errorMsg")
            throw Exception(errorMsg)
        }
        
        val feeds = response.body()?.feeds ?: emptyList()
        android.util.Log.d("TS_DEBUG", "getHistoricalFeed SUCCESS: id=$channelId, received ${feeds.size} items. Took ${System.currentTimeMillis() - startTime}ms")

        return@withContext feeds.map { dto ->
            dto.toEntity(channelId).toDomain()
        }
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

    override suspend fun getAlertsForChannel(channelId: Long): List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold> {
        return alertDao.getAlertsForChannel(channelId).map { entity ->
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

    override fun observeAlertRules(channelId: Long, appWidgetId: Int?): Flow<List<AlertRule>> {
        val flow = if (appWidgetId != null) {
            alertRuleDao.observeRulesForWidget(channelId, appWidgetId)
        } else {
            alertRuleDao.observeRulesForChannel(channelId)
        }
        
        return flow.map { entities ->
            entities.map { entity ->
                AlertRule(
                    id = entity.id,
                    channelId = entity.channelId,
                    appWidgetId = entity.appWidgetId,
                    fieldNumber = entity.fieldNumber,
                    condition = entity.condition,
                    thresholdValue = entity.thresholdValue,
                    isEnabled = entity.isEnabled
                )
            }
        }
    }

    override suspend fun getAlertRules(channelId: Long, appWidgetId: Int?): List<AlertRule> = withContext(ioDispatcher) {
        val entities = if (appWidgetId != null) {
            alertRuleDao.getRulesForWidget(channelId, appWidgetId)
        } else {
            alertRuleDao.getGlobalRulesForChannel(channelId)
        }
        
        entities.map { entity ->
            AlertRule(
                id = entity.id,
                channelId = entity.channelId,
                appWidgetId = entity.appWidgetId,
                fieldNumber = entity.fieldNumber,
                condition = entity.condition,
                thresholdValue = entity.thresholdValue,
                isEnabled = entity.isEnabled
            )
        }
    }

    override suspend fun saveAlertRule(rule: AlertRule) = withContext(ioDispatcher) {
        alertRuleDao.insertRule(
            AlertRuleEntity(
                id = rule.id,
                channelId = rule.channelId,
                appWidgetId = rule.appWidgetId,
                fieldNumber = rule.fieldNumber,
                condition = rule.condition,
                thresholdValue = rule.thresholdValue,
                isEnabled = rule.isEnabled
            )
        )
    }

    override suspend fun deleteAlertRule(rule: AlertRule) = withContext(ioDispatcher) {
        alertRuleDao.deleteRule(
            AlertRuleEntity(
                id = rule.id,
                channelId = rule.channelId,
                appWidgetId = rule.appWidgetId,
                fieldNumber = rule.fieldNumber,
                condition = rule.condition,
                thresholdValue = rule.thresholdValue,
                isEnabled = rule.isEnabled
            )
        )
    }

    override suspend fun deleteGlobalAlertRules(channelId: Long) = withContext(ioDispatcher) {
        alertRuleDao.deleteGlobalRulesForChannel(channelId)
    }

    override suspend fun getFiredAlert(channelId: Long, fieldNumber: Int): FiredAlert? {
        return firedAlertDao.getFiredAlert(channelId, fieldNumber)?.let { entity ->
            FiredAlert(
                channelId = entity.channelId,
                fieldNumber = entity.fieldNumber,
                lastFiredEntryId = entity.lastFiredEntryId,
                timestamp = entity.timestamp,
                lastFiredTimestamp = entity.lastFiredTimestamp,
                violationSignature = entity.violationSignature
            )
        }
    }

    override suspend fun saveFiredAlert(firedAlert: FiredAlert) {
        firedAlertDao.insertFiredAlert(
            FiredAlertEntity(
                channelId = firedAlert.channelId,
                fieldNumber = firedAlert.fieldNumber,
                lastFiredEntryId = firedAlert.lastFiredEntryId,
                timestamp = firedAlert.timestamp,
                lastFiredTimestamp = firedAlert.lastFiredTimestamp,
                violationSignature = firedAlert.violationSignature
            )
        )
    }

    override suspend fun deleteFiredAlert(channelId: Long, fieldNumber: Int) {
        firedAlertDao.deleteFiredAlert(channelId, fieldNumber)
    }

    override suspend fun refreshAll() {
        // Implementation will iterate over all saved channels and refresh them
        // This is a bridge to DataSyncWorker logic
        val savedChannels: List<SavedChannel> = channelPrefs.observe().first()
        for (channel in savedChannels) {
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

    override suspend fun searchChannels(query: String, page: Int): List<Channel> = withContext(ioDispatcher) {
        val response = api.searchPublicChannels(query = query, page = page)
        if (response.isSuccessful) {
            response.body()?.channels?.map { it.toDomain() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    override suspend fun getSyncInterval(): Long = withContext(ioDispatcher) {
        // Mocked or from settings if applicable
        60L
    }

    override suspend fun deleteOldEntries(dateCutoff: String) = withContext(ioDispatcher) {
        feedDao.deleteOldEntries(dateCutoff)
    }
}

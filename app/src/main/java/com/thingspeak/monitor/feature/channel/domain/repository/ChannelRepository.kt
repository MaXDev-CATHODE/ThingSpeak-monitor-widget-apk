package com.thingspeak.monitor.feature.channel.domain.repository

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import kotlinx.coroutines.flow.Flow

/**
 * Channel repository interface — Domain layer contract.
 *
 * Implementation resides in the Data layer.
 * Domain layer defines WHAT it wants, not HOW to do it.
 */
interface ChannelRepository {

    /** Observes channel feed entries as a reactive [Flow] stream, offline-first. */
    fun observeFeed(channelId: Long): Flow<List<FeedEntry>>

    /** Observes the list of all saved channels. */
    fun observeChannelList(): Flow<List<com.thingspeak.monitor.feature.channel.domain.model.Channel>>

    /** Observes channel metadata (name, fields) from local DataStore. */
    fun observeChannel(channelId: Long): Flow<com.thingspeak.monitor.feature.channel.domain.model.Channel?>

    /** Synchronizes data from ThingSpeak API and saves to Room. */
    suspend fun refreshFeed(channelId: Long, apiKey: String?, results: Int = 100)

    /** Updates channel settings in local DataStore. */
    suspend fun updateChannel(channel: com.thingspeak.monitor.feature.channel.domain.model.Channel)

    /** Fetches historical data in the date range (for charts). */
    suspend fun getHistoricalFeed(
        channelId: Long,
        apiKey: String?,
        start: String? = null,
        end: String? = null,
        average: Int? = null,
        results: Int? = null,
        days: Int? = null,
    ): List<FeedEntry>

    /** Observes alert thresholds for the channel. */
    fun observeAlerts(channelId: Long): Flow<List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold>>

    /** Gets all alert thresholds for a channel (one-shot). */
    suspend fun getAlertsForChannel(channelId: Long): List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold>

    /** Saves or updates an alert threshold. */
    suspend fun saveAlert(alert: com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold)

    /** Deletes an alert threshold. */
    suspend fun deleteAlert(alert: com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold)

    /** Observes advanced alert rules for the channel. */
    fun observeAlertRules(channelId: Long): Flow<List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>>

    /** Gets all advanced alert rules for a channel (one-shot). */
    suspend fun getAlertRulesForChannel(channelId: Long): List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>

    /** Gets a fired alert state for a specific field. */
    suspend fun getFiredAlert(channelId: Long, fieldNumber: Int): com.thingspeak.monitor.feature.alert.domain.model.FiredAlert?

    /** Saves a fired alert state. */
    suspend fun saveFiredAlert(firedAlert: com.thingspeak.monitor.feature.alert.domain.model.FiredAlert)

    /** Deletes a fired alert state. */
    suspend fun deleteFiredAlert(channelId: Long, fieldNumber: Int)

    /** Synchronizes all saved channels. */
    suspend fun refreshAll()

    /** Clears entire local database and channel settings. */
    suspend fun clearCache()

    /** Removes channel and all associated local data. */
    suspend fun removeChannel(channelId: Long)

    /** Searches for public channels on ThingSpeak. */
    suspend fun searchChannels(query: String, page: Int = 1): List<com.thingspeak.monitor.feature.channel.domain.model.Channel>

    /** Cleans up historical data older than the specified date. */
    suspend fun deleteOldEntries(dateCutoff: String)

    /** Gets the synchronization interval in seconds. */
    suspend fun getSyncInterval(): Long
}

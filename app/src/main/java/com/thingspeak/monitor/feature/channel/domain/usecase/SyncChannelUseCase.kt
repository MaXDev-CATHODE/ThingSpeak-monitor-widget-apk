package com.thingspeak.monitor.feature.channel.domain.usecase

import android.util.Log
import com.thingspeak.monitor.core.di.IoDispatcher
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.alert.domain.model.FiredAlert
import com.thingspeak.monitor.feature.channel.domain.model.*
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Orchestrates channel synchronization, alert evaluation, and notification triggering.
 * Unified logic (Agent 3.2) for both Periodic Worker and High Frequency Service.
 */
class SyncChannelUseCase @Inject constructor(
    private val repository: ChannelRepository,
    private val checkAlertRules: CheckAlertRulesUseCase,
    private val alertManager: AlertManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    data class Result(
        val channel: Channel,
        val latestEntry: FeedEntry,
        val allViolations: List<AlertRule>,
        val channelRules: List<AlertRule>,
        val hasNewData: Boolean,
        val alertStateChanged: Boolean,
        val error: Throwable? = null
    )

    suspend operator fun invoke(channel: Channel): Result = withContext(ioDispatcher) {
        try {
            // 1. Refresh data from API
            val resultsCount = (channel.chartProcessingPeriod.takeIf { it > 0 } ?: 24) * 60
            val maxResults = resultsCount.coerceAtMost(500)
            
            try {
                repository.refreshFeed(channel.id, channel.apiKey, maxResults)
            } catch (e: Exception) {
                Log.w("TS_DEBUG", "SyncChannelUseCase: Refresh failed for ${channel.id}, using cache: ${e.message}")
            }

            // 2. Load latest data
            val entries = repository.observeFeed(channel.id).firstOrNull() ?: emptyList()
            // Use a direct suspend query instead of entries.firstOrNull() to avoid a race condition
            // where observeFeed().firstOrNull() may return a stale Flow buffer emission from before
            // the upsert completed, causing the widget to display an outdated "Measured: HH:mm".
            val latestEntry = repository.getLatestFeedEntry(channel.id) ?: FeedEntry(0L, "—", emptyMap())
            
            val lastProcessedId = channel.lastProcessedEntryId
            val newEntries = entries.filter { it.entryId > lastProcessedId }
            
            // 3. Load UNIFIED global rules (appWidgetId = null)
            val channelRules = repository.getAlertRules(channel.id, null)
            
            // 4. Check ALL current violations (for visual indicators ▲/▼)
            val allViolations = checkAlertRules(latestEntry, channelRules)
            
            var anyAlertStateChanged = false
            val newViolationsToNotify = mutableListOf<AlertRule>()
            
            // 5. DEBOUNCING LOGIC: manage state per field
            val fieldsWithRules = channelRules.map { it.fieldNumber }.toSet()
            
            fieldsWithRules.forEach { fieldNum ->
                val currentFieldViolations = allViolations.filter { it.fieldNumber == fieldNum }
                // Unified signature format: condition:threshold|...
                val currentSignature = if (currentFieldViolations.isEmpty()) "" 
                    else currentFieldViolations.sortedBy { "${it.condition}:${it.thresholdValue}" }
                                             .joinToString("|") { "${it.condition}:${it.thresholdValue}" }
                
                val firedAlert = repository.getFiredAlert(channel.id, fieldNum)
                
                if (currentSignature.isNotEmpty()) {
                    if (firedAlert == null || firedAlert.violationSignature != currentSignature) {
                        Log.i("TS_DEBUG", "SyncChannelUseCase: NEW ALARM for channel ${channel.id}, field $fieldNum: $currentSignature")
                        newViolationsToNotify.addAll(currentFieldViolations)
                        repository.saveFiredAlert(FiredAlert(
                            channelId = channel.id,
                            fieldNumber = fieldNum,
                            lastFiredEntryId = latestEntry.entryId,
                            timestamp = System.currentTimeMillis(),
                            violationSignature = currentSignature
                        ))
                        anyAlertStateChanged = true
                    } else {
                        Log.v("TS_DEBUG", "SyncChannelUseCase: Alarm for field $fieldNum debounced.")
                    }
                } else if (firedAlert != null) {
                    Log.i("TS_DEBUG", "SyncChannelUseCase: Alarm for field $fieldNum CLEARED for channel ${channel.id}")
                    repository.deleteFiredAlert(channel.id, fieldNum)
                    anyAlertStateChanged = true
                }
            }

            // 6. Fire system notification (sound/vibrate) only if new violations detected
            if (newViolationsToNotify.isNotEmpty()) {
                alertManager.fireRuleAlert(channel.id, newViolationsToNotify, channel.fieldNames)
            }

            // 7. Update processing state
            var updatedChannel = channel
            if (newEntries.isNotEmpty()) {
                updatedChannel = channel.copy(lastProcessedEntryId = latestEntry.entryId)
                repository.updateChannel(updatedChannel)
            }

            Result(
                channel = updatedChannel,
                latestEntry = latestEntry,
                allViolations = allViolations,
                channelRules = channelRules,
                hasNewData = newEntries.isNotEmpty(),
                alertStateChanged = anyAlertStateChanged
            )
        } catch (e: Exception) {
            Log.e("TS_DEBUG", "SyncChannelUseCase: CRITICAL ERROR for ${channel.id}", e)
            Result(
                channel = channel,
                latestEntry = FeedEntry(0L, "—", emptyMap()),
                allViolations = emptyList(),
                channelRules = emptyList(),
                hasNewData = false,
                alertStateChanged = false,
                error = e
            )
        }
    }
}

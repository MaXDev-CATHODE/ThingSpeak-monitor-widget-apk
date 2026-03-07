package com.thingspeak.monitor.core.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.channel.data.mapper.toDomain
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.usecase.CheckAlertThresholdsUseCase
import com.thingspeak.monitor.feature.widget.ThingSpeakGlanceWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity

/**
 * Full data synchronization worker for all saved channels.
 *
 * Performs:
 * 1. Refreshes feed data for every saved channel via [ChannelRepository].
 * 2. Checks alert thresholds for the latest entry of each channel.
 * 3. Logs any threshold violations (notification handling is Agent 4's responsibility).
 * 4. Updates all Glance widgets with fresh data.
 */
@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ChannelRepository,
    private val channelPrefs: ChannelPreferences,
    private val alertDao: com.thingspeak.monitor.feature.alert.data.local.AlertDao,
    private val alertRuleDao: com.thingspeak.monitor.feature.channel.data.local.AlertRuleDao,
    private val firedAlertDao: com.thingspeak.monitor.feature.alert.data.local.FiredAlertDao,
    private val checkAlerts: CheckAlertThresholdsUseCase,
    private val alertManager: AlertManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val channels = channelPrefs.observe().first()
            if (channels.isEmpty()) {
                Log.i(TAG, "No saved channels — skipping sync")
                return Result.success()
            }

            kotlinx.coroutines.coroutineScope {
                channels.map { channel ->
                    async { syncChannel(channel) }
                }.awaitAll()
            }

            updateAllWidgets()
            
            // Database Retention: Cleanup entries older than 90 days
            val cutoffDate = Instant.now().minus(90, ChronoUnit.DAYS).toString()
            repository.deleteOldEntries(cutoffDate)
            Log.i(TAG, "Completed database retention cleanup: removed entries older than $cutoffDate")

            Result.success()
        } catch (e: Exception) {
            if (e is com.thingspeak.monitor.feature.channel.data.repository.RateLimitException) {
                Log.w(TAG, "Rate limited by ThingSpeak. Retry using backoff.")
            } else {
                Log.e(TAG, "Data sync failed", e)
            }
            Result.retry()
        }
    }

    private suspend fun syncChannel(channel: ChannelPreferences.SavedChannel) {
        try {
            Log.d(TAG, "===============================")
            Log.d(TAG, "syncChannel called for channel: ${channel.id}")
            // Fetch fresh data for the channel, scaling results based on desired chart timespan
            val chartProcessingPeriod = channel.chartProcessingPeriod.takeIf { it > 0 } ?: 24
            val resultsCount = (chartProcessingPeriod * 60).coerceAtMost(4000) // Up to 4000 points
            repository.refreshFeed(channel.id, channel.apiKey, resultsCount)

            val entries = repository.observeFeed(channel.id).first()
            if (entries.isEmpty()) {
                Log.d(TAG, "No entries found for channel ${channel.id}")
                return
            }
            
            // entries are sorted DESC (newest first), so .first() = latest
            val latestEntry = entries.first()
            Log.d(TAG, "Latest entry ID=${latestEntry.entryId}, fields=${latestEntry.fields}")
            val newEntries = entries.filter { it.entryId > channel.lastProcessedEntryId }
                .sortedBy { it.entryId }
            
            Log.d(TAG, "Found ${newEntries.size} new entries since last processed ID ${channel.lastProcessedEntryId}")
            
            val entriesToCheck = if (newEntries.isNotEmpty()) newEntries else listOf(latestEntry)

            // 1. Get enabled thresholds and rules
            val channelAlerts = alertDao.getAlertsForChannel(channel.id)
                .filter { it.isEnabled }
            val advancedRules = alertRuleDao.getRulesForChannel(channel.id)
                .filter { it.isEnabled }
                
            val allFieldNumbers = (channelAlerts.map { it.fieldNumber } + advancedRules.map { it.fieldNumber }).distinct()
            val violationsToNotify = mutableListOf<AlertThreshold>()

            // 2. Process each field for edge-triggered alerts based on the LATEST entry
            allFieldNumbers.forEach { fieldNum ->
                val fieldRules = channelAlerts.filter { it.fieldNumber == fieldNum }.map { entity ->
                    AlertThreshold(
                        channelId = entity.channelId,
                        fieldNumber = entity.fieldNumber,
                        fieldName = entity.fieldName,
                        minValue = entity.minValue,
                        maxValue = entity.maxValue,
                        isEnabled = entity.isEnabled,
                    )
                }
                val fieldAdvancedRules = advancedRules.filter { it.fieldNumber == fieldNum }
                
                // Find the latest entry that has a non-null value for THIS specific field
                // entries are sorted DESC (newest first)
                val latestEntryForField = entries.firstOrNull { it.fields[fieldNum] != null } ?: latestEntry
                val latestFieldValue = latestEntryForField.fields[fieldNum]?.toDoubleOrNull()
                
                // Check if the data is stale
                val entryAgeEntries = latestEntry.entryId - latestEntryForField.entryId
                val isStale = entryAgeEntries > 5
                
                Log.d(TAG, "Evaluating field $fieldNum for channel ${channel.id}: latestValue=$latestFieldValue (from entry ${latestEntryForField.entryId}, entriesBehind=$entryAgeEntries, stale=$isStale)")

                if (isStale) {
                    val fired = firedAlertDao.getFiredAlert(channel.id, fieldNum)
                    if (fired != null) {
                        Log.d(TAG, "Resetting stale fired alert for field $fieldNum")
                        firedAlertDao.deleteFiredAlert(channel.id, fieldNum)
                    }
                    return@forEach
                }

                // Identify if the LATEST value violates ANY rule for this field
                val currentFieldViolations = mutableSetOf<AlertThreshold>()
                if (fieldRules.isNotEmpty()) {
                    val legacyViolations = checkAlerts(latestEntryForField, fieldRules)
                    if (legacyViolations.isNotEmpty()) {
                        Log.d(TAG, "Legacy rule violation detected for field $fieldNum")
                        currentFieldViolations.addAll(legacyViolations)
                    }
                }
                
                fieldAdvancedRules.forEach { rule ->
                    val fieldValueStr = latestEntryForField.fields[rule.fieldNumber]
                    val fieldValue = fieldValueStr?.toDoubleOrNull()
                    Log.d(TAG, "Evaluating rule ID ${rule.id} (Field ${rule.fieldNumber}): value=$fieldValue, condition=${rule.condition} ${rule.thresholdValue}")
                    if (fieldValue != null) {
                        val isViolated = when (rule.condition) {
                            "GREATER_THAN" -> fieldValue > rule.thresholdValue
                            "LESS_THAN" -> fieldValue < rule.thresholdValue
                            else -> false
                        }
                        if (isViolated) {
                            Log.d(TAG, "!! VIOLATION detected for rule ID ${rule.id}")
                            currentFieldViolations.add(AlertThreshold(
                                channelId = channel.id,
                                fieldNumber = rule.fieldNumber,
                                fieldName = channel.fieldNames[rule.fieldNumber] ?: "Field ${rule.fieldNumber}",
                                minValue = if (rule.condition == "LESS_THAN") rule.thresholdValue else null,
                                maxValue = if (rule.condition == "GREATER_THAN") rule.thresholdValue else null,
                                isEnabled = true
                            ))
                        }
                    }
                }

                val fired = firedAlertDao.getFiredAlert(channel.id, fieldNum)
                
                // Build a signature from current violations to detect changes in violation TYPE
                val currentSignature = currentFieldViolations
                    .sortedBy { "${it.minValue}:${it.maxValue}" }
                    .joinToString("|") { "${it.minValue}:${it.maxValue}" }
                
                Log.d(TAG, "Violations for field $fieldNum: ${currentFieldViolations.size}. Signature='$currentSignature'. Previous fired state: $fired (prevSig='${fired?.violationSignature}')")

                if (currentFieldViolations.isNotEmpty()) {
                    if (fired == null || fired.violationSignature != currentSignature) {
                        // NEW violation or CHANGED violation type — notify!
                        Log.d(TAG, ">> TRIGGERING ALERT: fired=${fired == null}, sigChanged=${fired?.violationSignature != currentSignature}")
                        violationsToNotify.addAll(currentFieldViolations)
                        firedAlertDao.insertFiredAlert(
                            com.thingspeak.monitor.feature.alert.data.local.FiredAlertEntity(
                                channelId = channel.id,
                                fieldNumber = fieldNum,
                                lastFiredEntryId = latestEntry.entryId,
                                lastFiredTimestamp = System.currentTimeMillis(),
                                violationSignature = currentSignature
                            )
                        )
                    }
                } else {
                    if (fired != null) {
                        // Edge triggered: Transition Bad -> Normal. Reset alert state.
                        firedAlertDao.deleteFiredAlert(channel.id, fieldNum)
                    }
                }
            }

            // 3. Fire notifications for any new violations found
            if (violationsToNotify.isNotEmpty()) {
                alertManager.fireAlert(channel.id, violationsToNotify)
            }

            // 4. Update the sync metadata and mark entries as processed
            if (newEntries.isNotEmpty()) {
                val latestEntryId = newEntries.last().entryId
                channelPrefs.save(channel.copy(
                    lastSyncTime = System.currentTimeMillis(),
                    lastProcessedEntryId = latestEntryId
                ))
                Log.d(TAG, "Synced channel ${channel.id}, processed new entries up to $latestEntryId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync channel ${channel.id}", e)
            if (e is com.thingspeak.monitor.feature.channel.data.repository.RateLimitException) {
                throw e
            }
        }
    }

    private suspend fun updateAllWidgets() {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)
            
            // List of all widget classes to update
            val widgetClasses = listOf(
                ThingSpeakGlanceWidget::class.java,
                com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java
            )

            widgetClasses.forEach { widgetClass ->
                val glanceIds = manager.getGlanceIds(widgetClass)
                Log.d(TAG, "updateAllWidgets: found ${glanceIds.size} widgets of type ${widgetClass.simpleName}")
                
                for (id in glanceIds) {
                    // Clear refreshing state if set
                    try {
                        androidx.glance.appwidget.state.updateAppWidgetState(applicationContext, com.thingspeak.monitor.feature.widget.WidgetPreferencesStateDefinition, id) { prefs ->
                            val mutablePrefs = prefs.toMutablePreferences()
                            mutablePrefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] = false
                            mutablePrefs
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "updateAllWidgets: Failed to clear is_refreshing state for $id", e)
                    }

                    try {
                        // Dynamically create an instance or call update if we can
                        // For Glance, we usually just need to call update on an instance
                        if (widgetClass == ThingSpeakGlanceWidget::class.java) {
                            ThingSpeakGlanceWidget().update(applicationContext, id)
                        } else if (widgetClass == com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java) {
                            com.thingspeak.monitor.feature.widget.ValueGridWidget().update(applicationContext, id)
                        }
                        Log.d(TAG, "updateAllWidgets: triggered widget.update() for $id")
                    } catch (e: Exception) {
                        Log.e(TAG, "updateAllWidgets: Failed to trigger update for $id", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update widgets", e)
        }
    }

    companion object {
        private const val TAG = "DataSyncWorker"

        fun constraints(): androidx.work.Constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Relaxed for better reliability on emulators/low battery
            .build()

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = androidx.work.PeriodicWorkRequestBuilder<DataSyncWorker>(
                intervalMinutes, java.util.concurrent.TimeUnit.MINUTES
            )
            .setConstraints(constraints())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Scheduled Periodic Work: DataSyncWorker every $intervalMinutes minutes")
        }

        const val WORK_NAME = "DataSyncWorker"

        fun runOnce(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<DataSyncWorker>()
                .setConstraints(constraints())
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }
}


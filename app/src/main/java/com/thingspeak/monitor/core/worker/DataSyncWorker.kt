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
            // Fetch fresh data for the channel (default results count for history)
            repository.refreshFeed(channel.id, channel.apiKey)

            val entries = repository.observeFeed(channel.id).first()
            val latestEntry = entries.firstOrNull() ?: return

            // 1. Check legacy AlertThresholds (Min/Max range)
            val channelAlerts = alertDao.getAlertsForChannel(channel.id)
                .filter { it.isEnabled }
                .map { entity ->
                    AlertThreshold(
                        channelId = entity.channelId,
                        fieldNumber = entity.fieldNumber,
                        fieldName = entity.fieldName,
                        minValue = entity.minValue,
                        maxValue = entity.maxValue,
                        isEnabled = entity.isEnabled,
                    )
                }

            val violations = if (channelAlerts.isNotEmpty()) {
                checkAlerts(latestEntry, channelAlerts).toMutableList()
            } else mutableListOf()

            // 2. Check Advanced AlertRules (Single condition: GT/LT)
            val advancedRules = alertRuleDao.getRulesForChannel(channel.id).filter { it.isEnabled }
            advancedRules.forEach { rule ->
                val fieldValueStr = latestEntry.fields[rule.fieldNumber]
                val fieldValue = fieldValueStr?.toDoubleOrNull()
                if (fieldValue != null) {
                    val isViolated = when (rule.condition) {
                        "GREATER_THAN" -> fieldValue > rule.thresholdValue
                        "LESS_THAN" -> fieldValue < rule.thresholdValue
                        else -> false
                    }
                    if (isViolated) {
                        violations.add(
                            AlertThreshold(
                                channelId = channel.id,
                                fieldNumber = rule.fieldNumber,
                                fieldName = "Field ${rule.fieldNumber}",
                                minValue = if (rule.condition == "LESS_THAN") null else rule.thresholdValue,
                                maxValue = if (rule.condition == "GREATER_THAN") null else rule.thresholdValue,
                                isEnabled = true
                            )
                        )
                    }
                }
            }

            if (violations.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val THROTTLE_MS = 60L * 60L * 1000L // 60 min
            val newViolations = violations.filter { violation ->
                val fired = firedAlertDao.getFiredAlert(channel.id, violation.fieldNumber)
                if (fired == null) true else (currentTime - (fired.lastFiredTimestamp ?: 0L) > THROTTLE_MS)
            }
            if (newViolations.isNotEmpty()) {
                alertManager.fireAlert(channel.id, newViolations)
                newViolations.forEach { violation ->
                    firedAlertDao.insertFiredAlert(
                        com.thingspeak.monitor.feature.alert.data.local.FiredAlertEntity(
                            channelId = channel.id,
                            fieldNumber = violation.fieldNumber,
                            lastFiredEntryId = latestEntry.entryId,
                            lastFiredTimestamp = currentTime
                        )
                    )
                }
            }
        }

        channelPrefs.save(channel.copy(lastSyncTime = System.currentTimeMillis()))
            Log.d(TAG, "Synced channel ${channel.id}")
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
            .setRequiresBatteryNotLow(true)
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


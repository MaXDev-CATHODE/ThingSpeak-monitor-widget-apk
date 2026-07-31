package com.thingspeak.monitor.core.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.widget.ThingSpeakGlanceWidget
import com.thingspeak.monitor.feature.widget.WidgetChartGenerator
import com.thingspeak.monitor.feature.channel.domain.model.toSavedChannel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import com.thingspeak.monitor.core.datastore.SavedChannel
import java.util.concurrent.TimeUnit

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ChannelRepository,
    private val channelPrefs: ChannelPreferences,
    private val syncChannelUseCase: com.thingspeak.monitor.feature.channel.domain.usecase.SyncChannelUseCase,
    private val alertManager: AlertManager
) : CoroutineWorker(context, workerParams) {

    private val TAG = com.thingspeak.monitor.feature.widget.WIDGET_LOG_TAG

    override suspend fun doWork(): Result {
        android.util.Log.i(TAG, "DataSyncWorker START: periodic sync triggered")
        return try {
            val channels = channelPrefs.observe().first()
            if (channels.isEmpty()) {
                android.util.Log.v(TAG, "DataSyncWorker: No channels to sync, skipping.")
                return Result.success()
            }

            android.util.Log.d(TAG, "DataSyncWorker: Syncing ${channels.size} channels.")
            kotlinx.coroutines.coroutineScope {
                channels.map { channel ->
                    async { syncChannel(channel) }
                }.awaitAll()
            }

            updateAllWidgets()
            android.util.Log.i(TAG, "DataSyncWorker SUCCESS: All channels processed.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "DataSyncWorker CRITICALLY FAILED", e)
            Result.retry()
        }
    }

    private suspend fun syncChannel(channel: SavedChannel) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d(TAG, "syncChannel BEGIN: id=${channel.id} (${channel.name})")

        try {
            val result = syncChannelUseCase(channel.toDomain())
            
            if (result.error != null) {
                android.util.Log.w(TAG, "syncChannel RESULT: Error for ${channel.id}: ${result.error.message}")
            }

            // 1.  Generate chart bitmap (only if we have entries)
            val entries = repository.observeFeed(channel.id).first()
            val channelChartBase64: String? = if (entries.isNotEmpty()) {
                try {
                    val defaultFieldIndices = channel.preferredChartFields?.ifEmpty { null }
                        ?: channel.widgetVisibleFields?.ifEmpty { null }
                        ?: setOf(1)
                    com.thingspeak.monitor.feature.widget.WidgetChartGenerator.generateChartBase64(
                        context = applicationContext,
                        entries = entries.reversed(),
                        fieldIndices = defaultFieldIndices,
                        isNormalized = channel.isNormalized,
                        fieldColorsOverride = channel.fieldColors
                    )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "syncChannel: Chart generation FAILED for channel ${channel.id}", e)
                    null
                }
            } else null

            // 2.  Update bound widgets
            com.thingspeak.monitor.feature.widget.WidgetUpdateHelper.pushToBoundWidgets(
                context = applicationContext,
                channel = result.channel.toSavedChannel(),
                latestFeed = result.latestEntry,
                violatedMinFields = result.allViolations.filter {
                    it.condition == com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN
                }.map { it.fieldNumber }.toSet(),
                violatedMaxFields = result.allViolations.filter {
                    it.condition == com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN
                }.map { it.fieldNumber }.toSet(),
                minSetFields = result.channelRules.filter {
                    it.condition == com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN && it.isEnabled
                }.map { it.fieldNumber }.toSet(),
                maxSetFields = result.channelRules.filter {
                    it.condition == com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN && it.isEnabled
                }.map { it.fieldNumber }.toSet(),
                chartBase64 = channelChartBase64
            )
            android.util.Log.i(TAG, "syncChannel COMPLETED for ${channel.id}. Took ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "syncChannel MAJOR ERROR for ${channel.id}", e)
        }
    }

    private suspend fun updateAllWidgets() {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)
            manager.getGlanceIds(ThingSpeakGlanceWidget::class.java).forEach { id ->
                clearRefreshingState(id)
                ThingSpeakGlanceWidget().update(applicationContext, id)
            }
            manager.getGlanceIds(com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java).forEach { id ->
                clearRefreshingState(id)
                com.thingspeak.monitor.feature.widget.ValueGridWidget().update(applicationContext, id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "UI Update failed", e)
        }
    }

    private suspend fun clearRefreshingState(id: androidx.glance.GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(applicationContext).getAppWidgetId(id)
        com.thingspeak.monitor.feature.widget.onRefreshCompleted(appWidgetId)
        androidx.glance.appwidget.state.updateAppWidgetState(applicationContext, com.thingspeak.monitor.feature.widget.WidgetPreferencesStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                this[com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.KEY_IS_REFRESHING] = false
            }
        }
    }

    companion object {
        fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        
        /**
         * Schedule periodic data sync with KEEP policy to avoid resetting the timer.
         * Use this for routine scheduling (widget lifecycle, boot receiver).
         */
        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
        
        /**
         * Schedules periodic data sync with UPDATE policy to apply new interval immediately.
         * Use this when user changes sync interval in settings or after device boot.
         */
        fun scheduleWithUpdate(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        
        const val WORK_NAME = "DataSyncWorker"
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<DataSyncWorker>().setConstraints(constraints()).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

private fun SavedChannel.toDomain(): com.thingspeak.monitor.feature.channel.domain.model.Channel = 
    com.thingspeak.monitor.feature.channel.domain.model.Channel(
        id = id,
        name = name,
        apiKey = apiKey,
        fieldNames = fieldNames,
        lastSyncStatus = com.thingspeak.monitor.feature.channel.domain.model.SyncStatus.valueOf(lastSyncStatus),
        widgetBgColorHex = widgetBgColorHex,
        widgetTextColorHex = widgetTextColorHex,
        widgetTransparency = widgetTransparency,
        widgetFontSize = widgetFontSize,
        isGlassmorphismEnabled = isGlassmorphismEnabled,
        chartField = chartField,
        chartType = chartType,
        chartResults = chartResults,
        chartColor = chartColor ?: "#2196F3",
        chartBgColor = chartBgColor ?: "#FFFFFF",
        chartProcessingPeriod = chartProcessingPeriod,
        chartTimespan = chartTimespan,
        fieldColors = fieldColors,
        fieldYMin = fieldYMin,
        fieldYMax = fieldYMax,
        textColor = textColor ?: "#000000",
        preferredChartFields = preferredChartFields,
        lastSyncTime = lastSyncTime,
        widgetVisibleFields = widgetVisibleFields,
        displayNameMode = displayNameMode,
        displayFieldMode = displayFieldMode,
        lastProcessedEntryId = lastProcessedEntryId,
        isNormalized = isNormalized,
        isMergingEnabled = isMergingEnabled,
        drawingStyle = drawingStyle
    )

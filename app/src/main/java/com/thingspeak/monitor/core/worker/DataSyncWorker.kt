package com.thingspeak.monitor.core.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.model.toSavedChannel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import com.thingspeak.monitor.core.datastore.SavedChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        if (!workingLock.compareAndSet(false, true)) {
            android.util.Log.w(TAG, "DataSyncWorker: sync already in progress, skipping duplicate")
            return Result.success()
        }
        try {
            android.util.Log.i(TAG, "DataSyncWorker START: periodic sync triggered")
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

            finishAllWidgets()
            android.util.Log.i(TAG, "DataSyncWorker SUCCESS: All channels processed.")
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "DataSyncWorker CRITICALLY FAILED", e)
            return Result.retry()
        } finally {
            workingLock.set(false)
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

            val entries = repository.observeFeed(channel.id).first()

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
                feedEntries = entries
            )
            android.util.Log.i(TAG, "syncChannel COMPLETED for ${channel.id}. Took ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "syncChannel MAJOR ERROR for ${channel.id}", e)
        }
    }

    private suspend fun finishAllWidgets() {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext, com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
            )
            val bindingRepo = entryPoint.widgetBindingRepository()

            for (widgetClass in com.thingspeak.monitor.feature.widget.WidgetRegistry.ALL_CLASSES) {
                manager.getGlanceIds(widgetClass).forEach { id ->
                    val appWidgetId = manager.getAppWidgetId(id)
                    if (bindingRepo.getBindingSync(appWidgetId) != -1L) {
                        clearRefreshingState(id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "finishAllWidgets: clearing refreshing state failed", e)
        }
    }

    private suspend fun clearRefreshingState(id: androidx.glance.GlanceId) {
        val widgetId = GlanceAppWidgetManager(applicationContext).getAppWidgetId(id)
        com.thingspeak.monitor.feature.widget.onRefreshCompleted(widgetId)
        androidx.glance.appwidget.state.updateAppWidgetState(
            applicationContext,
            com.thingspeak.monitor.feature.widget.WidgetPreferencesStateDefinition,
            id
        ) { prefs ->
            prefs.toMutablePreferences().apply {
                this[com.thingspeak.monitor.feature.widget.WidgetPrefsKeys.KEY_IS_REFRESHING] = false
            }
        }
    }

    companion object {
        private val workingLock = AtomicBoolean(false)

        fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

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
            val request = OneTimeWorkRequestBuilder<DataSyncWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("${WORK_NAME}_runOnce", ExistingWorkPolicy.REPLACE, request)
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
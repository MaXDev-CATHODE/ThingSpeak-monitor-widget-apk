package com.thingspeak.monitor.core.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.usecase.CheckAlertThresholdsUseCase
import com.thingspeak.monitor.feature.widget.ThingSpeakGlanceWidget
import com.thingspeak.monitor.feature.widget.WidgetChartGenerator
import com.thingspeak.monitor.feature.channel.domain.model.toSavedChannel
import com.thingspeak.monitor.feature.alert.data.local.FiredAlertEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import java.time.Instant
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

    override suspend fun doWork(): Result {
        android.util.Log.i("TS_DEBUG", "DataSyncWorker START: periodic sync triggered")
        return try {
            val channels = channelPrefs.observe().first()
            if (channels.isEmpty()) {
                android.util.Log.v("TS_DEBUG", "DataSyncWorker: No channels to sync, skipping.")
                return Result.success()
            }

            android.util.Log.d("TS_DEBUG", "DataSyncWorker: Syncing ${channels.size} channels.")
            kotlinx.coroutines.coroutineScope {
                channels.map { channel ->
                    async { syncChannel(channel) }
                }.awaitAll()
            }

            updateAllWidgets()
            android.util.Log.i("TS_DEBUG", "DataSyncWorker SUCCESS: All channels processed.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TS_DEBUG", "DataSyncWorker CRITICALLY FAILED", e)
            Result.retry()
        }
    }

    private suspend fun syncChannel(channel: SavedChannel) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("TS_DEBUG", "syncChannel BEGIN: id=${channel.id} (${channel.name})")

        try {
            // Use UNIFIED UseCase (Agent 3.2)
            val result = syncChannelUseCase(channel.toDomain())
            
            if (result.error != null) {
                android.util.Log.w("TS_DEBUG", "syncChannel RESULT: Error for ${channel.id}: ${result.error.message}")
            }

            // 1. Generate chart bitmap (only if we have entries)
            val entries = repository.observeFeed(channel.id).first()
            var chartBase64: String? = null
            if (entries.isNotEmpty()) {
                try {
                    android.util.Log.v("TS_DEBUG", "syncChannel: Generating chart for ${channel.id}...")
                    val chartBitmap = WidgetChartGenerator.generateSimpleChart(
                        entries = entries.reversed(),
                        fieldIndices = channel.preferredChartFields?.ifEmpty { null } ?: setOf(1),
                        isNormalized = channel.isNormalized
                    )
                    
                    if (chartBitmap != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        chartBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
                        chartBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TS_DEBUG", "syncChannel: Chart FAILED for ${channel.id}", e)
                }
            }

            // 2. Update bound widgets
            val manager = GlanceAppWidgetManager(applicationContext)
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext, com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java)
            val bindingRepo = entryPoint.widgetBindingRepository()
            
            suspend fun updateWidgetsForType(widgetClass: Class<out androidx.glance.appwidget.GlanceAppWidget>) {
                val glanceIds = manager.getGlanceIds(widgetClass)
                glanceIds.forEach { id ->
                    val appWidgetId = manager.getAppWidgetId(id)
                    val boundId = bindingRepo.getBindingSync(appWidgetId)
                    if (boundId == channel.id) {
                        com.thingspeak.monitor.feature.widget.WidgetUpdateHelper.updateWidgetPreferences(
                            context = applicationContext,
                            glanceId = id,
                            channel = result.channel.toSavedChannel(),
                            latestFeed = result.latestEntry,
                            chartBitmapBase64 = if (widgetClass == ThingSpeakGlanceWidget::class.java) chartBase64 else null,
                            violatedMinFields = result.allViolations.filter { it.condition == "LESS_THAN" }.map { it.fieldNumber }.toSet(),
                            violatedMaxFields = result.allViolations.filter { it.condition == "GREATER_THAN" }.map { it.fieldNumber }.toSet(),
                            minSetFields = result.channelRules.filter { it.condition == "LESS_THAN" && it.isEnabled }.map { it.fieldNumber }.toSet(),
                            maxSetFields = result.channelRules.filter { it.condition == "GREATER_THAN" && it.isEnabled }.map { it.fieldNumber }.toSet()
                        )
                        // Trigger actual update
                        when (widgetClass) {
                            ThingSpeakGlanceWidget::class.java -> ThingSpeakGlanceWidget().update(applicationContext, id)
                            com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java -> com.thingspeak.monitor.feature.widget.ValueGridWidget().update(applicationContext, id)
                        }
                    }
                }
            }

            updateWidgetsForType(ThingSpeakGlanceWidget::class.java)
            updateWidgetsForType(com.thingspeak.monitor.feature.widget.ValueGridWidget::class.java)

            android.util.Log.i("TS_DEBUG", "syncChannel COMPLETED for ${channel.id}. Took ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            android.util.Log.e("TS_DEBUG", "syncChannel MAJOR ERROR for ${channel.id}", e)
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
        androidx.glance.appwidget.state.updateAppWidgetState(applicationContext, com.thingspeak.monitor.feature.widget.WidgetPreferencesStateDefinition, id) { prefs ->
            val m = prefs.toMutablePreferences()
            m[booleanPreferencesKey("is_refreshing")] = false
            m
        }
    }

    companion object {
        private const val TAG = "DataSyncWorker"
        fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints())
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

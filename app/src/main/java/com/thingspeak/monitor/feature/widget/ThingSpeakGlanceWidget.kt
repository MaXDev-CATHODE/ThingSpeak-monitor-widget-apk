package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import kotlinx.coroutines.flow.first

class ThingSpeakGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        val boundChannelId = bindingRepo.getBindingSync(appWidgetId)

        val realSyncIntervalMinutes: Long = try {
            entryPoint.appPreferences().observeSyncInterval().first()
        } catch (e: Exception) {
            android.util.Log.w("TS_DEBUG", "Failed to read sync interval, using default", e)
            DEFAULT_SYNC_INTERVAL_MINUTES
        }

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, boundChannelId, realSyncIntervalMinutes)

            // Self-Healing: trigger repair if prefs are empty/stale (one-shot guarded)
            val healAttempted = prefs[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] ?: false
            if (data.channelName == WidgetPrefsKeys.LOADING_PLACEHOLDER &&
                boundChannelId != -1L && !data.isRefreshing && !healAttempted
            ) {
                androidx.compose.runtime.LaunchedEffect(boundChannelId) {
                    updateAppWidget(context, appWidgetId)
                }
            }

            WidgetUI(data)
        }
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        WidgetUpdateHelper.performUpdate(
            context = context,
            appWidgetId = appWidgetId,
            widgetInstanceFactory = { ThingSpeakGlanceWidget() },
            onGenerateChart = { channel, channelId ->
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val repo = entryPoint.channelRepository()
                val feedEntries: List<com.thingspeak.monitor.feature.channel.domain.model.FeedEntry> = try {
                    repo.observeFeed(channelId).first()
                } catch (e: Exception) {
                    android.util.Log.w("TS_DEBUG", "updateAppWidget: failed to load feed entries", e)
                    emptyList()
                }

                var chartBase64: String? = null
                if (feedEntries.isNotEmpty()) {
                    try {
                        val chartBitmap = WidgetChartGenerator.generateSimpleChart(
                            entries = feedEntries.reversed(),
                            fieldIndices = channel.preferredChartFields?.ifEmpty { null }
                                ?: channel.widgetVisibleFields?.ifEmpty { null }
                                ?: setOf(1),
                            isNormalized = true,
                            fieldColorsOverride = channel.fieldColors
                        )
                        chartBase64 = bitmapToBase64(chartBitmap)
                    } catch (e: Exception) {
                        android.util.Log.w("TS_DEBUG", "updateAppWidget: Chart generation failed", e)
                    }
                }
                chartBase64
            }
        )
    }
}
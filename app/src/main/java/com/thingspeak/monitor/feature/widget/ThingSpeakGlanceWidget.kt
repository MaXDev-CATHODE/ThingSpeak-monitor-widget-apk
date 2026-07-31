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
        val gCtx = WidgetUpdateHelper.resolveGlanceContext(context, id)

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, gCtx.boundChannelId, gCtx.syncIntervalMinutes)

            if (WidgetUpdateHelper.shouldTriggerSelfHeal(prefs, data, gCtx.boundChannelId)) {
                androidx.compose.runtime.LaunchedEffect(gCtx.boundChannelId) {
                    WidgetUpdateHelper.bumpHealRetry(context, id)
                    updateAppWidget(context, gCtx.appWidgetId)
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
                    android.util.Log.w(WIDGET_LOG_TAG, "updateAppWidget: failed to load feed entries", e)
                    emptyList()
                }

                if (feedEntries.isEmpty()) {
                    null
                } else try {
                    WidgetChartGenerator.generateAndSaveChart(
                        context = context,
                        channel = channel,
                        entries = feedEntries.reversed(),
                        appWidgetId = appWidgetId
                    )
                } catch (e: Exception) {
                    android.util.Log.w(WIDGET_LOG_TAG, "updateAppWidget: Chart generation failed", e)
                    null
                }
            }
        )
    }
}
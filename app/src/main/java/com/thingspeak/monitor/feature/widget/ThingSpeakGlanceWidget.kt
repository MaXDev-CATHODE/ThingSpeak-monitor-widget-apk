package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition

class ThingSpeakGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val gCtx = try {
            WidgetUpdateHelper.resolveGlanceContext(context, id)
        } catch (e: Exception) {
            android.util.Log.e(WIDGET_LOG_TAG, "ThingSpeakGlanceWidget: resolveGlanceContext failed", e)
            return
        }

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, gCtx.boundChannelId, gCtx.syncIntervalMinutes)

            androidx.compose.runtime.LaunchedEffect(gCtx.boundChannelId) {
                WidgetUpdateHelper.handleSelfHealing(
                    prefs = prefs,
                    data = data,
                    boundChannelId = gCtx.boundChannelId,
                    context = context,
                    id = id,
                    appWidgetId = gCtx.appWidgetId,
                    updateAppWidget = { updateAppWidget(context, gCtx.appWidgetId) }
                )
            }

            WidgetUI(data)
        }
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        WidgetUpdateHelper.performUpdate(
            context = context,
            appWidgetId = appWidgetId,
            widgetInstanceFactory = { ThingSpeakGlanceWidget() },
            onGenerateChart = { channel, channelId, feeds ->
                if (feeds.isEmpty()) {
                    null
                } else try {
                    WidgetChartGenerator.generateAndSaveChart(
                        context = context,
                        channel = channel,
                        entries = feeds.reversed(),
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
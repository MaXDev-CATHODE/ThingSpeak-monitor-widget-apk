package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition

class ValueGridWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val gCtx = try {
            WidgetUpdateHelper.resolveGlanceContext(context, id)
        } catch (e: Exception) {
            android.util.Log.e(WIDGET_LOG_TAG, "ValueGridWidget: resolveGlanceContext failed", e)
            return
        }

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, gCtx.boundChannelId, gCtx.syncIntervalMinutes, skipChartBitmap = true)

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

            ValueGridContent(context, data)
        }
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        WidgetUpdateHelper.performUpdate(
            context = context,
            appWidgetId = appWidgetId,
            widgetInstanceFactory = { ValueGridWidget() },
            isChartWidget = false
        )
    }
}
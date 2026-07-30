package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters

class RefreshWidgetAction(
    private val widgetFactory: () -> GlanceAppWidget,
    private val uniqueWorkPrefix: String
) : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        performWidgetRefreshAction(
            context = context,
            glanceId = glanceId,
            updateWidget = { widgetFactory().update(context, glanceId) },
            uniqueWorkPrefix = uniqueWorkPrefix
        )
    }
}

class EditWidgetAction(
    private val configActivityClass: Class<out android.app.Activity>
) : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = android.content.Intent(context, configActivityClass).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
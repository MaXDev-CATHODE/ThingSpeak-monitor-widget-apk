package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System entry point for the ThingSpeak standard widget (chart view).
 *
 * Delegates rendering to [ThingSpeakGlanceWidget] via Glance framework.
 * Lifecycle events are handled by [WidgetReceiverHelper] shared functions.
 */
@AndroidEntryPoint
class WidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var repository: WidgetBindingRepository

    override val glanceAppWidget: GlanceAppWidget = ThingSpeakGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        handleReceiverOnUpdate(
            context = context,
            appWidgetIds = appWidgetIds,
            widgetFactory = { ThingSpeakGlanceWidget() },
            repository = repository
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        android.util.Log.v(WIDGET_LOG_TAG, "WidgetReceiver onReceive: action=${intent.action}")
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        handleReceiverOnEnabled(context, "WidgetReceiver")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        handleReceiverOnDeleted(context, appWidgetIds, repository)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        handleReceiverOnDisabled(context, repository, "WidgetReceiver")
    }
}

package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ValueGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValueGridWidget()

    @Inject
    lateinit var repository: WidgetBindingRepository

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        handleReceiverOnEnabled(context, "ValueGridWidgetReceiver")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        handleReceiverOnUpdate(
            context = context,
            appWidgetIds = appWidgetIds,
            widgetFactory = { ValueGridWidget() },
            repository = repository
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        handleReceiverOnDeleted(context, appWidgetIds, repository)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        handleReceiverOnDisabled(context, repository, "ValueGridWidgetReceiver")
    }
}

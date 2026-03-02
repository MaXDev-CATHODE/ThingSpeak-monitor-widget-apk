package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.thingspeak.monitor.core.worker.DataSyncWorker

class ValueGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValueGridWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        
        // Trigger data sync when widget updates (e.g., from manual refresh or period)
        val workRequest = OneTimeWorkRequestBuilder<DataSyncWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "widget_update_sync",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }
}

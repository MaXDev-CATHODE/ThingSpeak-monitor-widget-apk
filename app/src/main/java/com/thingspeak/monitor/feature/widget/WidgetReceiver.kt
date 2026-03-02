package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * System entry point for the ThingSpeak homescreen widget.
 *
 * Delegates rendering to [ThingSpeakGlanceWidget] via Glance framework.
 * Manages periodic [WidgetRefreshWorker] lifecycle: enqueue on first widget,
 * cancel when the last widget is removed.
 */
class WidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ThingSpeakGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for widgets: ${appWidgetIds.joinToString()}")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive intent: ${intent.action}")
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueuePeriodicRefresh(context)
        Log.i(TAG, "First widget added — periodic refresh enqueued")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val manager = AppWidgetManager.getInstance(context)
        val remaining = manager.getAppWidgetIds(
            android.content.ComponentName(context, WidgetReceiver::class.java)
        )
        if (remaining.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(com.thingspeak.monitor.core.worker.DataSyncWorker.WORK_NAME)
            Log.i(TAG, "Last widget removed — periodic refresh cancelled")
        }
    }

    companion object {
        private const val TAG = "WidgetReceiver"

        fun enqueuePeriodicRefresh(context: Context) {
            // Direct scheduling with default interval to avoid confusion in periodic work naming.
            // The actual interval will be fetched in DataSyncWorker anyway, but we ensure
            // that the WorkManager dependency chain is correct.
            com.thingspeak.monitor.core.worker.DataSyncWorker.schedule(context, 30L)
            android.util.Log.i(TAG, "periodic refresh enqueued directly via DataSyncWorker")
        }
    }
}

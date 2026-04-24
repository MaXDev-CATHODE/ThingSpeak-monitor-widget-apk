package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * System entry point for the ThingSpeak homescreen widget.
 *
 * Delegates rendering to [ThingSpeakGlanceWidget] via Glance framework.
 * Manages periodic [WidgetRefreshWorker] lifecycle: enqueue on first widget,
 * cancel when the last widget is removed.
 */
@AndroidEntryPoint
class WidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var repository: WidgetBindingRepository

    override val glanceAppWidget: GlanceAppWidget = ThingSpeakGlanceWidget()

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        android.util.Log.i("TS_DEBUG", "WidgetReceiver onUpdate: triggered for ids=${appWidgetIds.joinToString()}")

        // FORCE SYNC ID FROM ROOM TO GLANCE (NUCLEAR V8)
        // Note: Using a top-level coroutine scope for fire-and-forget sync to avoid blocking the receiver's main thread.
        // However, we ensure the periodic refresh is enqueued correctly.
        appWidgetIds.forEach { id ->
            scope.launch {
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        updateAppWidgetState(context, WidgetPreferencesStateDefinition, gId) { p ->
                            p.toMutablePreferences().apply {
                                if (this[longPreferencesKey("channel_id")] != boundId) {
                                    this[longPreferencesKey("channel_id")] = boundId
                                    Log.e("NUCLEAR_V8", "PUSHED binding to Glance for standard $id -> $boundId")
                                }
                            }
                        }
                        // Trigger immediate refresh for this specific widget if it's new/stale
                        ThingSpeakGlanceWidget().update(context, gId)
                    }
                } catch (e: Exception) {
                    Log.e("NUCLEAR_V8", "Failed to push binding for standard $id", e)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        android.util.Log.v("TS_DEBUG", "WidgetReceiver onReceive: action=${intent.action}")
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueuePeriodicRefresh(context)
        Log.i(TAG, "First widget added — periodic refresh enqueued")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        android.util.Log.i("TS_DEBUG", "WidgetReceiver onDeleted: ids=${appWidgetIds.joinToString()}")
        val manager = AppWidgetManager.getInstance(context)
        val remaining = manager.getAppWidgetIds(
            android.content.ComponentName(context, WidgetReceiver::class.java)
        )
        if (remaining.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(com.thingspeak.monitor.core.worker.DataSyncWorker.WORK_NAME)
            android.util.Log.w("TS_DEBUG", "WidgetReceiver: Last widget removed — periodic refresh cancelled")
        }
    }

    companion object {
        private const val TAG = "WidgetReceiver"

        fun enqueuePeriodicRefresh(context: Context) {
            // Read the user-configured sync interval from AppPreferences via EntryPointAccessors,
            // following the same pattern used in RescheduleWorker.doWork().
            CoroutineScope(Dispatchers.IO).launch {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
                )
                val intervalMinutes = entryPoint.appPreferences().observeSyncInterval().first()
                com.thingspeak.monitor.core.worker.DataSyncWorker.schedule(context, intervalMinutes)
                android.util.Log.i(TAG, "periodic refresh enqueued with interval=$intervalMinutes min")
            }
        }
    }
}

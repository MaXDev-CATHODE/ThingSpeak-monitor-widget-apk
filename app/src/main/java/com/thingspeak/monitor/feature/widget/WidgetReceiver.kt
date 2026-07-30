package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        android.util.Log.i("TS_DEBUG", "WidgetReceiver onUpdate: triggered for ids=${appWidgetIds.joinToString()}")

        // Ensure DataSyncWorker is running — schedule if not active (fix for widget-no-auto-refresh)
        enqueuePeriodicRefreshIfNeeded(context)

        // FORCE SYNC ID FROM ROOM TO GLANCE (NUCLEAR V8)
        // Note: Using a top-level coroutine scope for fire-and-forget sync to avoid blocking the receiver's main thread.
        // However, we ensure the periodic refresh is enqueued correctly.
        appWidgetIds.forEach { id ->
            scope.launch {
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        if (gId != null) {
                            updateAppWidgetState(context, WidgetPreferencesStateDefinition, gId) { p ->
                                p.toMutablePreferences().apply {
                                    if (this[WidgetPrefsKeys.KEY_CHANNEL_ID] != boundId) {
                                        this[WidgetPrefsKeys.KEY_CHANNEL_ID] = boundId
                                        Log.i("TS_DEBUG", "PUSHED binding to Glance for standard $id -> $boundId")
                                    }
                                }
                            }
                            // Trigger immediate refresh for this specific widget if it's new/stale
                            ThingSpeakGlanceWidget().update(context, gId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TS_DEBUG", "Failed to push binding for standard $id", e)
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

        // Check remaining active ThingSpeakGlanceWidget instances
        val remainingGlanceWidgets = manager.getAppWidgetIds(
            android.content.ComponentName(context, WidgetReceiver::class.java)
        )
        // Check remaining active ValueGridWidget instances
        val remainingValueGridWidgets = manager.getAppWidgetIds(
            android.content.ComponentName(context, ValueGridWidgetReceiver::class.java)
        )

        // Cancel periodic work only when no widgets of either type remain active
        if (remainingGlanceWidgets.isEmpty() && remainingValueGridWidgets.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(com.thingspeak.monitor.core.worker.DataSyncWorker.WORK_NAME)
            android.util.Log.w("TS_DEBUG", "WidgetReceiver: Last widget of any type removed — periodic refresh cancelled")
        }

        appWidgetIds.forEach { id ->
            cancelRefreshTimeout(id)
            scope.launch {
                try {
                    repository.removeBinding(id)
                    Log.i("TS_DEBUG", "WidgetReceiver: cleaned Room binding for $id")
                } catch (e: Exception) {
                    Log.e("TS_DEBUG", "WidgetReceiver: failed to clean Room binding for $id", e)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }

    companion object {
        private const val TAG = "TS_DEBUG"
        private val periodicScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun enqueuePeriodicRefresh(context: Context) {
            periodicScope.launch {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
                )
                val intervalMinutes = entryPoint.appPreferences().observeSyncInterval().first()
                com.thingspeak.monitor.core.worker.DataSyncWorker.schedule(context, intervalMinutes)
                android.util.Log.i(TAG, "periodic refresh enqueued with interval=$intervalMinutes min")
            }
        }

        /**
         * Enqueues periodic refresh only if DataSyncWorker is not already active.
         * Checks the real WorkManager state (ENQUEUED or RUNNING) before scheduling,
         * avoiding redundant enqueue calls while ensuring the worker is always running.
         * Fire-and-forget, runs on Dispatchers.IO (blocking .get() is safe here).
         */
        fun enqueuePeriodicRefreshIfNeeded(context: Context) {
            periodicScope.launch {
                val workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(com.thingspeak.monitor.core.worker.DataSyncWorker.WORK_NAME)
                    .get() // blocking, safe on Dispatchers.IO
                val isActive = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
                if (!isActive) {
                    android.util.Log.i(TAG, "enqueuePeriodicRefreshIfNeeded: no active work found, scheduling")
                    enqueuePeriodicRefresh(context)
                } else {
                    android.util.Log.v(TAG, "enqueuePeriodicRefreshIfNeeded: worker already active, skipping")
                }
            }
        }
    }
}

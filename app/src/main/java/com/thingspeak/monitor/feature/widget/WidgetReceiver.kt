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

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        android.util.Log.i(WIDGET_LOG_TAG, "WidgetReceiver onUpdate: triggered for ids=${appWidgetIds.joinToString()}")

        // Ensure DataSyncWorker is running — schedule if not active (fix for widget-no-auto-refresh)
        enqueuePeriodicRefreshIfNeeded(context)

        appWidgetIds.forEach { id ->
            cleanupScope.launch {
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        if (gId != null) {
                            updateAppWidgetState(context, WidgetPreferencesStateDefinition, gId) { p ->
                                p.toMutablePreferences().apply {
                                    if (this[WidgetPrefsKeys.KEY_CHANNEL_ID] != boundId) {
                                        this[WidgetPrefsKeys.KEY_CHANNEL_ID] = boundId
                                        Log.i(WIDGET_LOG_TAG, "PUSHED binding to Glance for standard $id -> $boundId")
                                    }
                                }
                            }
                            ThingSpeakGlanceWidget().update(context, gId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(WIDGET_LOG_TAG, "Failed to push binding for standard $id", e)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        android.util.Log.v(WIDGET_LOG_TAG, "WidgetReceiver onReceive: action=${intent.action}")
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueuePeriodicRefresh(context)
        Log.i(WIDGET_LOG_TAG, "First widget added — periodic refresh enqueued")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        android.util.Log.i(WIDGET_LOG_TAG, "WidgetReceiver onDeleted: ids=${appWidgetIds.joinToString()}")

        WidgetUpdateHelper.cancelRefreshIfNoWidgetsLeft(context)

        appWidgetIds.forEach { id ->
            cancelRefreshTimeout(id)
            cleanupScope.launch {
                try {
                    repository.removeBinding(id)
                    WidgetChartCache.clear(context, id)
                    Log.i(WIDGET_LOG_TAG, "WidgetReceiver: cleaned Room binding and chart cache for $id")
                } catch (e: Exception) {
                    Log.e(WIDGET_LOG_TAG, "WidgetReceiver: failed to clean Room binding and chart cache for $id", e)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.i(WIDGET_LOG_TAG, "WidgetReceiver onDisabled: last widget removed, cleaning orphaned bindings")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                repository.clearAllBindings()
                WidgetChartCache.clearAll(context)
                Log.i(WIDGET_LOG_TAG, "onDisabled: async cleanup completed")
            } catch (e: Exception) {
                Log.e(WIDGET_LOG_TAG, "onDisabled: cleanup failed", e)
            }
        }
    }

    companion object {
        private val periodicScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun enqueuePeriodicRefresh(context: Context) {
            periodicScope.launch {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
                )
                val intervalMinutes = entryPoint.appPreferences().observeSyncInterval().first()
                com.thingspeak.monitor.core.worker.DataSyncWorker.schedule(context, intervalMinutes)
                android.util.Log.i(WIDGET_LOG_TAG, "periodic refresh enqueued with interval=$intervalMinutes min")
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
                    android.util.Log.i(WIDGET_LOG_TAG, "enqueuePeriodicRefreshIfNeeded: no active work found, scheduling")
                    enqueuePeriodicRefresh(context)
                } else {
                    android.util.Log.v(WIDGET_LOG_TAG, "enqueuePeriodicRefreshIfNeeded: worker already active, skipping")
                }
            }
        }
    }
}

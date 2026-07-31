package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

fun enqueuePeriodicRefreshIfNeeded(context: Context) {
    periodicScope.launch {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(com.thingspeak.monitor.core.worker.DataSyncWorker.WORK_NAME)
            .get()
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

fun handleReceiverOnEnabled(context: Context, logTag: String = "WidgetReceiver") {
    enqueuePeriodicRefresh(context)
    android.util.Log.i(WIDGET_LOG_TAG, "$logTag added — periodic refresh enqueued")
}

fun handleReceiverOnUpdate(
    context: Context,
    appWidgetIds: IntArray,
    widgetFactory: () -> androidx.glance.appwidget.GlanceAppWidget,
    scope: CoroutineScope,
    repository: WidgetBindingRepository
) {
    android.util.Log.i(WIDGET_LOG_TAG, "Receiver onUpdate: triggered for ids=${appWidgetIds.joinToString()}")
    enqueuePeriodicRefreshIfNeeded(context)

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
                                    android.util.Log.i(WIDGET_LOG_TAG, "Pushed binding to Glance for $id -> $boundId")
                                }
                            }
                        }
                        val widget = widgetFactory()
                        widget.update(context, gId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(WIDGET_LOG_TAG, "Failed to push binding for $id", e)
            }
        }
    }
}

fun handleReceiverOnDeleted(
    context: Context,
    appWidgetIds: IntArray,
    scope: CoroutineScope,
    repository: WidgetBindingRepository
) {
    android.util.Log.i(WIDGET_LOG_TAG, "onDeleted: ids=${appWidgetIds.joinToString()}")
    WidgetUpdateHelper.cancelRefreshIfNoWidgetsLeft(context)
    appWidgetIds.forEach { id ->
        cancelRefreshTimeout(id)
        scope.launch {
            try {
                repository.removeBinding(id)
                WidgetChartCache.clear(context, id)
                android.util.Log.i(WIDGET_LOG_TAG, "Cleaned Room binding and chart cache for $id")
            } catch (e: Exception) {
                android.util.Log.e(WIDGET_LOG_TAG, "Failed to clean binding for $id", e)
            }
        }
    }
}

fun handleReceiverOnDisabled(
    context: Context,
    scope: CoroutineScope,
    repository: WidgetBindingRepository,
    logTag: String
) {
    android.util.Log.i(WIDGET_LOG_TAG, "$logTag onDisabled: cleaning orphaned bindings")
    scope.launch {
        try {
            repository.clearAllBindings()
            WidgetChartCache.clearAll(context)
            android.util.Log.i(WIDGET_LOG_TAG, "onDisabled: async cleanup completed")
        } catch (e: Exception) {
            android.util.Log.e(WIDGET_LOG_TAG, "onDisabled: cleanup failed", e)
        }
    }
}
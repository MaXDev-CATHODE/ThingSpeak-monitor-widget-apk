package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val refreshTimeoutJobs = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Job>()
private val activeRefreshes = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

fun cancelRefreshTimeout(appWidgetId: Int) {
    refreshTimeoutJobs.remove(appWidgetId)?.cancel()
    activeRefreshes.remove(appWidgetId)
}

fun onRefreshCompleted(appWidgetId: Int) {
    refreshTimeoutJobs.remove(appWidgetId)
    activeRefreshes.remove(appWidgetId)
}

suspend fun performWidgetRefreshAction(
    context: Context,
    glanceId: GlanceId,
    updateWidget: suspend () -> Unit,
    uniqueWorkPrefix: String
) {
    val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
    )

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val isOnline = if (activeNetwork != null) {
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } else false

    if (!isOnline) {
        android.util.Log.w(WIDGET_LOG_TAG, "performWidgetRefreshAction: device offline, refresh aborted for widget $appWidgetId")
        androidx.glance.appwidget.state.updateAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        ) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
                this[WidgetPrefsKeys.KEY_LAST_SYNC_STATUS] = WidgetPrefsKeys.STATUS_OFFLINE
            }
        }
        updateWidget()
        return
    }

    val bindingRepo = entryPoint.widgetBindingRepository()
    val channelId = bindingRepo.getBindingSync(appWidgetId)
    if (channelId == -1L) return

    if (!activeRefreshes.add(appWidgetId)) {
        android.util.Log.d(WIDGET_LOG_TAG, "performWidgetRefreshAction: refresh already in progress for $appWidgetId, ignored")
        return
    }

    androidx.glance.appwidget.state.updateAppWidgetState(
        context, WidgetPreferencesStateDefinition, glanceId
    ) { prefs ->
        prefs.toMutablePreferences().apply {
            this[WidgetPrefsKeys.KEY_IS_REFRESHING] = true
        }
    }
    updateWidget()

    try {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.thingspeak.monitor.core.worker.DataSyncWorker>().build()
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "${uniqueWorkPrefix}_${appWidgetId}",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
    } catch (e: Exception) {
        android.util.Log.e(WIDGET_LOG_TAG, "Failed to enqueue DataSyncWorker", e)
        androidx.glance.appwidget.state.updateAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        ) { p ->
            p.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
            }
        }
        updateWidget()
        activeRefreshes.remove(appWidgetId)
        return
    }

    refreshTimeoutJobs.remove(appWidgetId)?.cancel()
    val timeoutJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        delay(60_000)
        onRefreshCompleted(appWidgetId)
        val currentPrefs = androidx.glance.appwidget.state.getAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        )
        if (currentPrefs[WidgetPrefsKeys.KEY_IS_REFRESHING] == true) {
            androidx.glance.appwidget.state.updateAppWidgetState(
                context, WidgetPreferencesStateDefinition, glanceId
            ) { p -> p.toMutablePreferences().apply { this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false } }
            updateWidget()
        }
    }
    refreshTimeoutJobs[appWidgetId] = timeoutJob
}
package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.work.WorkManager
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.worker.DataSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ValueGridWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        val boundChannelId = bindingRepo.getBindingSync(appWidgetId)

        val realSyncIntervalMinutes: Long = try {
            entryPoint.appPreferences().observeSyncInterval().first()
        } catch (e: Exception) {
            android.util.Log.w("TS_DEBUG", "Failed to read sync interval, using default", e)
            DEFAULT_SYNC_INTERVAL_MINUTES
        }

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, boundChannelId, realSyncIntervalMinutes)

            // Self-Healing: trigger repair if prefs are empty/stale (one-shot guarded)
            val healAttempted = prefs[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] ?: false
            if (data.channelName == WidgetPrefsKeys.LOADING_PLACEHOLDER &&
                boundChannelId != -1L && !data.isRefreshing && !healAttempted
            ) {
                androidx.compose.runtime.LaunchedEffect(boundChannelId) {
                    updateAppWidget(context, appWidgetId)
                }
            }

            ValueGridContent(context, data)
        }
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        WidgetUpdateHelper.performUpdate(
            context = context,
            appWidgetId = appWidgetId,
            widgetInstanceFactory = { ValueGridWidget() }
        )
    }
}

@AndroidEntryPoint
class ValueGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValueGridWidget()

    @Inject
    lateinit var repository: WidgetBindingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetReceiver.enqueuePeriodicRefresh(context)
        android.util.Log.i("ValueGridWidgetReceiver", "First ValueGridWidget added - periodic refresh enqueued")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetReceiver.enqueuePeriodicRefreshIfNeeded(context)
        appWidgetIds.forEach { id ->
            scope.launch {
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        if (gId != null) {
                            updateAppWidgetState(
                                context, WidgetPreferencesStateDefinition, gId
                            ) { p ->
                                p.toMutablePreferences().apply {
                                    if (this[WidgetPrefsKeys.KEY_CHANNEL_ID] != boundId) {
                                        this[WidgetPrefsKeys.KEY_CHANNEL_ID] = boundId
                                        android.util.Log.i("ValueGridWidgetReceiver", "Synced binding to Glance for grid $id -> $boundId")
                                    }
                                }
                            }
                            ValueGridWidget().update(context, gId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ValueGridWidgetReceiver", "Failed to push binding for grid $id", e)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        android.util.Log.i("ValueGridWidgetReceiver", "onDeleted: ids=${appWidgetIds.joinToString()}")
        val manager = AppWidgetManager.getInstance(context)
        val remainingGlance = manager.getAppWidgetIds(
            ComponentName(context, WidgetReceiver::class.java)
        )
        val remainingValueGridWidgets = manager.getAppWidgetIds(
            ComponentName(context, ValueGridWidgetReceiver::class.java)
        )
        if (remainingGlance.isEmpty() && remainingValueGridWidgets.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(DataSyncWorker.WORK_NAME)
            android.util.Log.w("ValueGridWidgetReceiver", "Last widget of any type removed - periodic refresh cancelled")
        }

        appWidgetIds.forEach { id ->
            scope.launch {
                try {
                    repository.removeBinding(id)
                    android.util.Log.i("ValueGridWidgetReceiver", "cleaned Room binding for $id")
                } catch (e: Exception) {
                    android.util.Log.e("ValueGridWidgetReceiver", "failed to clean Room binding for $id", e)
                }
            }
        }
    }

    // Gracefully cancel scope when service lifecycle ends
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope.cancel()
    }
}
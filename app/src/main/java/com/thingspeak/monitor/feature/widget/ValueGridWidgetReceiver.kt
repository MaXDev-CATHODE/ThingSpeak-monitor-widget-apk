package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.thingspeak.monitor.core.worker.DataSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ValueGridWidgetReceiver : GlanceAppWidgetReceiver() {
    
    @Inject
    lateinit var repository: WidgetBindingRepository

    override val glanceAppWidget: GlanceAppWidget = ValueGridWidget()

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("NUCLEAR_V8", "ValueGrid onUpdate for: ${appWidgetIds.joinToString()}")

        // FORCE SYNC ID FROM ROOM TO GLANCE (NUCLEAR V8)
        scope.launch {
            appWidgetIds.forEach { id ->
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        updateAppWidgetState(context, WidgetPreferencesStateDefinition, gId) { p ->
                            p.toMutablePreferences().apply {
                                if (this[longPreferencesKey("channel_id")] != boundId) {
                                    this[longPreferencesKey("channel_id")] = boundId
                                    Log.e("NUCLEAR_V8", "PUSHED binding to Glance for $id -> $boundId")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NUCLEAR_V8", "Failed to push binding for $id", e)
                }
            }
        }
        
        // Trigger data sync when widget updates (e.g., from manual refresh or period)
        val workRequest = OneTimeWorkRequestBuilder<DataSyncWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "widget_grid_update_sync",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }
}

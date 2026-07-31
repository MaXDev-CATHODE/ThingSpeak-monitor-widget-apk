package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ValueGridWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val gCtx = WidgetUpdateHelper.resolveGlanceContext(context, id)

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val data = loadWidgetDataFromPreferences(prefs, gCtx.boundChannelId, gCtx.syncIntervalMinutes, skipChartBitmap = true)

            if (WidgetUpdateHelper.shouldTriggerSelfHeal(prefs, data, gCtx.boundChannelId)) {
                androidx.compose.runtime.LaunchedEffect(gCtx.boundChannelId) {
                    WidgetUpdateHelper.bumpHealRetry(context, id)
                    updateAppWidget(context, gCtx.appWidgetId)
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

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetReceiver.enqueuePeriodicRefresh(context)
        android.util.Log.i(WIDGET_LOG_TAG, "First ValueGridWidget added - periodic refresh enqueued")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetReceiver.enqueuePeriodicRefreshIfNeeded(context)
        appWidgetIds.forEach { id ->
            updateScope.launch {
                try {
                    val boundId = repository.getBindingSync(id)
                    if (boundId > 0) {
                        val gId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        if (gId != null) {
                            updateAppWidgetState(
                                context, WidgetPreferencesStateDefinition, gId
                            ) { branch ->
                                branch.toMutablePreferences().apply {
                                    if (this[WidgetPrefsKeys.KEY_CHANNEL_ID] != boundId) {
                                        this[WidgetPrefsKeys.KEY_CHANNEL_ID] = boundId
                                        android.util.Log.i(WIDGET_LOG_TAG, "Synced binding to Glance for grid $id -> $boundId")
                                    }
                                }
                            }
                            ValueGridWidget().update(context, gId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(WIDGET_LOG_TAG, "Failed to push binding for grid $id", e)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        android.util.Log.i(WIDGET_LOG_TAG, "onDeleted: ids=${appWidgetIds.joinToString()}")

        WidgetUpdateHelper.cancelRefreshIfNoWidgetsLeft(context)

        appWidgetIds.forEach { id ->
            cancelRefreshTimeout(id)
            updateScope.launch {
                try {
                    repository.removeBinding(id)
                    WidgetChartCache.clear(context, id)
                    android.util.Log.i(WIDGET_LOG_TAG, "cleaned Room binding and chart cache for $id")
                } catch (e: Exception) {
                    android.util.Log.e(WIDGET_LOG_TAG, "failed to clean Room binding for $id", e)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }
}
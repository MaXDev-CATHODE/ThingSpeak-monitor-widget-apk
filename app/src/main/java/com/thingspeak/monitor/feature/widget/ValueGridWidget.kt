package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.WorkManager
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.core.worker.DataSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.toMutablePreferences

class ValueGridWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        val boundChannelId = bindingRepo.getBindingSync(appWidgetId)

        // B1 fix: read real sync interval from global AppPreferences, with fallback
        val realSyncIntervalMinutes: Long = try {
            entryPoint.appPreferences().observeSyncInterval().first()
        } catch (e: Exception) {
            android.util.Log.w("TS_DEBUG", "DataStore read failed, using default 30min", e)
            30L
        }

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val isRefreshing = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] ?: false
            var name = prefs[androidx.datastore.preferences.core.stringPreferencesKey("channel_name")]
            val bgColor = prefs[androidx.datastore.preferences.core.stringPreferencesKey("bg_color")] ?: "#FFFFFF"
            val textColor = prefs[androidx.datastore.preferences.core.stringPreferencesKey("text_color")]
            val transparency = prefs[androidx.datastore.preferences.core.floatPreferencesKey("transparency")] ?: 1.0f
            val isGlass = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_glass")] ?: false
            val chartResults = prefs[androidx.datastore.preferences.core.intPreferencesKey("chart_results")] ?: 60
            val fontSize = prefs[androidx.datastore.preferences.core.intPreferencesKey("font_size")] ?: 12
            
            val visibleFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            val violatedMinSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("violated_min_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val violatedMaxSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("violated_max_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val minSetFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("min_set_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val maxSetFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("max_set_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            
            val entryJson = prefs[androidx.datastore.preferences.core.stringPreferencesKey("cached_entry")]

            // SELF-HEALING: If name or entry is missing, trigger repair
            // Prevent infinite loops by checking isRefreshing
            if ((name == null || entryJson == null) && boundChannelId != -1L && !isRefreshing) {
                androidx.compose.runtime.LaunchedEffect(boundChannelId) {
                    updateAppWidget(context, appWidgetId)
                }
            }

            val entry = if (entryJson != null) {
                try {
                    val jsonObj = org.json.JSONObject(entryJson)
                    com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity(
                        channelId = jsonObj.optLong("channelId", 0L),
                        createdAt = jsonObj.optString("createdAt", ""),
                        entryId = jsonObj.optLong("entryId", 0L),
                        field1 = jsonObj.optString("field1").takeIf { it != "null" && it.isNotBlank() },
                        field2 = jsonObj.optString("field2").takeIf { it != "null" && it.isNotBlank() },
                        field3 = jsonObj.optString("field3").takeIf { it != "null" && it.isNotBlank() },
                        field4 = jsonObj.optString("field4").takeIf { it != "null" && it.isNotBlank() },
                        field5 = jsonObj.optString("field5").takeIf { it != "null" && it.isNotBlank() },
                        field6 = jsonObj.optString("field6").takeIf { it != "null" && it.isNotBlank() },
                        field7 = jsonObj.optString("field7").takeIf { it != "null" && it.isNotBlank() },
                        field8 = jsonObj.optString("field8").takeIf { it != "null" && it.isNotBlank() }
                    )
                } catch (e: Exception) { null }
            } else null

            val fieldNamesJson = prefs[androidx.datastore.preferences.core.stringPreferencesKey("field_names")]
            val fieldNames = try {
                if (fieldNamesJson != null) {
                    val jsonObj = org.json.JSONObject(fieldNamesJson)
                    val map = mutableMapOf<Int, String>()
                    jsonObj.keys().forEach { key -> map[key.toInt()] = jsonObj.getString(key) }
                    map
                } else emptyMap()
            } catch (e: Exception) { emptyMap() }

            val fieldUnitsJson = prefs[androidx.datastore.preferences.core.stringPreferencesKey("field_units")]
            val fieldUnits = try {
                if (fieldUnitsJson != null) {
                    val jsonObj = org.json.JSONObject(fieldUnitsJson)
                    val map = mutableMapOf<Int, String>()
                    jsonObj.keys().forEach { key -> map[key.toInt()] = jsonObj.getString(key) }
                    map
                } else emptyMap()
            } catch (e: Exception) { emptyMap() }

            val rounding = prefs[androidx.datastore.preferences.core.intPreferencesKey("rounding")] ?: 2

            val lastSyncStatus = prefs[androidx.datastore.preferences.core.stringPreferencesKey("last_sync_status")] ?: "NONE"
            val channelTimezone = prefs[androidx.datastore.preferences.core.stringPreferencesKey("channel_timezone")]

            val data = WidgetData(
                channelName = name ?: "Loading...",
                channelId = boundChannelId,
                entry = entry,
                fieldNames = fieldNames,
                fieldUnits = fieldUnits,
                bgColorHex = bgColor,
                transparency = transparency,
                isGlass = isGlass,
                fontSize = fontSize,
                chartRounding = rounding,
                chartResults = chartResults,
                isRefreshing = isRefreshing || (name == null),
                lastSyncStatus = lastSyncStatus,
                syncIntervalMinutes = realSyncIntervalMinutes,
                visibleFields = visibleFieldsSet,
                violatedMinFields = violatedMinSet,
                violatedMaxFields = violatedMaxSet,
                minSetFields = minSetFieldsSet,
                maxSetFields = maxSetFieldsSet,
                textColor = if (textColor.isNullOrBlank()) null else textColor,
                channelTimezone = channelTimezone
            )
            
            ValueGridContent(context, data)
        }
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val chanPrefs = entryPoint.channelPreferences()
        val getChannelFeed = entryPoint.getChannelFeedUseCase()

        val channelId = bindingRepo.getBindingSync(appWidgetId)
        if (channelId == -1L) return

        var channel: SavedChannel? = null
        chanPrefs.observe().take(1).collect { list ->
            channel = list.find { it.id == channelId }
        }
        val channelToUse = channel ?: return

        var latestFeed: com.thingspeak.monitor.feature.channel.domain.model.FeedEntry? = null
        getChannelFeed.observe(channelId).take(1).collect { feeds ->
            latestFeed = feeds.lastOrNull()
        }

        val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        if (glanceId == null) {
            android.util.Log.e("TS_DEBUG", "updateAppWidget: Could not find GlanceId for $appWidgetId")
            return
        }

        // Fix 2: resolve alerts for self-healing (mirrors ThingSpeakGlanceWidget.updateAppWidget)
        val repo = entryPoint.channelRepository()
        val checkAlertRules = entryPoint.checkAlertRulesUseCase()
        val widgetRules = repo.getAlertRules(channelId, appWidgetId)
        val feed = latestFeed
        val violations = if (feed != null) {
            checkAlertRules(feed, widgetRules)
        } else emptyList<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()

        val prefs = androidx.glance.appwidget.state.getAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId)
        val chartResults = prefs[androidx.datastore.preferences.core.intPreferencesKey("chart_results")] ?: 60

        WidgetUpdateHelper.updateWidgetPreferences(
            context = context,
            glanceId = glanceId,
            channel = channelToUse.copy(chartResults = chartResults),
            latestFeed = latestFeed,
            violatedMinFields = violations.filter { it.condition == "LESS_THAN" }.map { it.fieldNumber }.toSet(),
            violatedMaxFields = violations.filter { it.condition == "GREATER_THAN" }.map { it.fieldNumber }.toSet(),
            minSetFields = widgetRules.filter { it.condition == "LESS_THAN" && it.isEnabled }.map { it.fieldNumber }.toSet(),
            maxSetFields = widgetRules.filter { it.condition == "GREATER_THAN" && it.isEnabled }.map { it.fieldNumber }.toSet()
        )
        ValueGridWidget().update(context, glanceId)
    }
}

@AndroidEntryPoint
class ValueGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValueGridWidget()

    @Inject
    lateinit var repository: WidgetBindingRepository

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetReceiver.enqueuePeriodicRefresh(context)
        android.util.Log.i("ValueGridWidgetReceiver", "First ValueGridWidget added - periodic refresh enqueued")
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetReceiver.enqueuePeriodicRefreshIfNeeded(context)
        // Mirror WidgetReceiver pattern: sync binding + trigger immediate render
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
                                    if (this[androidx.datastore.preferences.core.longPreferencesKey("channel_id")] != boundId) {
                                        this[androidx.datastore.preferences.core.longPreferencesKey("channel_id")] = boundId
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
    }
}

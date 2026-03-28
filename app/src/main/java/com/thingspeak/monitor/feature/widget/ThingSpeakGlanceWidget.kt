package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.EntryPointAccessors
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.datastore.SavedChannel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect

class ThingSpeakGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)
        
        // V22 - Self-Healing: if prefs are empty, try to repair from DB
        val boundChannelId = bindingRepo.getBindingSync(appWidgetId)

        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val isRefreshing = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] ?: false
            var name = prefs[androidx.datastore.preferences.core.stringPreferencesKey("channel_name")]
            var bgColor = prefs[androidx.datastore.preferences.core.stringPreferencesKey("bg_color")] ?: "#FFFFFF"
            var textColor = prefs[androidx.datastore.preferences.core.stringPreferencesKey("text_color")]
            var transparency = prefs[androidx.datastore.preferences.core.floatPreferencesKey("transparency")] ?: 1.0f
            var isGlass = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_glass")] ?: false
            val fontSize = prefs[androidx.datastore.preferences.core.intPreferencesKey("font_size")] ?: 12
            
            val visibleFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            val violatedMinSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("violated_min_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val violatedMaxSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("violated_max_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val minSetFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("min_set_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val maxSetFieldsSet = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("max_set_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            
            val entryJson = prefs[androidx.datastore.preferences.core.stringPreferencesKey("cached_entry")]
            
            // If name is missing OR entry is missing, we are in "Loading" state. 
            // Trigger immediate repair if we have a binding.
            if ((name == null || entryJson == null) && boundChannelId != -1L) {
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
            val chartResults = prefs[androidx.datastore.preferences.core.intPreferencesKey("chart_results")] ?: 60
            
            val chartBase64 = prefs[androidx.datastore.preferences.core.stringPreferencesKey("chart_bitmap")]
            val chartBitmap = if (chartBase64 != null) {
                try {
                    val bytes = android.util.Base64.decode(chartBase64, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) { null }
            } else null

            val lastSyncStatus = prefs[androidx.datastore.preferences.core.stringPreferencesKey("last_sync_status")] ?: "NONE"

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
                chartBitmap = chartBitmap,
                isRefreshing = isRefreshing || (name == null),
                lastSyncStatus = lastSyncStatus,
                visibleFields = visibleFieldsSet,
                violatedMinFields = violatedMinSet,
                violatedMaxFields = violatedMaxSet,
                minSetFields = minSetFieldsSet,
                maxSetFields = maxSetFieldsSet,
                textColor = if (textColor.isNullOrBlank()) null else textColor
            )
            
            WidgetUI(data)
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
        val repo = entryPoint.channelRepository()
        val checkAlertRules = entryPoint.checkAlertRulesUseCase()
        
        // Use repo methods to get current alarm state for repair
        val widgetRules = repo.getAlertRules(channelId, appWidgetId)
        val violations = if (latestFeed != null) {
            checkAlertRules(latestFeed!!, widgetRules) 
        } else emptyList<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()
        
        val chartResults = glanceId?.let { gid ->
            val prefs = androidx.glance.appwidget.state.getAppWidgetState(context, WidgetPreferencesStateDefinition, gid)
            prefs[androidx.datastore.preferences.core.intPreferencesKey("chart_results")]
        } ?: 60
        
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
        ThingSpeakGlanceWidget().update(context, glanceId)
    }
}

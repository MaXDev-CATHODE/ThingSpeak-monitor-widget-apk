package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.GlanceId
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import org.json.JSONObject

/**
 * Centralized utility to push data from Room/API to Glance Preferences.
 * Ensures consistent key usage across different widget types and sync sources.
 */
object WidgetUpdateHelper {

    suspend fun updateWidgetPreferences(
        context: Context,
        glanceId: GlanceId,
        channel: SavedChannel,
        latestFeed: FeedEntry?,
        chartBitmapBase64: String? = null,
        violatedMinFields: Set<Int> = emptySet(),
        violatedMaxFields: Set<Int> = emptySet(),
        minSetFields: Set<Int> = emptySet(),
        maxSetFields: Set<Int> = emptySet()
    ) {
        android.util.Log.d("TS_DEBUG", "updateWidgetPreferences: START for channel ${channel.id}, glanceId=$glanceId")
        val cachedEntryStr = latestFeed?.let { f ->
            try {
                JSONObject().apply {
                    put("channelId", channel.id)
                    put("createdAt", f.createdAt)
                    put("entryId", f.entryId)
                    (1..8).forEach { i ->
                        put("field$i", f.fields[i] ?: "null")
                    }
                }.toString()
            } catch (e: Exception) { null }
        }

        val fieldNamesJson = JSONObject().apply {
            channel.fieldNames.forEach { (k, v) -> put(k.toString(), v) }
        }.toString()

        val fieldUnitsJson = JSONObject().apply {
            channel.fieldUnits.forEach { (k, v) -> put(k.toString(), v) }
        }.toString()

        updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { prefs ->
            android.util.Log.v("TS_DEBUG", "PUSHING preferences to Glance for channel ${channel.id}")
            prefs.toMutablePreferences().apply {
                this[longPreferencesKey("channel_id")] = channel.id
                this[stringPreferencesKey("channel_name")] = channel.name
                this[intPreferencesKey("rounding")] = channel.chartRounding
                this[stringPreferencesKey("field_names")] = fieldNamesJson
                this[stringPreferencesKey("field_units")] = fieldUnitsJson
                this[booleanPreferencesKey("is_refreshing")] = false
                this[stringPreferencesKey("last_sync_status")] = channel.lastSyncStatus
                channel.timezone?.let { this[stringPreferencesKey("channel_timezone")] = it }
                
                if (cachedEntryStr != null) {
                    this[stringPreferencesKey("cached_entry")] = cachedEntryStr
                }
                
                if (chartBitmapBase64 != null) {
                    this[stringPreferencesKey("chart_bitmap")] = chartBitmapBase64
                }
                
                // 2. PROTECT visual settings from being overwritten (Agent 2.1 Fix)
                // We only set them if they don't exist yet (initial setup)
                if (this[stringPreferencesKey("bg_color")] == null) {
                    this[stringPreferencesKey("bg_color")] = channel.widgetBgColorHex ?: "#FFFFFF"
                }
                if (this[stringPreferencesKey("text_color")] == null) {
                    this[stringPreferencesKey("text_color")] = channel.widgetTextColorHex ?: ""
                }
                if (this[floatPreferencesKey("transparency")] == null) {
                    this[floatPreferencesKey("transparency")] = channel.widgetTransparency
                }
                if (this[intPreferencesKey("font_size")] == null) {
                    this[intPreferencesKey("font_size")] = channel.widgetFontSize
                }
                if (this[booleanPreferencesKey("is_glass")] == null) {
                    this[booleanPreferencesKey("is_glass")] = channel.isGlassmorphismEnabled ?: false
                }
                
                // 3. SYNC visible fields only as INITIAL default if they don't exist in Preferences yet (Agent 2.1 Fix)
                val currentVisible = this[stringSetPreferencesKey("visible_fields")]
                if (currentVisible == null || currentVisible.isEmpty()) {
                    channel.widgetVisibleFields?.let { fields ->
                        this[stringSetPreferencesKey("visible_fields")] = fields.map { it.toString() }.toSet()
                    }
                }
                
                // Always sync results count as it affects data loading
                this[intPreferencesKey("chart_results")] = channel.chartResults ?: 60
                
                // 4. SYNC Alarms state (Agent 3.0)
                this[stringSetPreferencesKey("violated_min_fields")] = violatedMinFields.map { it.toString() }.toSet()
                this[stringSetPreferencesKey("violated_max_fields")] = violatedMaxFields.map { it.toString() }.toSet()
                this[stringSetPreferencesKey("min_set_fields")] = minSetFields.map { it.toString() }.toSet()
                this[stringSetPreferencesKey("max_set_fields")] = maxSetFields.map { it.toString() }.toSet()
            }
        }
        android.util.Log.i("TS_DEBUG", "updateWidgetPreferences: SUCCESS for channel ${channel.id}")
    }
}

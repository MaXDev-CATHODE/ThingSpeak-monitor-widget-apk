package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.GlanceId
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Centralized utility to push data from Room/API to Glance Preferences.
 * Ensures consistent key usage across different widget types and sync sources.
 */
object WidgetUpdateHelper {

    const val MAX_GLANCE_RETRIES = 2

    /**
     * Shared updateAppWidget logic for both ThingSpeakGlanceWidget and ValueGridWidget.
     * Eliminates ~80% code duplication between the two implementations.
     *
     * @param onGenerateChart Optional callback to generate chart bitmap (only for chart widget).
     */
    suspend fun performUpdate(
        context: Context,
        appWidgetId: Int,
        widgetInstanceFactory: () -> androidx.glance.appwidget.GlanceAppWidget,
        onGenerateChart: suspend (SavedChannel, Long) -> String? = { _, _ -> null }
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val bindingRepo = entryPoint.widgetBindingRepository()
        val chanPrefs = entryPoint.channelPreferences()
        val getChannelFeed = entryPoint.getChannelFeedUseCase()

        val channelId = bindingRepo.getBindingSync(appWidgetId)
        if (channelId == -1L) return

        val savedChannels = chanPrefs.observe().first()
        val channel = savedChannels.find { it.id == channelId } ?: return

        val feeds = getChannelFeed.observe(channelId).first()
        val latestFeed = feeds.lastOrNull()

        val glanceId = findWidgetGlanceId(context, appWidgetId, maxRetries = MAX_GLANCE_RETRIES)
        if (glanceId == null) {
            android.util.Log.e("TS_DEBUG", "updateWidget: Could not find GlanceId for $appWidgetId")
            return
        }

        // Alert rules
        val repo = entryPoint.channelRepository()
        val checkRules = entryPoint.checkAlertRulesUseCase()
        val widgetAlerts = repo.getAlertRules(channelId, appWidgetId)

        val violations = latestFeed?.let { checkRules(it, widgetAlerts) }
            ?: emptyList<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()

        // Chart generation (only for chart widget; grid widgets get null)
        val chartBase64 = onGenerateChart(channel, channelId)

        // Resolve chartResults from existing prefs (preserve user setting)
        val prefs = getAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId)
        val cachedChartResults = prefs?.get(WidgetPrefsKeys.KEY_CHART_RESULTS) ?: 60

        // Push to DataStore Preferences
        updateWidgetPreferences(
            context = context,
            glanceId = glanceId,
            channel = channel.copy(chartResults = cachedChartResults),
            latestFeed = latestFeed,
            chartBitmapBase64 = chartBase64,
            violatedMinFields = violations
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN }
                .map { it.fieldNumber }.toSet(),
            violatedMaxFields = violations
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN }
                .map { it.fieldNumber }.toSet(),
            minSetFields = widgetAlerts
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN && it.isEnabled }
                .map { it.fieldNumber }.toSet(),
            maxSetFields = widgetAlerts
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN && it.isEnabled }
                .map { it.fieldNumber }.toSet()
        )

        // Re-render widget
        widgetInstanceFactory().update(context, glanceId)
    }

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
                        val value = f.fields[i]
                        if (value != null) put("field$i", value)
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
            android.util.Log.d("TS_DEBUG", "PUSHING preferences to widget for channel ${channel.id}")
            prefs.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_CHANNEL_ID] = channel.id
                this[WidgetPrefsKeys.KEY_CHANNEL_NAME] = channel.name
                this[WidgetPrefsKeys.KEY_ROUNDING] = channel.chartRounding
                this[WidgetPrefsKeys.KEY_FIELD_NAMES] = fieldNamesJson
                this[WidgetPrefsKeys.KEY_FIELD_UNITS] = fieldUnitsJson
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
                this[WidgetPrefsKeys.KEY_LAST_SYNC_STATUS] = channel.lastSyncStatus
                channel.timezone?.let { this[WidgetPrefsKeys.KEY_CHANNEL_TIMEZONE] = it }

                if (cachedEntryStr != null) {
                    this[WidgetPrefsKeys.KEY_CACHED_ENTRY] = cachedEntryStr
                }

                if (chartBitmapBase64 != null) {
                    this[WidgetPrefsKeys.KEY_CHART_BITMAP] = chartBitmapBase64
                }

                // PROTECT visual settings — only overwrite if user hasn't customized them via config screen
                val isCustomized = this[WidgetPrefsKeys.KEY_WIDGET_VISUALS_CUSTOMIZED] ?: false
                if (!isCustomized) {
                    this[WidgetPrefsKeys.KEY_BG_COLOR] = channel.widgetBgColorHex ?: "#FFFFFF"
                    this[WidgetPrefsKeys.KEY_TEXT_COLOR] = channel.widgetTextColorHex ?: ""
                    this[WidgetPrefsKeys.KEY_TRANSPARENCY] = channel.widgetTransparency
                    this[WidgetPrefsKeys.KEY_FONT_SIZE] = channel.widgetFontSize
                    this[WidgetPrefsKeys.KEY_IS_GLASS] = channel.isGlassmorphismEnabled ?: false
                }

                val currentVisible = this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS]
                if (currentVisible == null || currentVisible.isEmpty()) {
                    channel.widgetVisibleFields?.let { fields ->
                        this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS] = fields.map { it.toString() }.toSet()
                    }
                }

                this[WidgetPrefsKeys.KEY_CHART_RESULTS] = channel.chartResults ?: 60

                this[WidgetPrefsKeys.KEY_VIOLATED_MIN_FIELDS] = violatedMinFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_VIOLATED_MAX_FIELDS] = violatedMaxFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_MIN_SET_FIELDS] = minSetFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_MAX_SET_FIELDS] = maxSetFields.map { it.toString() }.toSet()

                this[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] = true
            }
        }
        android.util.Log.i("TS_DEBUG", "updateWidgetPreferences: SUCCESS for channel ${channel.id}")
    }
}
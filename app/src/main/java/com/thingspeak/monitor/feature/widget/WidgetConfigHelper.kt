package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.EntryPointAccessors
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.AlertRule
import kotlinx.coroutines.flow.first

data class SavedWidgetPrefs(
    val channelId: Long?,
    val bgColor: String?,
    val textColor: String?,
    val transparency: Float?,
    val fontSize: Int?,
    val isGlass: Boolean?,
    val bgColorMode: String?,
    val visibleFields: Set<Int>?
)

suspend fun loadSavedWidgetPrefs(
    context: Context,
    appWidgetId: Int,
    widgetClasses: List<Class<out androidx.glance.appwidget.GlanceAppWidget>>
): SavedWidgetPrefs {
    val gId = findWidgetGlanceId(context, appWidgetId, widgetClasses = widgetClasses)
    val prefs = if (gId != null) {
        getAppWidgetState<Preferences>(context, WidgetPreferencesStateDefinition, gId)
    } else null

    return SavedWidgetPrefs(
        channelId = prefs?.get(WidgetPrefsKeys.KEY_CHANNEL_ID),
        bgColor = prefs?.get(WidgetPrefsKeys.KEY_BG_COLOR),
        textColor = prefs?.get(WidgetPrefsKeys.KEY_TEXT_COLOR),
        transparency = prefs?.get(WidgetPrefsKeys.KEY_TRANSPARENCY),
        fontSize = prefs?.get(WidgetPrefsKeys.KEY_FONT_SIZE),
        isGlass = prefs?.get(WidgetPrefsKeys.KEY_IS_GLASS),
        bgColorMode = prefs?.get(WidgetPrefsKeys.KEY_BG_COLOR_MODE),
        visibleFields = prefs?.get(WidgetPrefsKeys.KEY_VISIBLE_FIELDS)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
    )
}

suspend fun saveWidgetConfigAndRefresh(
    context: Context,
    appWidgetId: Int,
    glanceId: GlanceId?,
    channelId: Long,
    channelName: String,
    apiKey: String,
    bgColor: String?,
    textColor: String?,
    transparency: Float,
    fontSize: Int,
    visibleFields: Set<Int>,
    isGlass: Boolean,
    bgColorMode: String?,
    chartResultsCount: Int,
    alertRules: List<AlertRule>,
    skipChartClear: Boolean,
    channelPreferences: ChannelPreferences,
    widgetBindingRepository: WidgetBindingRepository,
    updateWidget: suspend () -> Unit,
    onResult: () -> Unit
) {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
    )
    WidgetUpdateHelper.saveWidgetCoreConfig(
        entryPoint = entryPoint,
        channelPreferences = channelPreferences,
        widgetBindingRepository = widgetBindingRepository,
        appWidgetId = appWidgetId,
        channelId = channelId,
        apiKey = apiKey,
        channelName = channelName,
        alertRules = alertRules
    )

    if (glanceId != null) {
        val channelDefaults = channelPreferences.observe().first().find { it.id == channelId }

        val isBgCustomized = bgColor != null && channelDefaults != null &&
            bgColor != channelDefaults.widgetBgColorHex
        val isTextCustomized = textColor != null && channelDefaults != null &&
            textColor != channelDefaults.widgetTextColorHex
        val isTransCustomized = channelDefaults != null &&
            kotlin.math.abs(transparency - channelDefaults.widgetTransparency) > 0.001f
        val isFontCustomized = channelDefaults != null && fontSize != channelDefaults.widgetFontSize
        val isGlassCustomized = channelDefaults != null && isGlass != channelDefaults.isGlassmorphismEnabled

        updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_CHANNEL_ID] = channelId
                this[WidgetPrefsKeys.KEY_CHANNEL_NAME] = channelName
                this[WidgetPrefsKeys.KEY_BG_COLOR] = bgColor ?: "#FFFFFF"
                this[WidgetPrefsKeys.KEY_TEXT_COLOR] = textColor ?: ""
                this[WidgetPrefsKeys.KEY_TRANSPARENCY] = transparency
                this[WidgetPrefsKeys.KEY_FONT_SIZE] = fontSize
                this[WidgetPrefsKeys.KEY_IS_GLASS] = isGlass
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = true
                this[WidgetPrefsKeys.KEY_CHART_RESULTS] = chartResultsCount
                this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS] = visibleFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_WIDGET_VISUALS_CUSTOMIZED] = true
                this[WidgetPrefsKeys.KEY_BG_COLOR_CUSTOMIZED] = isBgCustomized
                this[WidgetPrefsKeys.KEY_TEXT_COLOR_CUSTOMIZED] = isTextCustomized
                this[WidgetPrefsKeys.KEY_TRANSPARENCY_CUSTOMIZED] = isTransCustomized
                this[WidgetPrefsKeys.KEY_FONT_SIZE_CUSTOMIZED] = isFontCustomized
                this[WidgetPrefsKeys.KEY_IS_GLASS_CUSTOMIZED] = isGlassCustomized
                this[WidgetPrefsKeys.KEY_BG_COLOR_MODE] = bgColorMode ?: WidgetPrefsKeys.COLOR_MODE_CUSTOM
                this[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] = false
                this[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] = 0
                if (!skipChartClear) {
                    this.remove(WidgetPrefsKeys.KEY_CHART_BITMAP)
                    this.remove(WidgetPrefsKeys.KEY_CHART_FILE)
                }
            }
        }

        if (!skipChartClear) {
            WidgetChartCache.clear(context, appWidgetId)
        }

        performWidgetRefreshAction(
            context = context,
            glanceId = glanceId,
            updateWidget = updateWidget,
            uniqueWorkPrefix = "config_refresh"
        )
    } else {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onResult()
        }
        return
    }

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        onResult()
    }
}
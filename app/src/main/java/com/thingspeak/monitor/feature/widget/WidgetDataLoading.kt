package com.thingspeak.monitor.feature.widget

import androidx.datastore.preferences.core.Preferences
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity

fun parseCachedEntry(entryJson: String?): FeedEntryEntity? {
    if (entryJson == null) return null
    return try {
        val jsonObj = org.json.JSONObject(entryJson)
        val rawFields = (1..8).map { i ->
            jsonObj.optString("field$i", "").takeIf { it != "null" && it.isNotBlank() }
        }
        FeedEntryEntity(
            channelId = jsonObj.optLong("channelId", 0L),
            createdAt = jsonObj.optString("createdAt", ""),
            entryId = jsonObj.optLong("entryId", 0L),
            field1 = rawFields[0],
            field2 = rawFields[1],
            field3 = rawFields[2],
            field4 = rawFields[3],
            field5 = rawFields[4],
            field6 = rawFields[5],
            field7 = rawFields[6],
            field8 = rawFields[7]
        )
    } catch (e: Exception) {
        null
    }
}

fun parseFieldJsonMap(jsonStr: String?): Map<Int, String> {
    if (jsonStr == null) return emptyMap()
    return try {
        val jsonObj = org.json.JSONObject(jsonStr)
        val map = mutableMapOf<Int, String>()
        jsonObj.keys().forEach { key -> map[key.toInt()] = jsonObj.getString(key) }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

fun loadWidgetDataFromPreferences(
    prefs: Preferences,
    boundChannelId: Long,
    realSyncIntervalMinutes: Long,
    skipChartBitmap: Boolean = false
): WidgetData {
    val isRefreshing = prefs[WidgetPrefsKeys.KEY_IS_REFRESHING] ?: false
    val name = prefs[WidgetPrefsKeys.KEY_CHANNEL_NAME]
    val bgColor = prefs[WidgetPrefsKeys.KEY_BG_COLOR] ?: "#FFFFFF"
    val textColor = prefs[WidgetPrefsKeys.KEY_TEXT_COLOR]
    val transparency = prefs[WidgetPrefsKeys.KEY_TRANSPARENCY] ?: 1.0f
    val isGlass = prefs[WidgetPrefsKeys.KEY_IS_GLASS] ?: false
    val fontSize = prefs[WidgetPrefsKeys.KEY_FONT_SIZE] ?: 12
    val chartResults = prefs[WidgetPrefsKeys.KEY_CHART_RESULTS] ?: 60

    val visibleFieldsSet = prefs[WidgetPrefsKeys.KEY_VISIBLE_FIELDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()

    val violatedMinSet = prefs[WidgetPrefsKeys.KEY_VIOLATED_MIN_FIELDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()
        ?: emptySet()
    val violatedMaxSet = prefs[WidgetPrefsKeys.KEY_VIOLATED_MAX_FIELDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()
        ?: emptySet()
    val minSetFieldsSet = prefs[WidgetPrefsKeys.KEY_MIN_SET_FIELDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()
        ?: emptySet()
    val maxSetFieldsSet = prefs[WidgetPrefsKeys.KEY_MAX_SET_FIELDS]
        ?.mapNotNull { it.toIntOrNull() }
        ?.toSet()
        ?: emptySet()

    val entry = parseCachedEntry(prefs[WidgetPrefsKeys.KEY_CACHED_ENTRY])
    val fieldNames = parseFieldJsonMap(prefs[WidgetPrefsKeys.KEY_FIELD_NAMES])
    val fieldUnits = parseFieldJsonMap(prefs[WidgetPrefsKeys.KEY_FIELD_UNITS])

    val rounding = prefs[WidgetPrefsKeys.KEY_ROUNDING] ?: 2
    val lastSyncStatus = prefs[WidgetPrefsKeys.KEY_LAST_SYNC_STATUS] ?: WidgetPrefsKeys.STATUS_NONE
    val channelTimezone = prefs[WidgetPrefsKeys.KEY_CHANNEL_TIMEZONE]
    val colorMode = prefs[WidgetPrefsKeys.KEY_BG_COLOR_MODE]

    val chartFile = if (skipChartBitmap) null else prefs[WidgetPrefsKeys.KEY_CHART_FILE]
    val chartBitmap = if (chartFile != null) {
        WidgetChartCache.load(chartFile)
    } else {
        val chartBase64 = if (skipChartBitmap) null else prefs[WidgetPrefsKeys.KEY_CHART_BITMAP]
        if (chartBase64 != null) {
            try {
                val bytes = android.util.Base64.decode(chartBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { null }
        } else null
    }

    return WidgetData(
        channelName = name ?: WidgetPrefsKeys.LOADING_PLACEHOLDER,
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
        syncIntervalMinutes = realSyncIntervalMinutes,
        visibleFields = visibleFieldsSet,
        violatedMinFields = violatedMinSet,
        violatedMaxFields = violatedMaxSet,
        minSetFields = minSetFieldsSet,
        maxSetFields = maxSetFieldsSet,
        textColor = if (textColor.isNullOrBlank()) null else textColor,
        channelTimezone = channelTimezone,
        bgColorMode = colorMode
    )
}
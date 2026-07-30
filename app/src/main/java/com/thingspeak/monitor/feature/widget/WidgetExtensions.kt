package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.datastore.preferences.core.Preferences
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import java.io.ByteArrayOutputStream

const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L

val Int.sp: TextUnit get() = TextUnit(this.toFloat(), TextUnitType.Sp)
val Int.dp: Dp get() = Dp(this.toFloat())

fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)) / 255
    return darkness >= 0.5
}

fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 90): String? {
    if (bitmap == null) return null
    return try {
        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, quality, stream)
        android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }
}

suspend fun findWidgetGlanceId(
    context: Context,
    appWidgetId: Int,
    maxRetries: Int = 2,
    widgetClasses: List<Class<out androidx.glance.appwidget.GlanceAppWidget>> = emptyList()
): androidx.glance.GlanceId? {
    var retries = maxRetries
    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
    while (retries > 0) {
        try {
            val officialId = manager.getGlanceIdBy(appWidgetId)
            if (officialId != null) {
                android.util.Log.d("TS_DEBUG", "findGlanceId: found via official API for $appWidgetId")
                return officialId
            }
            for (widgetClass in widgetClasses) {
                val foundId = manager.getGlanceIds(widgetClass).find {
                    manager.getAppWidgetId(it) == appWidgetId
                }
                if (foundId != null) {
                    android.util.Log.d("TS_DEBUG", "findGlanceId: exhaustive search found for $appWidgetId")
                    return foundId
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TS_DEBUG", "findGlanceId: attempt failed for $appWidgetId ($retries left)", e)
        }
        if (retries > 1) kotlinx.coroutines.delay(100)
        retries--
    }
    android.util.Log.e("TS_DEBUG", "findGlanceId: FAILED for $appWidgetId after $maxRetries retries")
    return null
}

fun parseCachedEntry(entryJson: String?): FeedEntryEntity? {
    if (entryJson == null) return null
    return try {
        val jsonObj = org.json.JSONObject(entryJson)
        FeedEntryEntity(
            channelId = jsonObj.optLong("channelId", 0L),
            createdAt = jsonObj.optString("createdAt", ""),
            entryId = jsonObj.optLong("entryId", 0L),
            field1 = jsonObj.optString("field1", "").takeIf { it != "null" && it.isNotBlank() },
            field2 = jsonObj.optString("field2", "").takeIf { it != "null" && it.isNotBlank() },
            field3 = jsonObj.optString("field3", "").takeIf { it != "null" && it.isNotBlank() },
            field4 = jsonObj.optString("field4", "").takeIf { it != "null" && it.isNotBlank() },
            field5 = jsonObj.optString("field5", "").takeIf { it != "null" && it.isNotBlank() },
            field6 = jsonObj.optString("field6", "").takeIf { it != "null" && it.isNotBlank() },
            field7 = jsonObj.optString("field7", "").takeIf { it != "null" && it.isNotBlank() },
            field8 = jsonObj.optString("field8", "").takeIf { it != "null" && it.isNotBlank() }
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
    realSyncIntervalMinutes: Long
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

    val chartBase64 = prefs[WidgetPrefsKeys.KEY_CHART_BITMAP]
    val chartBitmap = if (chartBase64 != null) {
        try {
            val bytes = android.util.Base64.decode(chartBase64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    } else null

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
        channelTimezone = channelTimezone
    )
}

suspend fun performWidgetRefreshAction(
    context: Context,
    glanceId: androidx.glance.GlanceId,
    updateWidget: suspend () -> Unit,
    uniqueWorkPrefix: String
) {
    val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
    )
    val bindingRepo = entryPoint.widgetBindingRepository()
    val channelId = bindingRepo.getBindingSync(appWidgetId)

    if (channelId == -1L) return

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
        android.util.Log.e("WidgetExtensions", "Failed to enqueue DataSyncWorker", e)
        androidx.glance.appwidget.state.updateAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        ) { p ->
            p.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
            }
        }
        updateWidget()
        return
    }

    // Fallback timeout: clear refreshing after 60s if worker didn't finish.
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
        kotlinx.coroutines.delay(60_000)
        val currentPrefs = androidx.glance.appwidget.state.getAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        )
        if (currentPrefs[WidgetPrefsKeys.KEY_IS_REFRESHING] == true) {
            androidx.glance.appwidget.state.updateAppWidgetState(
                context, WidgetPreferencesStateDefinition, glanceId
            ) { p ->
                p.toMutablePreferences().apply {
                    this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
                }
            }
            updateWidget()
        }
    }
}
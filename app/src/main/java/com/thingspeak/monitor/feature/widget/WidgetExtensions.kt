package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.datastore.preferences.core.Preferences
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val WIDGET_LOG_TAG = "TS_DEBUG"
const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L

private val refreshTimeoutJobs = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Job>()
private val activeRefreshes = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

fun cancelRefreshTimeout(appWidgetId: Int) {
    refreshTimeoutJobs.remove(appWidgetId)?.cancel()
    activeRefreshes.remove(appWidgetId)
}

/** Must be called when a refresh completes (success or failure) to prevent map leak. */
fun onRefreshCompleted(appWidgetId: Int) {
    refreshTimeoutJobs.remove(appWidgetId)
    activeRefreshes.remove(appWidgetId)
}

fun isSystemDarkMode(context: Context): Boolean {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMode == Configuration.UI_MODE_NIGHT_YES
}

fun darkModeAutoBgColor(data: WidgetData, context: Context): String? {
    val hex = data.bgColorHex
    if (isSystemDarkMode(context) && hex != null) {
        val color = try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { null }
        if (color != null && isColorDark(color).not()) {
            // Use system dark widget background when available (API 31+), fall back to dark gray
            val darkColor = try {
                context.resources.getColor(android.R.color.background_dark, null)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#212121")
            }
            return String.format("#%06X", 0xFFFFFF and darkColor)
        }
    }
    return hex
}

/**
 * Resolves background color for widget, supporting system-wide theme colors
 * (Material You / Dynamic Colors) when the user opts into the system color mode.
 */
fun resolveSystemAwareBackground(
    prefHex: String?,
    isDarkMode: Boolean,
    context: Context,
    colorMode: String? = WidgetPrefsKeys.COLOR_MODE_CUSTOM
): Int {
    if (colorMode == WidgetPrefsKeys.COLOR_MODE_SYSTEM) {
        // Use system accent/dynamic color when available
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val accentRes = if (isDarkMode)
                    android.R.color.system_accent1_200
                else
                    android.R.color.system_accent1_500
                context.resources.getColor(accentRes, context.theme)
            } catch (_: Exception) {
                if (isDarkMode) android.graphics.Color.parseColor("#212121")
                else android.graphics.Color.parseColor("#FFFFFF")
            }
        } else {
            if (isDarkMode) android.graphics.Color.parseColor("#212121")
            else android.graphics.Color.parseColor("#FFFFFF")
        }
    }
    return try {
        android.graphics.Color.parseColor(prefHex ?: "#FFFFFF")
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }
}

fun darkModeAutoTextColor(data: WidgetData, isDarkBg: Boolean): androidx.compose.ui.graphics.Color {
    val tc = data.textColor
    if (tc != null && tc.startsWith("#")) {
        return try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(tc)) }
            catch (_: Exception) { if (isDarkBg) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black }
    }
    return if (isDarkBg) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
}

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
    widgetClasses: List<Class<out androidx.glance.appwidget.GlanceAppWidget>> = listOf(
        ThingSpeakGlanceWidget::class.java,
        ValueGridWidget::class.java
    )
): androidx.glance.GlanceId? {
    var retries = maxRetries
    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
    while (retries > 0) {
        try {
            val officialId = manager.getGlanceIdBy(appWidgetId)
            if (officialId != null) {
                android.util.Log.d(WIDGET_LOG_TAG, "findGlanceId: found via official API for $appWidgetId")
                return officialId
            }
            for (widgetClass in widgetClasses) {
                val foundId = manager.getGlanceIds(widgetClass).find {
                    manager.getAppWidgetId(it) == appWidgetId
                }
                if (foundId != null) {
                    android.util.Log.d(WIDGET_LOG_TAG, "findGlanceId: exhaustive search found for $appWidgetId")
                    return foundId
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(WIDGET_LOG_TAG, "findGlanceId: attempt failed for $appWidgetId ($retries left)", e)
        }
        if (retries > 1) kotlinx.coroutines.delay(100)
        retries--
    }
    android.util.Log.e(WIDGET_LOG_TAG, "findGlanceId: FAILED for $appWidgetId after $maxRetries retries")
    return null
}

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

/**
 * Safe field accessor for FeedEntryEntity — eliminates duplicated when(fieldNum) branches.
 */
fun FeedEntryEntity.getField(fieldNum: Int): String? = when (fieldNum) {
    1 -> field1
    2 -> field2
    3 -> field3
    4 -> field4
    5 -> field5
    6 -> field6
    7 -> field7
    8 -> field8
    else -> null
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

    // Chart loading: prefer file cache (new), fallback to legacy base64 (old DataStore)
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

suspend fun performWidgetRefreshAction(
    context: Context,
    glanceId: androidx.glance.GlanceId,
    updateWidget: suspend () -> Unit,
    uniqueWorkPrefix: String
) {
    val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

    if (!activeRefreshes.add(appWidgetId)) {
        android.util.Log.d(WIDGET_LOG_TAG, "performWidgetRefreshAction: refresh already in progress for $appWidgetId, ignored")
        return
    }
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
    )

    // Guard: don't start a sync cycle if the device is offline.
    // The WorkManager constraint would silently enqueue the job forever;
    // instead we short‑circuit and leave the refreshing flag unchanged.
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val isOnline = if (activeNetwork != null) {
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } else false
    if (!isOnline) {
        android.util.Log.w(WIDGET_LOG_TAG, "performWidgetRefreshAction: device offline, refresh aborted for widget $appWidgetId")
        activeRefreshes.remove(appWidgetId)
        return
    }

    val bindingRepo = entryPoint.widgetBindingRepository()
    val channelId = bindingRepo.getBindingSync(appWidgetId)

    if (channelId == -1L) {
        activeRefreshes.remove(appWidgetId)
        return
    }

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
        activeRefreshes.remove(appWidgetId)
        return
    }

    // Fallback timeout: clear refreshing after 60s if worker didn't finish (previous job cancelled on re-tap)
    refreshTimeoutJobs.remove(appWidgetId)?.cancel()
    val timeoutJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        delay(60_000)
        onRefreshCompleted(appWidgetId)
        val currentPrefs = androidx.glance.appwidget.state.getAppWidgetState(
            context, WidgetPreferencesStateDefinition, glanceId
        )
        if (currentPrefs[WidgetPrefsKeys.KEY_IS_REFRESHING] == true) {
            androidx.glance.appwidget.state.updateAppWidgetState(
                context, WidgetPreferencesStateDefinition, glanceId
            ) { p -> p.toMutablePreferences().apply { this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false } }
            updateWidget()
        }
    }
    refreshTimeoutJobs[appWidgetId] = timeoutJob
}
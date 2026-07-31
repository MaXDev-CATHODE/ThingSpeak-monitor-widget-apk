package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity

const val WIDGET_LOG_TAG = "TS_DEBUG"
const val DEFAULT_SYNC_INTERVAL_MINUTES = 30L

val Int.sp: TextUnit get() = TextUnit(this.toFloat(), TextUnitType.Sp)
val Int.dp: Dp get() = Dp(this.toFloat())

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

suspend fun findWidgetGlanceId(
    context: Context,
    appWidgetId: Int,
    maxRetries: Int = 2,
    widgetClasses: List<Class<out androidx.glance.appwidget.GlanceAppWidget>> = WidgetRegistry.ALL_CLASSES
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
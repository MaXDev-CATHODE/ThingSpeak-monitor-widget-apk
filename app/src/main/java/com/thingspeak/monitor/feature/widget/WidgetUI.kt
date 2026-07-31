package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.layout.Spacer
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.glance.Image
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.FontWeight
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import androidx.glance.text.TextAlign

data class WidgetData(
    val channelName: String?,
    val channelId: Long,
    val entry: FeedEntryEntity?,
    val fieldNames: Map<Int, String> = emptyMap(),
    val fieldUnits: Map<Int, String> = emptyMap(),
    val bgColorHex: String? = "#FFFFFF",
    val textColor: String? = "#000000",
    val transparency: Float = 1.0f,
    val fontSize: Int = 12,
    val chartRounding: Int = 2,
    val chartResults: Int = 60,
    val isGlass: Boolean = false,
    val violatedMinFields: Set<Int> = emptySet(),
    val violatedMaxFields: Set<Int> = emptySet(),
    val minSetFields: Set<Int> = emptySet(),
    val maxSetFields: Set<Int> = emptySet(),
    val syncIntervalMinutes: Long = 30,
    val lastSyncStatus: String = WidgetPrefsKeys.STATUS_NONE,
    val visibleFields: Set<Int>? = null,
    val chartType: String? = "line",
    val chartBitmap: android.graphics.Bitmap? = null,
    val isRefreshing: Boolean = false,
    val channelTimezone: String? = null,
    val bgColorMode: String? = null
)

@Composable
fun WidgetUI(data: WidgetData) {
    // NOTE: try/catch around a composable call is not allowed by the Compose compiler.
    // Glance handles widget composition errors natively (error placeholder).
    WidgetUIContent(data)
}

@Composable
private fun WidgetUIContent(data: WidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    
    // Dynamic sizing based on widget dimensions
    val isCompact = size.height < WidgetPrefsKeys.HEIGHT_COMPACT_THRESHOLD.dp || size.width < WidgetPrefsKeys.WIDTH_COMPACT_THRESHOLD.dp
    val isTiny = size.height < WidgetPrefsKeys.HEIGHT_TINY_THRESHOLD.dp || size.width < WidgetPrefsKeys.WIDTH_TINY_THRESHOLD.dp
    
    val titleSize = when {
        isTiny -> 10
        isCompact -> 11
        else -> data.fontSize + 2
    }
    val fieldSize = when {
        isTiny -> 8
        isCompact -> 9
        else -> data.fontSize
    }
    val subSize = when {
        isTiny -> 7
        isCompact -> 8
        else -> data.fontSize - 2
    }
    val pad = if (isCompact) 4 else 8
    
    // Dark mode auto-detection: swap default white bg → dark when system is dark
    val effectiveBgHex = darkModeAutoBgColor(data, context)
    val isDarkMode = isSystemDarkMode(context)
    val baseColor = resolveSystemAwareBackground(
        prefHex = effectiveBgHex,
        isDarkMode = isDarkMode,
        context = context,
        colorMode = data.bgColorMode
    )

    val bgColor = if (data.isGlass) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color(baseColor).copy(alpha = data.transparency)
    }

    val isDarkBg = try {
        isColorDark(baseColor)
    } catch (e: Exception) {
        false
    }
    val textColor = darkModeAutoTextColor(data, isDarkBg)
    
    val buttonBg = if (isDarkBg) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bgColor))
            .cornerRadius(12.dp)
            .padding(pad.dp)
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.channelName ?: "ThingSpeak",
                        style = TextStyle(
                            color = ColorProvider(textColor),
                            fontSize = titleSize.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    if (data.lastSyncStatus == WidgetPrefsKeys.STATUS_ERROR_SYNC) {
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text = "⚠",
                            style = TextStyle(
                                color = ColorProvider(Color.Red),
                                fontSize = titleSize.sp
                            )
                        )
                    }
                }
                if (!isTiny) {
                    if (data.entry != null) {
                        val timeStr = WidgetUtils.formatTime(data.entry.createdAt, data.channelTimezone)
                        val isStale = WidgetUtils.isDataStale(data.entry.createdAt, data.syncIntervalMinutes * 60 * 1000L)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Measured: $timeStr",
                                style = TextStyle(
                                    color = ColorProvider(textColor.copy(alpha = 0.6f)),
                                    fontSize = (subSize - 1).sp
                                )
                            )
                            if (isStale) {
                                Spacer(GlanceModifier.width(3.dp))
                                Text(
                                    text = "⌛",
                                    style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.6f)), fontSize = (subSize - 1).sp)
                                )
                            }
                        }
                    } else if (data.channelName != WidgetPrefsKeys.LOADING_PLACEHOLDER && data.channelId != -1L) {
                        Text(
                            text = "Waiting for data...",
                            style = TextStyle(
                                color = ColorProvider(textColor.copy(alpha = 0.35f)),
                                fontSize = (subSize - 1).sp
                            )
                        )
                    }
                }
            }
            
            // Buttons: EDIT + REF
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .background(buttonBg)
                        .cornerRadius(6.dp)
                        .clickable(actionRunCallback<GlanceEditAction>())
                ) {
                    Text(
                        text = "✎",
                        style = TextStyle(
                            color = ColorProvider(textColor),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
                Spacer(GlanceModifier.width(3.dp))
                Box(
                    modifier = GlanceModifier
                        .background(buttonBg)
                        .cornerRadius(6.dp)
                        .clickable(actionRunCallback<GlanceRefreshAction>())
                ) {
                    Text(
                        text = if (data.isRefreshing) "●" else "↻",
                        style = TextStyle(
                            color = ColorProvider(textColor),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }
        
        Spacer(GlanceModifier.height(if (isCompact) 2.dp else 4.dp))
        
        // Fields
        val fieldsToShow = (1..8).filter { fieldNum ->
            val name = data.fieldNames[fieldNum]
            val isVisible = data.visibleFields?.contains(fieldNum) ?: true
            !name.isNullOrBlank() && isVisible
        }
        
        val maxFields = when {
            isTiny -> WidgetPrefsKeys.TINY_MAX_FIELDS
            isCompact -> WidgetPrefsKeys.COMPACT_MAX_FIELDS
            else -> 8
        }
        
        fieldsToShow.take(maxFields).forEach { fieldNum ->
            val value = data.entry?.getField(fieldNum)
            
            val unit = data.fieldUnits[fieldNum] ?: ""
            val roundedValue = if (value != null && value != "null") {
                value.toDoubleOrNull()?.let { num ->
                    "%.${data.chartRounding}f".format(num)
                } ?: value
            } else "—"

            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.fieldNames[fieldNum] ?: "Field $fieldNum",
                    style = TextStyle(color = ColorProvider(textColor), fontSize = fieldSize.sp),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.minSetFields.contains(fieldNum)) {
                        Text(
                            text = "▼",
                            style = TextStyle(color = ColorProvider(if (data.violatedMinFields.contains(fieldNum)) Color.Red else Color.Gray), fontSize = (fieldSize - 2).sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(end = 2.dp)
                        )
                    }
                    if (data.maxSetFields.contains(fieldNum)) {
                        Text(
                            text = "▲",
                            style = TextStyle(color = ColorProvider(if (data.violatedMaxFields.contains(fieldNum)) Color.Red else Color.Gray), fontSize = (fieldSize - 2).sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = if (unit.isNotBlank()) "$roundedValue $unit" else roundedValue,
                        style = TextStyle(
                            color = ColorProvider(if (data.violatedMinFields.contains(fieldNum) || data.violatedMaxFields.contains(fieldNum)) Color.Red else textColor),
                            fontSize = fieldSize.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Chart Section
        if (!isTiny) {
            Spacer(GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(if (isDarkBg) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                    .cornerRadius(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (data.chartBitmap != null) {
                    Image(
                        provider = ImageProvider(data.chartBitmap),
                        contentDescription = "Live Chart",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = if (data.isRefreshing) "Loading Chart..." else "No chart data",
                        style = TextStyle(color = ColorProvider(textColor.copy(alpha = 0.4f)), fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

class GlanceRefreshAction : RefreshWidgetAction({ ThingSpeakGlanceWidget() }, "widget_refresh")
class GlanceEditAction : EditWidgetAction(WidgetConfigActivity::class.java)
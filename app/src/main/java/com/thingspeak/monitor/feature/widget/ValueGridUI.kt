package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.TextStyle

@Composable
fun ValueGridContent(context: Context, data: WidgetData) {
    ValueGridContentImpl(context, data)
}

@Composable
private fun ValueGridContentImpl(context: Context, data: WidgetData) {
    val size = LocalSize.current
    val isSmallHeight = size.height < WidgetPrefsKeys.HEIGHT_SMALL_GRID.dp
    
    val isStale = data.entry?.let { 
        WidgetUtils.isDataStale(it.createdAt, data.syncIntervalMinutes * 60 * 1000L) 
    } ?: true

    // Dark mode auto-detection
    val effectiveBgHex = darkModeAutoBgColor(data, context)
    val isDarkMode = isSystemDarkMode(context)
    val baseColor = resolveSystemAwareBackground(
        prefHex = effectiveBgHex,
        isDarkMode = isDarkMode,
        context = context,
        colorMode = data.bgColorMode
    )

    val isDarkBg = isColorDark(baseColor)

    val contentColorVal = darkModeAutoTextColor(data, isDarkBg)
    
    val secondaryContentColorVal = contentColorVal.copy(alpha = 0.7f)

    val bgColor = if (data.isGlass) {
        ColorProvider(Color.White.copy(alpha = 0.12f))
    } else {
        ColorProvider(Color(baseColor).copy(alpha = data.transparency))
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(if (isSmallHeight) 4.dp else 8.dp)
            .background(bgColor)
            .cornerRadius(16.dp),
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (isSmallHeight) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.channelName ?: "ThingSpeak",
                        style = TextStyle(
                            color = ColorProvider(contentColorVal),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isSmallHeight) 11.sp else 14.sp,
                        ),
                        maxLines = 1
                    )
                    if (data.lastSyncStatus == WidgetPrefsKeys.STATUS_ERROR_SYNC) {
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "⚠",
                            style = TextStyle(
                                color = ColorProvider(Color.Red),
                                fontSize = (if (isSmallHeight) (data.fontSize - 1).coerceAtLeast(10) else data.fontSize).sp
                            )
                        )
                    }
                }
                if (data.entry != null) {
                    val timeStr = WidgetUtils.formatTime(data.entry.createdAt, data.channelTimezone)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Measured: $timeStr",
                            style = TextStyle(
                                color = ColorProvider(contentColorVal.copy(alpha = 0.6f)),
                                fontSize = (if (isSmallHeight) 8 else 9).sp
                            )
                        )
                        if (isStale) {
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "⌛", // Hourglass for stale data
                                style = TextStyle(fontSize = 9.sp)
                            )
                        }
                    }
                } else if (data.channelName != WidgetPrefsKeys.LOADING_PLACEHOLDER && data.channelId != -1L) {
                    Text(
                        text = "Waiting for data...",
                        style = TextStyle(
                            color = ColorProvider(contentColorVal.copy(alpha = 0.35f)),
                            fontSize = (if (isSmallHeight) 8 else 9).sp
                        )
                    )
                }
            }
            
            if (data.isRefreshing) {
                Text(
                    text = "•••",
                    style = TextStyle(color = ColorProvider(contentColorVal), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
            }
            
            // Buttons
            val buttonBg = if (isDarkBg) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Refresh
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 2.dp)
                        .background(buttonBg)
                        .cornerRadius(8.dp)
                        .clickable(actionRunCallback<GridRefreshAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "REF",
                        style = TextStyle(color = ColorProvider(contentColorVal), fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        modifier = GlanceModifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
                
                Spacer(GlanceModifier.width(4.dp))
                
                // Edit
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 2.dp)
                        .background(buttonBg)
                        .cornerRadius(8.dp)
                        .clickable(actionRunCallback<GridEditAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✎",
                        style = TextStyle(color = ColorProvider(contentColorVal), fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(12.dp))

        if (data.entry != null) {
            val visibleFields = data.visibleFields
            val fieldsToRender = if (visibleFields != null) {
                visibleFields.toList().sorted()
            } else {
                // Default to first 4 non-empty fields if no visible fields configured (null)
                (1..8).filter { i -> 
                    data.fieldNames[i]?.isNotBlank() == true || 
                    (i == 1 && data.entry?.field1 != null) // Always show field 1 if possible
                }.take(4)
            }

            if (fieldsToRender.isNotEmpty()) {
                val columnCount = if (fieldsToRender.size == 1) 1 else 2
                val rowCount = (fieldsToRender.size + columnCount - 1) / columnCount
                
                for (row in 0 until rowCount) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in 0 until columnCount) {
                            val index = row * columnCount + col
                            if (index < fieldsToRender.size) {
                                val fieldId = fieldsToRender[index]
                                ValueTile(
                                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                    index = fieldId,
                                    data = data,
                                    contentColor = if (isStale) contentColorVal.copy(alpha = 0.5f) else contentColorVal,
                                    secondaryColor = if (isStale) secondaryContentColorVal.copy(alpha = 0.4f) else secondaryContentColorVal,
                                    baseColor = baseColor,
                                    tileCount = fieldsToRender.size,
                                    isSmallHeight = isSmallHeight
                                )
                            } else {
                                Spacer(modifier = GlanceModifier.defaultWeight())
                            }
                            
                            // Add horizontal spacer between columns
                            if (col < columnCount - 1) {
                                Spacer(modifier = GlanceModifier.width(8.dp))
                            }
                        }
                    }
                    // Add vertical spacer between rows
                    if (row < rowCount - 1) {
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
            } else {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No fields configured",
                        style = TextStyle(color = ColorProvider(contentColorVal.copy(alpha = 0.5f)), fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun ValueTile(
    modifier: GlanceModifier,
    index: Int, 
    data: WidgetData, 
    contentColor: Color, 
    secondaryColor: Color, 
    baseColor: Int,
    tileCount: Int,
    isSmallHeight: Boolean
) {
    val name = data.fieldNames[index]?.takeIf { it.isNotBlank() } ?: "Field $index"
    val unit = data.fieldUnits[index] ?: ""
    val rawValue = data.entry?.getField(index)

    val tileValue = if (rawValue != null && rawValue != "null") {
        rawValue.toDoubleOrNull()?.let { num ->
            "%.${data.chartRounding}f".format(num)
        } ?: rawValue
    } else "—"

    val isViolated = data.violatedMinFields.contains(index) || data.violatedMaxFields.contains(index)
    val tileValueColor = if (isViolated) Color.Red else contentColor
    val valueFontSize = when(tileCount) {
        1 -> (data.fontSize + (if (isSmallHeight) 8 else 16)).sp
        2 -> (data.fontSize + (if (isSmallHeight) 4 else 8)).sp
        else -> (data.fontSize + (if (isSmallHeight) 1 else 4)).sp
    }
    
    val nameFontSize = when(tileCount) {
        1 -> (data.fontSize + (if (isSmallHeight) 0 else 2)).sp
        else -> 10.sp
    }

    val tileBgColor = if (data.isGlass) {
        Color.White.copy(alpha = 0.25f)
    } else {
        if (isColorDark(baseColor)) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier.background(tileBgColor).cornerRadius(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Alarm Indicators Layer
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            if (data.maxSetFields.contains(index)) {
                val isMaxViolated = data.violatedMaxFields.contains(index)
                Text(
                    text = "▲",
                    modifier = GlanceModifier.padding(top = 2.dp, end = 4.dp),
                    style = androidx.glance.text.TextStyle(
                        color = ColorProvider(if (isMaxViolated) Color.Red else Color.Gray),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            if (data.minSetFields.contains(index)) {
                val isMinViolated = data.violatedMinFields.contains(index)
                Text(
                    text = "▼",
                    modifier = GlanceModifier.padding(bottom = 2.dp, end = 4.dp),
                    style = androidx.glance.text.TextStyle(
                        color = ColorProvider(if (isMinViolated) Color.Red else Color.Gray),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Main Content Layer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tileValue,
                    style = androidx.glance.text.TextStyle(
                        color = ColorProvider(tileValueColor),
                        fontSize = valueFontSize, 
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                // Keep the bell icon if ANY violation exists as a general alert signal
                if (isViolated) {
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        text = "!",
                        style = androidx.glance.text.TextStyle(
                            color = ColorProvider(Color.Red),
                            fontSize = (valueFontSize.value * 0.7f).toInt().sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                if (unit.isNotBlank()) {
                    Text(
                        text = " $unit",
                        style = androidx.glance.text.TextStyle(
                            color = ColorProvider(contentColor.copy(alpha = 0.5f)),
                            fontSize = (data.fontSize - 1).sp
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(if (tileCount == 1) 4.dp else 2.dp))
            Text(
                text = name,
                style = androidx.glance.text.TextStyle(
                    color = ColorProvider(secondaryColor),
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.Medium 
                ),
                maxLines = 1
            )
        }
    }
}

class GridRefreshAction : RefreshWidgetAction({ ValueGridWidget() }, "widget_grid_refresh_sync")
class GridEditAction : EditWidgetAction(ValueGridWidgetConfigActivity::class.java)

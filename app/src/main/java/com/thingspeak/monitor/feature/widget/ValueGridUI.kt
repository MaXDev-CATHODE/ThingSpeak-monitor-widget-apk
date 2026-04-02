package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.ContentScale
import androidx.glance.text.TextStyle

@Composable
fun ValueGridContent(context: Context, data: WidgetData) {
    val size = LocalSize.current
    val isSmallHeight = size.height < 120.dp
    
    val isStale = data.entry?.let { 
        WidgetUtils.isDataStale(it.createdAt, data.syncIntervalMinutes * 60 * 1000L) 
    } ?: true

    val baseColor = data.bgColorHex?.let { 
        try { android.graphics.Color.parseColor(it) } catch (e: Exception) { android.graphics.Color.WHITE }
    } ?: android.graphics.Color.WHITE

    val isDarkBg = isColorDark(baseColor)
    
    val contentColorVal = data.textColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
    } ?: (if (isDarkBg) Color.White else Color.Black)
    
    val secondaryContentColorVal = contentColorVal.copy(alpha = 0.7f)

    val bgColor = if (data.isGlass) {
        ColorProvider(Color.White.copy(alpha = 0.12f))
    } else {
        ColorProvider(Color(baseColor))
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
                    if (data.lastSyncStatus == "ERROR_SYNC") {
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "⚠️",
                            style = TextStyle(fontSize = (if (isSmallHeight) 11 else 14).sp)
                        )
                    }
                }
                if (data.entry != null) {
                    val timeStr = WidgetUtils.formatTime(data.entry.createdAt, data.channelTimezone)
                    Text(
                        text = "Measured: $timeStr",
                        style = TextStyle(
                            color = ColorProvider(contentColorVal.copy(alpha = 0.6f)),
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
                                    contentColor = contentColorVal,
                                    secondaryColor = secondaryContentColorVal,
                                    baseColor = baseColor,
                                    tileCount = fieldsToRender.size
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
    tileCount: Int
) {
    val name = data.fieldNames[index]?.takeIf { it.isNotBlank() } ?: "Field $index"
    val unit = data.fieldUnits[index] ?: ""
    val rawValue = when(index) {
        1 -> data.entry?.field1
        2 -> data.entry?.field2
        3 -> data.entry?.field3
        4 -> data.entry?.field4
        5 -> data.entry?.field5
        6 -> data.entry?.field6
        7 -> data.entry?.field7
        8 -> data.entry?.field8
        else -> null
    }

    val tileValue = try {
        val num = rawValue?.toDoubleOrNull() ?: 0.0
        "%.${data.chartRounding}f".format(num)
    } catch (e: Exception) { rawValue ?: "—" }

    val isViolated = data.violatedMinFields.contains(index) || data.violatedMaxFields.contains(index)
    val tileValueColor = if (isViolated) Color.Red else contentColor
    val valueFontSize = when(tileCount) {
        1 -> (data.fontSize + 16).sp
        2 -> (data.fontSize + 8).sp
        else -> (data.fontSize + 4).sp
    }
    
    val nameFontSize = when(tileCount) {
        1 -> (data.fontSize + 2).sp
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
                        text = "🔔",
                        style = androidx.glance.text.TextStyle(
                            fontSize = (valueFontSize.value * 0.7f).toInt().sp
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

private fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 
                       0.587 * android.graphics.Color.green(color) + 
                       0.114 * android.graphics.Color.blue(color)) / 255
    return darkness >= 0.5
}

class GridRefreshAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java)
        val bindingRepo = entryPoint.widgetBindingRepository()
        val channelId = bindingRepo.getBindingSync(appWidgetId)
        
        if (channelId != -1L) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.thingspeak.monitor.core.worker.DataSyncWorker>().build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "widget_grid_refresh_sync_$appWidgetId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}

class GridEditAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = android.content.Intent(context, ValueGridWidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

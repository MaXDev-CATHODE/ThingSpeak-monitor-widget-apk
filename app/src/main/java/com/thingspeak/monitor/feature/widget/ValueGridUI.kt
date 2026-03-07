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
    val contentColorVal = if (isDarkBg) Color.White else Color.Black
    val secondaryContentColorVal = if (isDarkBg) Color.LightGray else Color.DarkGray

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
        // Nagłówek - bardziej kompaktowy w małych widżetach
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (isSmallHeight) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isSmallHeight || size.width > 150.dp) {
                Text(
                    text = data.channelName,
                    style = TextStyle(
                        color = ColorProvider(contentColorVal),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallHeight) 11.sp else 14.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
            
            if (data.isRefreshing) {
                Text(
                    text = "•••",
                    style = TextStyle(color = ColorProvider(contentColorVal), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
            }
            
            // Buttons with premium look
            val buttonBg = if (isDarkBg) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        style = TextStyle(
                            color = ColorProvider(contentColorVal),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 2.dp)
                        .background(buttonBg)
                        .cornerRadius(8.dp)
                        .clickable(actionRunCallback<GridEditAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CFG",
                        style = TextStyle(
                            color = ColorProvider(contentColorVal),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        modifier = GlanceModifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (data.entry != null) {
            val visibleList = data.visibleFields?.toList()?.sorted() ?: (1..8).filter { idx ->
                val fieldVal = when(idx) {
                    1 -> data.entry.field1
                    2 -> data.entry.field2
                    3 -> data.entry.field3
                    4 -> data.entry.field4
                    5 -> data.entry.field5
                    6 -> data.entry.field6
                    7 -> data.entry.field7
                    8 -> data.entry.field8
                    else -> null
                }
                fieldVal != null
            }
            val fieldsToRender = visibleList.take(4)
            
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val totalFields = fieldsToRender.size
                // Uniform gap = 4dp between tiles in both axes
                val halfGap = 8.dp
                
                when (totalFields) {
                    1 -> {
                        ValueTile(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(4.dp),
                            index = fieldsToRender[0], 
                            data = data, 
                            contentColor = contentColorVal, 
                            secondaryColor = secondaryContentColorVal, 
                            baseColor = baseColor,
                            tileCount = 1
                        )
                    }
                    2 -> {
                        Row(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[0], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 2
                            )
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[1], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 2
                            )
                        }
                    }
                    3 -> {
                        // Top row: 2 tiles
                        Row(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[0], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 3
                            )
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[1], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 3
                            )
                        }
                        
                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Bottom row: 1 full-width tile
                        Row(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[2], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 3
                            )
                        }
                    }
                    else -> {
                        // 4 tiles: 2x2 grid with uniform 4dp gaps
                        // Top row
                        Row(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[0], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 4
                            )
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[1], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 4
                            )
                        }
                        
                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Bottom row
                        Row(
                            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[2], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 4
                            )
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                index = fieldsToRender[3], 
                                data = data, 
                                contentColor = contentColorVal, 
                                secondaryColor = secondaryContentColorVal, 
                                baseColor = baseColor,
                                tileCount = 4
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No data",
                style = androidx.glance.text.TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
            )
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
    val tileValue = when(index) {
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

    val hasAlert = data.activeAlertFields.contains(index)
    val tileValueColor = if (hasAlert) Color.Red else contentColor
    val valueFontSize = when(tileCount) {
        1 -> (data.fontSize + 18).sp
        2 -> (data.fontSize + 10).sp
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = tileValue ?: "—",
                style = androidx.glance.text.TextStyle(
                    color = ColorProvider(tileValueColor),
                    fontSize = valueFontSize, 
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
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

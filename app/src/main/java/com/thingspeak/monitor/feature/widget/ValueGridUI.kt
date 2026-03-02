package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.thingspeak.monitor.MainActivity
import com.thingspeak.monitor.R

@Composable
fun ValueGridContent(context: Context, data: WidgetData) {
    val isStale = data.entry?.let { 
        WidgetUtils.isDataStale(it.createdAt, data.syncIntervalMinutes * 60 * 1000L) 
    } ?: true

    val baseColor = data.bgColorHex?.let { 
        try { android.graphics.Color.parseColor(it) } catch (e: Exception) { android.graphics.Color.WHITE }
    } ?: android.graphics.Color.WHITE

    val isDarkBg = isColorDark(baseColor)
    
    // Improved color logic: don't force Black for Glass if the intention was Dark theme
    val contentColor = if (isDarkBg) Color.White else Color.Black
    val secondaryContentColor = if (isDarkBg) Color.LightGray else Color.DarkGray

    android.util.Log.d("ValueGridUI", "Rendering ${data.channelName} (ID: ${data.channelId}) " + 
        "| Glass: ${data.isGlass} | isDarkBg: $isDarkBg | contentColor: $contentColor")
    
    // Background handling (Glassmorphism or solid)
    val bgColor = if (data.isGlass) {
        androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f))
    } else {
        androidx.glance.unit.ColorProvider(
            androidx.compose.ui.graphics.Color(baseColor).copy(alpha = data.transparency)
        )
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(bgColor),
    ) {
        // TOP HEADER
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.channelName,
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(contentColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            
            if (data.isRefreshing) {
                Text(
                    text = "...",
                    style = TextStyle(color = androidx.glance.unit.ColorProvider(contentColor), fontSize = 14.sp),
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
            }
            if (isStale) {
                Box(modifier = GlanceModifier.width(8.dp).height(8.dp).background(Color.Red)) {}
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            
            Text(
                text = "↻",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(horizontal = 4.dp).clickable(actionRunCallback<GridRefreshAction>())
            )
            Text(
                text = "⚙",
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(horizontal = 4.dp).clickable(actionRunCallback<GridEditAction>())
            )
        }
        
        Spacer(modifier = GlanceModifier.height(8.dp))

        // GRID CONTENT
        if (data.entry != null) {
            // If no visible fields defined, auto-scan all 8 for data, but limit to 4 for grid
            val visibleList = data.visibleFields?.toList()?.sorted() ?: (1..8).filter { index ->
                val value = when(index) {
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
                value != null
            }
            val fieldsToRender = visibleList.take(4) // Max 4 fields for grid layout
            
            android.util.Log.d("ValueGridUI", "Grid content start. Fields to render: $fieldsToRender")
            
            // Use defaultWeight() to take remaining space after header, avoiding fillMaxSize overlap
            Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                if (fieldsToRender.size <= 2) {
                    android.util.Log.d("ValueGridUI", "Creating Row 2x1")
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (index in fieldsToRender) {
                            android.util.Log.d("ValueGridUI", "Rendering tile in 2x1 row: index $index")
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp),
                                index = index, 
                                data = data, 
                                contentColor = contentColor, 
                                secondaryColor = secondaryContentColor, 
                                baseColor = baseColor
                            )
                        }
                        // If only 1 field, add spacer to keep it to the left
                        if (fieldsToRender.size == 1) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                } else {
                    val firstRowFields = fieldsToRender.take(2)
                    val secondRowFields = fieldsToRender.drop(2)
                    
                    android.util.Log.d("ValueGridUI", "Creating 2x2: Row1=$firstRowFields, Row2=$secondRowFields")
                    
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (index in firstRowFields) {
                            android.util.Log.d("ValueGridUI", "Rendering tile in 2x2 row 1: index $index")
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp),
                                index = index, 
                                data = data, 
                                contentColor = contentColor, 
                                secondaryColor = secondaryContentColor, 
                                baseColor = baseColor
                            )
                        }
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (index in secondRowFields) {
                            android.util.Log.d("ValueGridUI", "Rendering tile in 2x2 row 2: index $index")
                            ValueTile(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp),
                                index = index, 
                                data = data, 
                                contentColor = contentColor, 
                                secondaryColor = secondaryContentColor, 
                                baseColor = baseColor
                            )
                        }
                        // Handle odd number of fields in the second row (if user chose 3 fields)
                        if (secondRowFields.size == 1) {
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        } else {
            Text(
                text = context.getString(com.thingspeak.monitor.R.string.chart_empty_data),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
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
    baseColor: Int
) {
    val name = data.fieldNames[index]?.takeIf { it.isNotBlank() } ?: "Field $index"
    val value = when(index) {
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

    // Distinct background for the tile to make it look like a "window"
    val tileBgColor = if (data.isGlass) {
        // High visibility glass for tiles
        Color.White.copy(alpha = 0.4f)
    } else {
        // Very distinct overlay for tiles on solid backgrounds
        if (isColorDark(baseColor)) {
            Color.White.copy(alpha = 0.2f)
        } else {
            Color.Black.copy(alpha = 0.15f)
        }
    }

    Box(
        modifier = modifier
            .background(tileBgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value ?: "—",
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(tileValueColor),
                    fontSize = (data.fontSize + 16).sp, 
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = name,
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(secondaryColor),
                    fontSize = (data.fontSize).sp,
                    fontWeight = FontWeight.Bold 
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

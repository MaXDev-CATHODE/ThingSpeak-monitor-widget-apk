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
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import androidx.glance.Button
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider as GColor

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
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = "NONE",
    val visibleFields: Set<Int>? = null,
    val chartType: String? = "line",
    val chartBitmap: android.graphics.Bitmap? = null,
    val isRefreshing: Boolean = false,
    val channelTimezone: String? = null
)

@Composable
fun WidgetUI(data: WidgetData) {
    val context = LocalContext.current
    val size = LocalSize.current
    
    // Dynamic sizing based on widget dimensions
    val isCompact = size.height < 140.dp || size.width < 200.dp
    val isTiny = size.height < 100.dp || size.width < 150.dp
    
    // Scale font sizes dynamically - respect user data.fontSize
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
    
    val bgColor = try {
        val hex = data.bgColorHex?.removePrefix("#") ?: "FFFFFF"
        val alpha = (data.transparency * 255).toInt().toString(16).padStart(2, '0')
        Color(android.graphics.Color.parseColor("#$alpha$hex"))
    } catch (e: Exception) {
        Color.White.copy(alpha = data.transparency)
    }

    val isDarkBg = isColorDark(android.graphics.Color.parseColor(data.bgColorHex ?: "#FFFFFF"))
    val textColor = try {
        val tc = data.textColor
        if (tc != null && tc.startsWith("#")) {
            Color(android.graphics.Color.parseColor(tc))
        } else {
            // Auto-contrast
            if (isDarkBg) Color.White else Color.Black
        }
    } catch (e: Exception) {
        if (isDarkBg) Color.White else Color.Black
    }
    
    val buttonBg = if (isDarkBg) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceColorProvider(bgColor))
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
                            color = GlanceColorProvider(textColor),
                            fontSize = titleSize.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    if (data.lastSyncStatus == "ERROR_SYNC") {
                        Spacer(GlanceModifier.width(2.dp))
                        Text(
                            text = "⚠",
                            style = TextStyle(
                                color = GlanceColorProvider(Color.Red),
                                fontSize = titleSize.sp
                            )
                        )
                    }
                }
                if (!isTiny && data.entry != null) {
                    val timeStr = WidgetUtils.formatTime(data.entry.createdAt, data.channelTimezone)
                    val isStale = WidgetUtils.isDataStale(data.entry.createdAt, data.syncIntervalMinutes * 60 * 1000L)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Measured: $timeStr",
                            style = TextStyle(
                                color = GlanceColorProvider(textColor.copy(alpha = 0.6f)),
                                fontSize = (subSize - 1).sp
                            )
                        )
                        if (isStale) {
                            Spacer(GlanceModifier.width(3.dp))
                            Text(
                                text = "⌛",
                                style = TextStyle(color = GlanceColorProvider(textColor.copy(alpha = 0.6f)), fontSize = (subSize - 1).sp)
                            )
                        }
                    }
                }
            }
            
            // Buttons: EDIT + REF
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .background(buttonBg)
                        .cornerRadius(6.dp)
                        .clickable(actionRunCallback<EditActionV2>())
                ) {
                    Text(
                        text = "✎",
                        style = TextStyle(
                            color = GlanceColorProvider(textColor),
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
                        .clickable(actionRunCallback<RefreshActionV2>())
                ) {
                    Text(
                        text = if (data.isRefreshing) "●" else "↻",
                        style = TextStyle(
                            color = GlanceColorProvider(textColor),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(horizontal = 5.dp, vertical = 3.dp)
                    )
                }
            }
        }
        
        Spacer(GlanceModifier.height(if (isCompact) 2.dp else 4.dp))
        
        // Fields — filter by visible fields config AND by having a name
        val fieldsToShow = (1..8).filter { fieldNum ->
            val name = data.fieldNames[fieldNum]
            val isVisible = data.visibleFields?.contains(fieldNum) ?: true
            !name.isNullOrBlank() && isVisible
        }
        
        // Limit fields if widget is compact to leave room for chart
        val maxFields = when {
            isTiny -> 2
            isCompact -> 4
            else -> 8
        }
        
        fieldsToShow.take(maxFields).forEach { fieldNum ->
            val value = when(fieldNum) {
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
                    style = TextStyle(color = GlanceColorProvider(textColor), fontSize = fieldSize.sp),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (data.minSetFields.contains(fieldNum)) {
                        Text(
                            text = "▼",
                            style = TextStyle(color = GlanceColorProvider(if (data.violatedMinFields.contains(fieldNum)) Color.Red else Color.Gray), fontSize = (fieldSize - 2).sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(end = 2.dp)
                        )
                    }
                    if (data.maxSetFields.contains(fieldNum)) {
                        Text(
                            text = "▲",
                            style = TextStyle(color = GlanceColorProvider(if (data.violatedMaxFields.contains(fieldNum)) Color.Red else Color.Gray), fontSize = (fieldSize - 2).sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = if (unit.isNotBlank()) "$roundedValue $unit" else roundedValue,
                        style = TextStyle(
                            color = GlanceColorProvider(if (data.violatedMinFields.contains(fieldNum) || data.violatedMaxFields.contains(fieldNum)) Color.Red else textColor),
                            fontSize = fieldSize.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Chart Section — fills remaining space
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
                        style = TextStyle(color = GlanceColorProvider(textColor.copy(alpha = 0.4f)), fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

private fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 
                       0.587 * android.graphics.Color.green(color) + 
                       0.114 * android.graphics.Color.blue(color)) / 255
    return darkness >= 0.5
}

class RefreshActionV2 : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: android.content.Context,
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
                    "glance_refresh_sync_$appWidgetId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}

class EditActionV2 : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: android.content.Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val intent = android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())


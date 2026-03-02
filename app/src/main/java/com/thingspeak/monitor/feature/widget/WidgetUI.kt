package com.thingspeak.monitor.feature.widget

import android.graphics.Bitmap
import android.content.Context
import androidx.compose.runtime.Composable
import com.thingspeak.monitor.MainActivity
import com.thingspeak.monitor.R
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import com.thingspeak.monitor.feature.widget.WidgetConfigActivity

/**
 * Snapshot of data for widget rendering.
 */
data class WidgetData(
    val channelName: String,
    val channelId: Long,
    val entry: FeedEntryEntity?,
    val fieldNames: Map<Int, String> = emptyMap(),
    val bgColorHex: String? = null,
    val transparency: Float = 1.0f,
    val fontSize: Int = 12,
    val isGlass: Boolean = false,
    val activeAlertFields: Set<Int> = emptySet(),
    val syncIntervalMinutes: Long = 30,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = "SUCCESS",
    val visibleFields: Set<Int>? = null,
    val chartBitmap: android.graphics.Bitmap? = null,
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false
)

@Composable
fun NoDataContent(context: Context, channelName: String = "") {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<com.thingspeak.monitor.MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (channelName.isNotBlank()) {
                Text(
                    text = channelName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
            Text(
                text = context.getString(com.thingspeak.monitor.R.string.dashboard_empty_title),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(com.thingspeak.monitor.R.string.widget_no_data),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun WidgetLoadingUI(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.glance.layout.Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = context.getString(R.string.widget_loading),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun WidgetErrorUI(context: Context, message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .background(GlanceTheme.colors.errorContainer)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_error_title),
                style = TextStyle(
                    color = GlanceTheme.colors.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Text(
                text = message,
                style = TextStyle(
                    color = GlanceTheme.colors.onErrorContainer,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun WidgetContent(context: Context, data: WidgetData) {
    val isStale = data.entry?.let { 
        WidgetUtils.isDataStale(it.createdAt, data.syncIntervalMinutes * 60 * 1000L) 
    } ?: true

    val baseColor = data.bgColorHex?.let { 
        try { android.graphics.Color.parseColor(it) } catch (e: Exception) { android.graphics.Color.WHITE }
    } ?: android.graphics.Color.WHITE

    val isDarkBg = isColorDark(baseColor)
    val contentColor = if (isDarkBg) Color.White else Color.Black
    val secondaryContentColor = if (isDarkBg) Color.LightGray else Color.DarkGray

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
            .padding(12.dp)
            .background(bgColor),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.channelName,
                style = TextStyle(
                    color = androidx.glance.unit.ColorProvider(contentColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = (data.fontSize + 2).sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )

            if (data.isRefreshing) {
                Text(
                    text = "...",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(contentColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
            }
            
            if (data.lastSyncStatus.startsWith("ERROR")) {
                Text(
                    text = "!",
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.Red),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier.padding(horizontal = 8.dp)
                )
            }

            Text(
                text = context.getString(R.string.widget_action_refresh),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .clickable(actionRunCallback<RefreshAction>()),
            )

            Spacer(modifier = GlanceModifier.width(4.dp))

            Text(
                text = context.getString(R.string.widget_action_edit),
                style = TextStyle(
                    color = GlanceTheme.colors.secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(androidx.glance.appwidget.action.actionRunCallback<EditAction>()),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (data.entry != null) {
            // Flattened list to avoid deep nesting
            Column {
                (1..8).forEach { index ->
                    if (data.visibleFields == null || data.visibleFields.contains(index)) {
                        val name = data.fieldNames[index]?.takeIf { it.isNotBlank() } ?: "Field $index"
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
                            if (value != null) {
                                FieldRow(
                                    label = name, 
                                    value = value, 
                                    hasAlert = data.activeAlertFields.contains(index), 
                                    fontSize = data.fontSize,
                                    textColor = contentColor,
                                    labelColor = secondaryContentColor
                                )
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

        if (data.entry != null && data.chartBitmap != null) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(data.chartBitmap),
                contentDescription = context.getString(R.string.widget_content_chart),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentScale = androidx.glance.layout.ContentScale.FillBounds
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        val timestampText = data.entry?.let { WidgetUtils.formatRelativeTime(it.createdAt) } ?: "—"
        val localSyncText = if (data.lastSyncTime > 0) WidgetUtils.formatTime(data.lastSyncTime) else "—"

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(
                        com.thingspeak.monitor.R.string.widget_last_updated,
                        timestampText,
                    ),
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(secondaryContentColor),
                        fontSize = 11.sp,
                    ),
                )
                Text(
                    text = context.getString(R.string.widget_sync_prefix, localSyncText),
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(secondaryContentColor),
                        fontSize = 10.sp,
                    ),
                )
            }
            if (isStale) {
                Text(
                    text = context.getString(R.string.widget_status_stale),
                    style = TextStyle(
                        color = GlanceTheme.colors.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                )
            }
        }
    }
}

@Composable
fun FieldRow(
    label: String, 
    value: String?, 
    hasAlert: Boolean, 
    fontSize: Int,
    textColor: Color,
    labelColor: Color
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(labelColor),
                fontSize = fontSize.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = value ?: "—",
            style = TextStyle(
                color = if (hasAlert) androidx.glance.unit.ColorProvider(Color.Red) 
                        else androidx.glance.unit.ColorProvider(textColor),
                fontWeight = FontWeight.Medium,
                fontSize = (fontSize + 1).sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (hasAlert) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Box(modifier = GlanceModifier.width(14.dp).height(14.dp)) {
                androidx.glance.Image(
                    provider = androidx.glance.ImageProvider(com.thingspeak.monitor.R.drawable.ic_notification_bell),
                    contentDescription = "Alert active",
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = androidx.glance.ColorFilter.tint(androidx.glance.unit.ColorProvider(Color.Red))
                )
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

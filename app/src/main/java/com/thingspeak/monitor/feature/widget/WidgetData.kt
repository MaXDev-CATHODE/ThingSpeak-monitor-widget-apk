package com.thingspeak.monitor.feature.widget

import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity

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
    val chartBitmap: android.graphics.Bitmap? = null,
    val isRefreshing: Boolean = false,
    val channelTimezone: String? = null,
    val bgColorMode: String? = null
)
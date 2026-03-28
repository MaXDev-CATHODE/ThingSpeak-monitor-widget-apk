package com.thingspeak.monitor.core.datastore

import kotlinx.serialization.Serializable

/**
 * A channel saved by the user in Datastore.
 * Top-level class to ensure visibility in all components.
 */
@Serializable
data class SavedChannel(
    val id: Long,
    val name: String,
    val apiKey: String? = null,
    val fieldNames: Map<Int, String> = emptyMap(),
    val fieldUnits: Map<Int, String> = emptyMap(),
    val widgetBgColorHex: String? = "#FFFFFF",
    val widgetTextColorHex: String? = null,
    val widgetTransparency: Float = 1.0f,
    val widgetFontSize: Int = 12,
    val widgetVisibleFields: Set<Int>? = null,
    val isGlassmorphismEnabled: Boolean = false,
    val chartField: Int = 1,
    val chartType: String = "line",
    val chartResults: Int = 60,
    val chartColor: String? = "#2196F3",
    val chartBgColor: String? = "#FFFFFF",
    val chartProcessingPeriod: Int = 0,
    val chartTimespan: String = "1D", // New: "1D", "7D", "30D"
    val fieldColors: Map<Int, String> = emptyMap(),
    val fieldYMin: Map<Int, Double> = emptyMap(),
    val fieldYMax: Map<Int, Double> = emptyMap(),
    val textColor: String? = "#000000",
    val displayNameMode: String = "default",
    val displayFieldMode: String = "default",
    val chartRounding: Int = 2,
    val chartProcessingType: String = "NONE",
    val preferredChartFields: Set<Int>? = null,
    val lastProcessedEntryId: Long = 0L,
    val lastSyncStatus: String = "NONE",
    val lastSyncTime: Long = 0L,
    val isNormalized: Boolean = false,
    val isMergingEnabled: Boolean = false,
    val drawingStyle: String = "CUBIC"
)

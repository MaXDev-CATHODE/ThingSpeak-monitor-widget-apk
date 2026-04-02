package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Domain model for a ThingSpeak channel.
 *
 * Stores channel configuration along with field names (field1-8).
 * Independent of the data layer — pure Kotlin.
 */
data class Channel(
    val id: Long,
    val name: String,
    val description: String = "",
    val apiKey: String? = null,
    val fieldNames: Map<Int, String> = emptyMap(),
    val fieldUnits: Map<Int, String> = emptyMap(),
    val lastSyncStatus: SyncStatus = SyncStatus.NONE,
    val widgetBgColorHex: String? = null,
    val widgetTextColorHex: String? = null,
    val widgetTransparency: Float = 1.0f,
    val widgetFontSize: Int = 12,
    val isGlassmorphismEnabled: Boolean = false,
    val chartRounding: Int = 2,
    val chartProcessingType: String = "NONE",
    val chartProcessingPeriod: Int = 0,
    val chartField: Int = 1,
    val chartType: String = "line",
    val chartResults: Int = 60,
    val chartColor: String = "#2196F3",
    val chartBgColor: String = "#FFFFFF",
    val fieldColors: Map<Int, String> = emptyMap(),
    val fieldYMin: Map<Int, Double> = emptyMap(),
    val fieldYMax: Map<Int, Double> = emptyMap(),
    val textColor: String = "#000000",
    val preferredChartFields: Set<Int>? = null,
    val lastSyncTime: Long = 0L,
    val widgetVisibleFields: Set<Int>? = null,
    val displayNameMode: String = "default",
    val displayFieldMode: String = "default",
    val lastProcessedEntryId: Long = 0L,
    val chartTimespan: String = "1D",
    val isNormalized: Boolean = false,
    val isMergingEnabled: Boolean = false,
    val drawingStyle: String = "CUBIC",
    val timezone: String? = null
)

fun Channel.toSavedChannel(): com.thingspeak.monitor.core.datastore.SavedChannel = 
    com.thingspeak.monitor.core.datastore.SavedChannel(
        id = id,
        name = name,
        apiKey = apiKey,
        fieldNames = fieldNames,
        fieldUnits = fieldUnits,
        widgetBgColorHex = widgetBgColorHex,
        widgetTextColorHex = widgetTextColorHex,
        widgetTransparency = widgetTransparency,
        widgetFontSize = widgetFontSize,
        widgetVisibleFields = widgetVisibleFields,
        isGlassmorphismEnabled = isGlassmorphismEnabled,
        chartField = chartField,
        chartType = chartType,
        chartResults = chartResults,
        chartColor = chartColor,
        chartBgColor = chartBgColor,
        chartProcessingPeriod = chartProcessingPeriod,
        chartTimespan = chartTimespan,
        fieldColors = fieldColors,
        fieldYMin = fieldYMin,
        fieldYMax = fieldYMax,
        textColor = textColor,
        displayNameMode = displayNameMode,
        displayFieldMode = displayFieldMode,
        chartRounding = chartRounding,
        chartProcessingType = chartProcessingType,
        preferredChartFields = preferredChartFields,
        lastProcessedEntryId = lastProcessedEntryId,
        lastSyncStatus = lastSyncStatus.name,
        lastSyncTime = lastSyncTime,
        isNormalized = isNormalized,
        isMergingEnabled = isMergingEnabled,
        drawingStyle = drawingStyle,
        timezone = timezone
    )

enum class SyncStatus {
    NONE, SUCCESS, ERROR_API, ERROR_AUTH, ERROR_NETWORK
}

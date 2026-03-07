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
    val lastSyncStatus: SyncStatus = SyncStatus.NONE,
    val widgetBgColorHex: String? = null,
    val widgetTransparency: Float = 1.0f,
    val widgetFontSize: Int = 12,
    val isGlassmorphismEnabled: Boolean = false,
    val chartRounding: Int = 2,
    val chartProcessingType: String = "NONE",
    val chartProcessingPeriod: Int = 0,
    val preferredChartFields: Set<Int>? = null,
    val lastSyncTime: Long = 0L,
    val widgetVisibleFields: Set<Int>? = null,
    val lastProcessedEntryId: Long = 0L
)

enum class SyncStatus {
    NONE, SUCCESS, ERROR_API, ERROR_AUTH, ERROR_NETWORK
}

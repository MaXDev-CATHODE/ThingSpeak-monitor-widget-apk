package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Model domenowy progu alertu per field.
 *
 * When the field value exceeds [minValue] or [maxValue],
 * system wyemituje local notification.
 */
data class AlertThreshold(
    val channelId: Long,
    val fieldNumber: Int,
    val fieldName: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val isEnabled: Boolean = true,
)

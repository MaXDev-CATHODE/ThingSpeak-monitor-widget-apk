package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Domain model of an alert threshold per field.
 *
 * When the field value exceeds [minValue] or [maxValue],
 * the system will emit a local notification.
 */
data class AlertThreshold(
    val channelId: Long,
    val fieldNumber: Int,
    val fieldName: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val isEnabled: Boolean = true,
)

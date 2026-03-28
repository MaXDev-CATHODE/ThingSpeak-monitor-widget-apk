package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Domain model of an advanced alert rule.
 */
data class AlertRule(
    val id: Long = 0,
    val channelId: Long,
    val appWidgetId: Int? = null,
    val fieldNumber: Int,
    val condition: String, // "GREATER_THAN", "LESS_THAN"
    val thresholdValue: Double,
    val isEnabled: Boolean = true
)

package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Model domenowy zaawansowanej reguły alertu.
 */
data class AlertRule(
    val id: Long = 0,
    val channelId: Long,
    val fieldNumber: Int,
    val condition: String, // "GREATER_THAN", "LESS_THAN"
    val thresholdValue: Double,
    val isEnabled: Boolean = true
)

package com.thingspeak.monitor.feature.alert.domain.model

/**
 * Domain model representing a notification state for a specific field.
 */
data class FiredAlert(
    val channelId: Long,
    val fieldNumber: Int,
    val lastFiredEntryId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val lastFiredTimestamp: Long? = null,
    val violationSignature: String = ""
)

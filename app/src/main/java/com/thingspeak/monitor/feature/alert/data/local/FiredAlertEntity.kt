package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.Entity

/**
 * Tracks which alerts have already been fired to avoid duplicate notifications.
 *
 * For a given channel and field, we store the [lastFiredEntryId] and a [violationSignature]
 * that describes the set of active violations. When the signature changes (e.g. from
 * GREATER_THAN to LESS_THAN), it means a different type of violation occurred and we should
 * re-fire the alert.
 */
@Entity(
    tableName = "fired_alerts",
    primaryKeys = ["channelId", "fieldNumber"]
)
data class FiredAlertEntity(
    val channelId: Long,
    val fieldNumber: Int,
    val lastFiredEntryId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val lastFiredTimestamp: Long? = null,
    val violationSignature: String = ""
)

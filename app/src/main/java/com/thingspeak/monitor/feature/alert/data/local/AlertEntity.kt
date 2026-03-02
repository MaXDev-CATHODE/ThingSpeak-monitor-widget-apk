package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
/**
 * Entity representing alert threshold for a specific field in a channel.
 * Moved from DataStore to Room for transaction support and relationships.
 */
@Entity(
    tableName = "alerts",
    indices = [androidx.room.Index(value = ["channelId"])]
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val fieldNumber: Int,
    val fieldName: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val isEnabled: Boolean = true
)

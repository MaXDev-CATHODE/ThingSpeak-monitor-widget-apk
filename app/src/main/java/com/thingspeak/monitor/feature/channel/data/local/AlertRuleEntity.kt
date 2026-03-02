package com.thingspeak.monitor.feature.channel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a custom alert rule for a ThingSpeak channel field.
 */
@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    val fieldNumber: Int, // 1..8
    val condition: String, // "GREATER_THAN", "LESS_THAN"
    val thresholdValue: Double,
    val isEnabled: Boolean = true
)

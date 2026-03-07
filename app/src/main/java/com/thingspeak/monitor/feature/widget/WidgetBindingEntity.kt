package com.thingspeak.monitor.feature.widget

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing the binding between a system appWidgetId and a ThingSpeak channelId.
 * Storing this in Room ensures multi-process consistency via enableMultiInstanceInvalidation.
 */
@Entity(tableName = "widget_bindings")
data class WidgetBindingEntity(
    @PrimaryKey val appWidgetId: Int,
    val channelId: Long
)

package com.thingspeak.monitor.feature.channel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity — local storage of a single feed entry from a ThingSpeak channel.
 *
 * Serves as an offline-first cache: widget and UI read from Room,
 * while synchronization with API happens in the background.
 */
@Entity(
    tableName = "feed_entries",
    indices = [Index(value = ["channelId", "createdAt"]), Index(value = ["createdAt"])]
)
data class FeedEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val entryId: Long = 0,
    val createdAt: String,
    val field1: String? = null,
    val field2: String? = null,
    val field3: String? = null,
    val field4: String? = null,
    val field5: String? = null,
    val field6: String? = null,
    val field7: String? = null,
    val field8: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)

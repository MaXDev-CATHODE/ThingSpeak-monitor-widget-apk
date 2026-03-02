package com.thingspeak.monitor.feature.channel.domain.model

/**
 * Domain model of a single feed entry.
 *
 * Independent of the data layer (Room/Retrofit).
 * Field values mapped as [Map<Int, String>] for flexibility.
 */
data class FeedEntry(
    val entryId: Long = 0,
    val createdAt: String,
    val fields: Map<Int, String>,
)

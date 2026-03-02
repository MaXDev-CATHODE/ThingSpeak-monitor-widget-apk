package com.thingspeak.monitor.feature.channel.data.mapper

import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity
import com.thingspeak.monitor.feature.channel.data.remote.dto.FeedEntryDto
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

/**
 * Conversion mappers between data layers.
 *
 * The Domain layer NEVER depends on DTOs or Entities —
 * these mappers ensure isolation.
 */

/** Converts API DTO → Room Entity (Data layer). */
fun FeedEntryDto.toEntity(channelId: Long): FeedEntryEntity = FeedEntryEntity(
    channelId = channelId,
    entryId = entryId ?: 0L,
    createdAt = createdAt,
    field1 = field1,
    field2 = field2,
    field3 = field3,
    field4 = field4,
    field5 = field5,
    field6 = field6,
    field7 = field7,
    field8 = field8,
)

/** Converts Room Entity → Domain Model (Domain layer). */
fun FeedEntryEntity.toDomain(): FeedEntry = FeedEntry(
    entryId = entryId,
    createdAt = createdAt,
    fields = listOfNotNull(
        field1?.toSafeNumericPair(1),
        field2?.toSafeNumericPair(2),
        field3?.toSafeNumericPair(3),
        field4?.toSafeNumericPair(4),
        field5?.toSafeNumericPair(5),
        field6?.toSafeNumericPair(6),
        field7?.toSafeNumericPair(7),
        field8?.toSafeNumericPair(8),
    ).toMap(),
)

/** 
 * Safely parses a string to a numeric value for domain mapping.
 * ThingSpeak might send "NaN", "null", or scientific notation like "1.23e-4".
 * Kotlin's toFloatOrNull() handles scientific notation on JVM.
 */
private fun String.toSafeNumericPair(index: Int): Pair<Int, String>? {
    val trimmed = this.trim()
    if (trimmed.equals("NaN", ignoreCase = true) || trimmed.equals("null", ignoreCase = true) || trimmed.isEmpty()) {
        return null
    }
    val floatValue = trimmed.toFloatOrNull()
    return if (floatValue != null && !floatValue.isNaN() && !floatValue.isInfinite()) {
        index to trimmed
    } else null
}

/** Converts API Channel DTO → Domain Channel model. */
fun com.thingspeak.monitor.feature.channel.data.remote.dto.ChannelDto.toDomain(
    apiKey: String? = null
): com.thingspeak.monitor.feature.channel.domain.model.Channel = 
    com.thingspeak.monitor.feature.channel.domain.model.Channel(
        id = id,
        name = name,
        description = description,
        apiKey = apiKey,
        fieldNames = listOfNotNull(
            field1?.let { 1 to it },
            field2?.let { 2 to it },
            field3?.let { 3 to it },
            field4?.let { 4 to it },
            field5?.let { 5 to it },
            field6?.let { 6 to it },
            field7?.let { 7 to it },
            field8?.let { 8 to it },
        ).toMap()
    )


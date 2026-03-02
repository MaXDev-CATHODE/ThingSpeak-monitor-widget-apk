package com.thingspeak.monitor.feature.channel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the response from /channels/{id}/feeds.json endpoint.
 *
 * Structure reflects the exact JSON format from ThingSpeak API.
 */
@Serializable
data class ChannelFeedResponse(
    val channel: ChannelDto,
    val feeds: List<FeedEntryDto>,
)

@Serializable
data class ChannelDto(
    val id: Long,
    val name: String,
    val description: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val field1: String? = null,
    val field2: String? = null,
    val field3: String? = null,
    val field4: String? = null,
    val field5: String? = null,
    val field6: String? = null,
    val field7: String? = null,
    val field8: String? = null,
)

@Serializable
data class FeedEntryDto(
    @SerialName("entry_id") val entryId: Long? = null,
    @SerialName("created_at") val createdAt: String,
    val field1: String? = null,
    val field2: String? = null,
    val field3: String? = null,
    val field4: String? = null,
    val field5: String? = null,
    val field6: String? = null,
    val field7: String? = null,
    val field8: String? = null,
)

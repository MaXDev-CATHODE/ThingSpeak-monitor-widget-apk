package com.thingspeak.monitor.feature.channel.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wrapper for public channel search response.
 * ThingSpeak API returns public channels under a "channels" array.
 */
@Serializable
data class PublicChannelsResponse(
    val channels: List<ChannelDto>
)

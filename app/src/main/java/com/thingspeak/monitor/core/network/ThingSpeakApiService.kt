package com.thingspeak.monitor.core.network

import com.thingspeak.monitor.feature.channel.data.remote.dto.ChannelFeedResponse
import com.thingspeak.monitor.feature.channel.data.remote.dto.FeedEntryDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface defining ThingSpeak API endpoints.
 */
interface ThingSpeakApiService {

    @GET("channels/{channelId}/feeds.json")
    suspend fun getChannelFeed(
        @Path("channelId") channelId: Long,
        @Query("results") results: Int = 100,
        @Query("api_key") apiKey: String? = null,
    ): Response<ChannelFeedResponse>

    @GET("channels/{channelId}/feeds/last.json")
    suspend fun getLastEntry(
        @Path("channelId") channelId: Long,
        @Query("api_key") apiKey: String? = null,
    ): Response<FeedEntryDto>

    @GET("channels/{channelId}/feeds.json")
    suspend fun getChannelFeedByDateRange(
        @Path("channelId") channelId: Long,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("api_key") apiKey: String? = null,
        @Query("average") average: Int? = null,
    ): Response<ChannelFeedResponse>

    @GET("channels/public.json")
    suspend fun searchPublicChannels(
        @Query("q") query: String? = null,
        @Query("tag") tag: String? = null,
        @Query("page") page: Int = 1
    ): Response<com.thingspeak.monitor.feature.channel.data.remote.dto.PublicChannelsResponse>
}

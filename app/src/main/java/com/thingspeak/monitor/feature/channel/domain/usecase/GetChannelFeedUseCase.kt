package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.core.error.safeApiCall
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fetches channel feed with an offline-first approach.
 *
 * [observe] — reactive stream from Room (immediate data).
 * [refresh] — forces synchronization with ThingSpeak API.
 */
class GetChannelFeedUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    /** Observes local data as Flow (offline-first). */
    fun observe(channelId: Long): Flow<List<FeedEntry>> {
        return repository.observeFeed(channelId)
    }

    /** Observes channel metadata (name, fields). */
    fun observeChannel(channelId: Long): Flow<com.thingspeak.monitor.feature.channel.domain.model.Channel?> {
        return repository.observeChannel(channelId)
    }

    /** Synchronizes data from API and saves to Room. */
    suspend fun refresh(channelId: Long, apiKey: String?, results: Int = 100): ApiResult<Unit> {
        return safeApiCall { repository.refreshFeed(channelId, apiKey, results) }
    }

    /** Updates channel settings (e.g. selected fields). */
    suspend fun updateChannel(channel: com.thingspeak.monitor.feature.channel.domain.model.Channel) {
        repository.updateChannel(channel)
    }
}

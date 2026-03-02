package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.core.error.safeApiCall
import com.thingspeak.monitor.feature.channel.domain.model.Channel
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import javax.inject.Inject

/**
 * UseCase for searching public ThingSpeak channels.
 */
class SearchChannelsUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    /**
     * Searches for public channels based on a query.
     */
    suspend operator fun invoke(query: String, page: Int = 1): ApiResult<List<Channel>> {
        return safeApiCall {
            repository.searchChannels(query, page)
        }
    }
}

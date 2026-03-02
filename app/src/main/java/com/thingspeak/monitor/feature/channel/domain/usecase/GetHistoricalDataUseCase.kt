package com.thingspeak.monitor.feature.channel.domain.usecase

import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.core.error.safeApiCall
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import javax.inject.Inject

/**
 * Fetches historical channel data within a date range.
 *
 * Used by ChartScreen for drawing charts.
 * z pinch-zoom i custom date range.
 */
class GetHistoricalDataUseCase @Inject constructor(
    private val repository: ChannelRepository,
) {
    /**
     * @param channelId ThingSpeak channel ID
     * @param apiKey    Klucz Read API (null dla publicznych)
     * @param start     ISO8601 start date (e.g., "2026-02-01T00:00:00Z")
     * @param end       ISO8601 end date
     */
    suspend operator fun invoke(
        channelId: Long,
        apiKey: String?,
        start: String,
        end: String,
        average: Int? = null,
    ): ApiResult<List<FeedEntry>> {
        return safeApiCall {
            repository.getHistoricalFeed(channelId, apiKey, start, end, average)
        }
    }
}

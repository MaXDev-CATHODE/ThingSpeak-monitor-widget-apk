package com.thingspeak.monitor.feature.channel.presentation

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

/**
 * MVI — State and Intent contract for the channel screen.
 *
 * [ChannelUiState] jest emitowany z ViewModel jako StateFlow.
 * [ChannelIntent] jest odbierany z warstwy UI i przetwarzany w ViewModel.
 * [ChannelEffect] to efekty jednorazowe (np. Snackbar, nawigacja).
 */

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Success(
        val channelName: String,
        val entries: List<FeedEntry>,
        val isRefreshing: Boolean = false,
        val isStale: Boolean = false,
    ) : ChannelUiState

    data class Error(val message: String) : ChannelUiState
}

sealed interface ChannelIntent {
    data class Load(val channelId: Long, val apiKey: String?) : ChannelIntent
    data object Refresh : ChannelIntent
}

sealed interface ChannelEffect {
    data class ShowError(val message: String) : ChannelEffect
}

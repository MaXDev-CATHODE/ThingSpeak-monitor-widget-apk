package com.thingspeak.monitor.feature.channel.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the channel screen — MVI pattern with auto-refresh.
 *
 * - Observes data from Room (offline-first) via [GetChannelFeedUseCase].
 * - Auto-refreshes every [AUTO_REFRESH_MS] ms while the app is in the foreground.
 * - Emits [ChannelUiState] as [StateFlow] and [ChannelEffect] as [SharedFlow].
 */
@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val getChannelFeed: GetChannelFeedUseCase,
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ChannelEffect>()
    val effect: SharedFlow<ChannelEffect> = _effect.asSharedFlow()

    private var currentChannelId: Long = savedStateHandle["channelId"] ?: 0L
    private var currentApiKey: String? = savedStateHandle["apiKey"]
    private var autoRefreshJob: Job? = null
    private var consecutiveErrors = 0

    init {
        if (currentChannelId != 0L) {
            loadChannel(currentChannelId, currentApiKey)
        }
    }

    companion object {
        /** Foreground auto-refresh interval — single source of truth from AppPreferences. */
        private const val AUTO_REFRESH_MS = AppPreferences.DEFAULT_REFRESH_MS

        /** Maximum consecutive auto-refresh failures before notifying the user. */
        private const val MAX_CONSECUTIVE_ERRORS = 3
    }

    /** Entry point for intents from the UI layer. */
    fun onIntent(intent: ChannelIntent) {
        when (intent) {
            is ChannelIntent.Load -> loadChannel(intent.channelId, intent.apiKey)
            is ChannelIntent.Refresh -> refreshChannel()
        }
    }

    private fun loadChannel(channelId: Long, apiKey: String?) {
        savedStateHandle["channelId"] = channelId
        savedStateHandle["apiKey"] = apiKey
        currentChannelId = channelId
        currentApiKey = apiKey
        _state.value = ChannelUiState.Loading

        // Observe local data (Room) — offline-first
        viewModelScope.launch {
            getChannelFeed.observe(channelId).collect { entries ->
                val currentState = _state.value
                val channelName = if (currentState is ChannelUiState.Success) {
                    currentState.channelName
                } else "Channel #$channelId"

                if (entries.isNotEmpty()) {
                    _state.value = ChannelUiState.Success(
                        channelName = channelName,
                        entries = entries,
                        isStale = currentState is ChannelUiState.Success && currentState.isStale
                    )
                }
            }
        }

        // Observe channel metadata (DataStore) for name and fields
        viewModelScope.launch {
            getChannelFeed.observeChannel(channelId).collect { channel ->
                val currentState = _state.value
                if (currentState is ChannelUiState.Success && channel != null) {
                    _state.value = currentState.copy(channelName = channel.name)
                }
            }
        }

        // First sync + start auto-refresh
        refreshChannel()
        startAutoRefresh()
    }

    private fun refreshChannel() {
        if (currentChannelId == 0L) return // guard: do not query API without a loaded channel

        viewModelScope.launch {
            val currentState = _state.value
            if (currentState is ChannelUiState.Success) {
                _state.value = currentState.copy(isRefreshing = true)
            }

            when (val result = getChannelFeed.refresh(currentChannelId, currentApiKey)) {
                is ApiResult.Success -> { /* Room Flow will emit automatically */ }
                is ApiResult.Error -> {
                    val msg = result.message ?: "Unknown Error"
                    _effect.emit(ChannelEffect.ShowError(msg))
                    if (_state.value is ChannelUiState.Loading) {
                        _state.value = ChannelUiState.Error(msg)
                    }
                }
                is ApiResult.Exception -> {
                    val msg = result.e.localizedMessage ?: "Connection Error"
                    _effect.emit(ChannelEffect.ShowError(msg))
                    if (_state.value is ChannelUiState.Loading) {
                        _state.value = ChannelUiState.Error(msg)
                    }
                }
                is ApiResult.Loading -> {
                     _state.value = ChannelUiState.Loading
                }
            }

            val current = _state.value
            if (current is ChannelUiState.Success) {
                _state.value = current.copy(isRefreshing = false)
            }
        }
    }

    /**
     * Auto-refresh every [AUTO_REFRESH_MS] in the foreground.
     *
     * Job is automatically cancelled when ViewModel is destroyed (viewModelScope).
     * After [MAX_CONSECUTIVE_ERRORS] consecutive failures, marks data as stale
     * and emits [ChannelEffect.ShowError].
     */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            appPreferences.observeRefreshInterval().collectLatest { interval ->
                while (isActive) {
                    delay(interval)
                    when (val result = getChannelFeed.refresh(currentChannelId, currentApiKey)) {
                        is ApiResult.Success -> {
                            consecutiveErrors = 0
                            val current = _state.value
                            if (current is ChannelUiState.Success && current.isStale) {
                                _state.value = current.copy(isStale = false)
                            }
                        }
                        is ApiResult.Error -> {
                            consecutiveErrors++
                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                val current = _state.value
                                if (current is ChannelUiState.Success) {
                                    _state.value = current.copy(isStale = true)
                                }
                                _effect.emit(ChannelEffect.ShowError(result.message ?: "Sync Error"))
                                consecutiveErrors = 0 // reset after notification
                            }
                        }
                        is ApiResult.Exception -> {
                            consecutiveErrors++
                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                val current = _state.value
                                if (current is ChannelUiState.Success) {
                                    _state.value = current.copy(isStale = true)
                                }
                                _effect.emit(ChannelEffect.ShowError(result.e.localizedMessage ?: "Exception"))
                                consecutiveErrors = 0
                            }
                        }
                        is ApiResult.Loading -> { /* nothing */ }
                    }
                }
            }
        }
    }
}

package com.thingspeak.monitor.feature.dashboard.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * UI State for the Dashboard screen.
 */
data class DashboardUiState(
    val channels: List<ChannelPreferences.SavedChannel> = emptyList(),
    val isLoading: Boolean = false,
    val searchResults: List<com.thingspeak.monitor.feature.channel.domain.model.Channel> = emptyList(),
    val isSearching: Boolean = false,
    val isOffline: Boolean = false
)

/**
 * UI Events for the Dashboard screen.
 */
sealed interface DashboardEvent {
    data class ShowError(val message: String) : DashboardEvent
}

/**
 * ViewModel for the Dashboard.
 * Manages the list of saved channels and coordination with UseCases.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val channelPreferences: ChannelPreferences,
    private val repository: com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository,
    private val getChannelFeedUseCase: com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase,
    private val searchChannelsUseCase: com.thingspeak.monitor.feature.channel.domain.usecase.SearchChannelsUseCase,
    private val networkMonitor: com.thingspeak.monitor.core.network.NetworkMonitor,
    private val savedStateHandle: SavedStateHandle,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events = _events.asSharedFlow()

    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _searchResults = kotlinx.coroutines.flow.MutableStateFlow<List<com.thingspeak.monitor.feature.channel.domain.model.Channel>>(emptyList())
    private val _isSearching = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(500L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        performSearch(query)
                    } else {
                        _searchResults.value = emptyList()
                    }
                }
        }
    }

    val uiState: StateFlow<DashboardUiState> = kotlinx.coroutines.flow.combine(
        channelPreferences.observe(),
        _isLoading,
        _searchResults,
        _isSearching,
        networkMonitor.isOnline
    ) { channels, loading, results, searching, isOnline ->
        DashboardUiState(
            channels = channels, 
            isLoading = loading,
            searchResults = results,
            isSearching = searching,
            isOffline = !isOnline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true)
    )

    /** Adds a new channel to the monitor list. Checks for duplicates. */
    fun addChannel(id: Long, name: String, apiKey: String?) {
        viewModelScope.launch {
            val existing = channelPreferences.observe().first()
            if (existing.any { it.id == id }) {
                _events.emit(DashboardEvent.ShowError("Channel already exists"))
                return@launch
            }
            
            _isLoading.value = true
            try {
                // Initial save is fast
                channelPreferences.save(ChannelPreferences.SavedChannel(id, name, apiKey))
                // Refresh is slow/network
                when (val result = getChannelFeedUseCase.refresh(id, apiKey)) {
                    is com.thingspeak.monitor.core.error.ApiResult.Error -> {
                        _events.emit(DashboardEvent.ShowError("Refresh failed: ${result.message}"))
                    }
                    else -> Unit
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Removes a channel and cleans up all associated data layers. */
    fun removeChannel(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                channelPreferences.remove(id)
                repository.removeChannel(id)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /** Refreshes data for all channels with concurrency protection. */
    fun refreshAll() {
        if (_isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val channels = uiState.value.channels
                val refreshJobs = channels.map { channel ->
                    async { getChannelFeedUseCase.refresh(channel.id, channel.apiKey) }
                }
                refreshJobs.awaitAll()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Searches for public channels. */
    fun searchPublicChannels(query: String) {
        _searchQuery.value = query
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        try {
            // Log search attempt for debugging
            android.util.Log.d("DashboardViewModel", "Searching for: $query")
            val result = searchChannelsUseCase(query)
            when (result) {
                is com.thingspeak.monitor.core.error.ApiResult.Success -> {
                    // Apply local sorting to prioritize relevant matches
                    val sortedData = result.data.sortedWith(
                        compareByDescending<com.thingspeak.monitor.feature.channel.domain.model.Channel> {
                            it.name.startsWith(query, ignoreCase = true)
                        }.thenByDescending {
                            it.name.contains(query, ignoreCase = true)
                        }
                    )
                    _searchResults.value = sortedData
                }
                is com.thingspeak.monitor.core.error.ApiResult.Error -> {
                    _events.emit(DashboardEvent.ShowError("Search failed: ${result.message}"))
                }
                else -> Unit
            }
        } catch (e: Exception) {
            _events.emit(DashboardEvent.ShowError("Search error: ${e.message}"))
        } finally {
            _isSearching.value = false
        }
    }
}

package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelSettingsUiState(
    val channel: ChannelPreferences.SavedChannel? = null,
    val alerts: List<AlertThreshold> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ChannelSettingsViewModel @Inject constructor(
    private val channelPreferences: ChannelPreferences,
    private val repository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSettingsUiState(isLoading = true))
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    fun loadChannel(channelId: Long) {
        viewModelScope.launch {
            combine(
                channelPreferences.observe().map { channels -> channels.find { it.id == channelId } },
                repository.observeAlerts(channelId)
            ) { channel, alerts ->
                ChannelSettingsUiState(
                    channel = channel,
                    alerts = alerts,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateWidgetSettings(
        transparency: Float? = null,
        bgColor: String? = null,
        fontSize: Int? = null,
        nameMode: String? = null,
        fieldMode: String? = null,
        isGlass: Boolean? = null
    ) {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            channelPreferences.save(
                current.copy(
                    widgetTransparency = transparency ?: current.widgetTransparency,
                    widgetBgColorHex = bgColor ?: current.widgetBgColorHex,
                    widgetFontSize = fontSize ?: current.widgetFontSize,
                    displayNameMode = nameMode ?: current.displayNameMode,
                    displayFieldMode = fieldMode ?: current.displayFieldMode,
                    isGlassmorphismEnabled = isGlass ?: current.isGlassmorphismEnabled
                )
            )
        }
    }

    fun updateChartSettings(
        rounding: Int? = null,
        processingType: String? = null,
        processingPeriod: Int? = null
    ) {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            channelPreferences.save(
                current.copy(
                    chartRounding = rounding ?: current.chartRounding,
                    chartProcessingType = processingType ?: current.chartProcessingType,
                    chartProcessingPeriod = processingPeriod ?: current.chartProcessingPeriod
                )
            )
        }
    }

    fun saveAlert(alert: AlertThreshold) {
        viewModelScope.launch {
            repository.saveAlert(alert)
        }
    }

    fun deleteAlert(alert: AlertThreshold) {
        viewModelScope.launch {
            repository.deleteAlert(alert)
        }
    }
}

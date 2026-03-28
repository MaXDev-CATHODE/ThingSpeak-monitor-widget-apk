package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelSettingsUiState(
    val channel: Channel? = null,
    val alerts: List<AlertThreshold> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ChannelSettingsViewModel @Inject constructor(
    private val repository: ChannelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSettingsUiState(isLoading = true))
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    fun loadChannel(channelId: Long) {
        viewModelScope.launch {
            combine(
                repository.observeChannel(channelId),
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
            repository.updateChannel(
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
        isNormalized: Boolean? = null,
        isMergingEnabled: Boolean? = null,
        preferredFields: Set<Int>? = null
    ) {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            // Use repository.updateChannel to ensure proper mapping
            repository.updateChannel(
                current.copy(
                    chartRounding = rounding ?: current.chartRounding,
                    chartProcessingType = processingType ?: current.chartProcessingType,
                    chartProcessingPeriod = current.chartProcessingPeriod,
                    isNormalized = isNormalized ?: current.isNormalized,
                    isMergingEnabled = isMergingEnabled ?: current.isMergingEnabled,
                    preferredChartFields = preferredFields ?: current.preferredChartFields
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

package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleDao
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

data class AlertRulesUiState(
    val channel: SavedChannel? = null,
    val rules: List<AlertRuleEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AlertRulesViewModel @Inject constructor(
    private val alertRuleDao: AlertRuleDao,
    private val channelPrefs: ChannelPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertRulesUiState())
    val uiState: StateFlow<AlertRulesUiState> = _uiState.asStateFlow()

    private var channelJob: Job? = null
    private var rulesJob: Job? = null

    fun loadChannel(channelId: Long) {
        channelJob?.cancel()
        rulesJob?.cancel()
        
        _uiState.update { it.copy(isLoading = true, rules = emptyList(), channel = null) }
        
        channelJob = viewModelScope.launch {
            channelPrefs.observe()
                .map { channels -> channels.find { it.id == channelId } }
                .collect { channel ->
                    _uiState.update { it.copy(channel = channel) }
                }
        }

        rulesJob = viewModelScope.launch {
            alertRuleDao.observeRulesForChannel(channelId).collect { rules ->
                _uiState.update { it.copy(rules = rules, isLoading = false) }
            }
        }
    }

    fun addRule(channelId: Long, fieldNumber: Int, condition: String, threshold: Double) {
        viewModelScope.launch {
            alertRuleDao.insertRule(
                AlertRuleEntity(
                    channelId = channelId,
                    fieldNumber = fieldNumber,
                    condition = condition,
                    thresholdValue = threshold
                )
            )
        }
    }

    fun deleteRule(rule: AlertRuleEntity) {
        viewModelScope.launch {
            alertRuleDao.deleteRule(rule)
        }
    }

    fun toggleRule(rule: AlertRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            alertRuleDao.updateRule(rule.copy(isEnabled = enabled))
        }
    }

    fun updateRule(rule: AlertRuleEntity, newFieldNumber: Int, newCondition: String, newThreshold: Double) {
        viewModelScope.launch {
            alertRuleDao.updateRule(
                rule.copy(
                    fieldNumber = newFieldNumber,
                    condition = newCondition,
                    thresholdValue = newThreshold
                )
            )
        }
    }

}

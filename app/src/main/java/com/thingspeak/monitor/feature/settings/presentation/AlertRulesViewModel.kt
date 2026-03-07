package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleDao
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertRulesUiState(
    val channel: ChannelPreferences.SavedChannel? = null,
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

    fun loadChannel(channelId: Long) {
        viewModelScope.launch {
            val channel = channelPrefs.observe().first().find { it.id == channelId }
            _uiState.update { it.copy(channel = channel) }
            
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

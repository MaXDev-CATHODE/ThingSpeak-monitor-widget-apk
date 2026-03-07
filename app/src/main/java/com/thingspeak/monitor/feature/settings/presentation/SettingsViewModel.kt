package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.core.datastore.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Settings screen.
 *
 * @param refreshIntervalMs Current refresh interval in milliseconds.
 */
data class SettingsUiState(
    val refreshIntervalMs: Long = 1_000L,
    val serverUrl: String = "https://api.thingspeak.com/",
    val syncIntervalMinutes: Long = 30L,
    val targetTheme: ThemePreference = ThemePreference.SYSTEM,
    val isChartMergingEnabled: Boolean = false,
    val isHighFrequencyEnabled: Boolean = false,
    val highFrequencyIntervalMinutes: Long = 5L
)

/**
 * ViewModel for application settings.
 * Manages global preferences like refresh interval.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val firedAlertDao: com.thingspeak.monitor.feature.alert.data.local.FiredAlertDao
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        appPreferences.observeRefreshInterval(),
        appPreferences.observeServerUrl(),
        appPreferences.observeSyncInterval(),
        appPreferences.observeThemePreference(),
        appPreferences.observeChartMerging(),
        appPreferences.observeIsHighFrequencyEnabled(),
        appPreferences.observeHighFrequencyInterval()
    ) { flows ->
        SettingsUiState(
            refreshIntervalMs = flows[0] as Long,
            serverUrl = flows[1] as String,
            syncIntervalMinutes = flows[2] as Long,
            targetTheme = flows[3] as ThemePreference,
            isChartMergingEnabled = flows[4] as Boolean,
            isHighFrequencyEnabled = flows[5] as Boolean,
            highFrequencyIntervalMinutes = flows[6] as Long
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    /** Updates the refresh interval. */
    fun setRefreshInterval(intervalMs: Long) {
        viewModelScope.launch {
            appPreferences.setRefreshInterval(intervalMs)
        }
    }

    /** Updates the custom server URL. */
    fun setServerUrl(url: String) {
        viewModelScope.launch {
            appPreferences.setServerUrl(url)
        }
    }

    /** Updates the sync interval and reschedules the worker. */
    fun setSyncInterval(context: android.content.Context, minutes: Long) {
        viewModelScope.launch {
            appPreferences.setSyncInterval(minutes)
            com.thingspeak.monitor.core.worker.DataSyncWorker.schedule(context, minutes)
            appPreferences.setIsWorkerScheduled(true)
        }
    }

    /** Updates the application theme preference. */
    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            appPreferences.setThemePreference(theme)
        }
    }

    /** Updates the chart merging preference. */
    fun setChartMerging(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setChartMerging(enabled)
        }
    }

    /** Updates high-frequency monitoring state. */
    fun setHighFrequencyEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setIsHighFrequencyEnabled(enabled)
            if (enabled) {
                com.thingspeak.monitor.core.worker.DataSyncService.start(context)
            } else {
                com.thingspeak.monitor.core.worker.DataSyncService.stop(context)
            }
        }
    }

    /** Updates high-frequency interval. */
    fun setHighFrequencyInterval(context: android.content.Context, minutes: Long) {
        viewModelScope.launch {
            appPreferences.setHighFrequencyInterval(minutes)
            // Restart service to apply new interval if enabled
            val isEnabled = appPreferences.observeIsHighFrequencyEnabled().first()
            if (isEnabled) {
                com.thingspeak.monitor.core.worker.DataSyncService.start(context)
            }
        }
    }

    /** Clears all fired alert states, forcing re-evaluation on next sync. */
    fun resetAlerts() {
        viewModelScope.launch {
            firedAlertDao.deleteAll()
            android.util.Log.d("SettingsVM", "All fired alerts cleared — will re-trigger on next sync")
        }
    }
}

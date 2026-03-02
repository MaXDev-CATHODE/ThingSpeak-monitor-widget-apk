package com.thingspeak.monitor.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.core.datastore.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val isChartMergingEnabled: Boolean = false
)

/**
 * ViewModel for application settings.
 * Manages global preferences like refresh interval.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        appPreferences.observeRefreshInterval(),
        appPreferences.observeServerUrl(),
        appPreferences.observeSyncInterval(),
        appPreferences.observeThemePreference(),
        appPreferences.observeChartMerging()
    ) { refresh, url, sync, theme, mergeCharts ->
        SettingsUiState(
            refreshIntervalMs = refresh,
            serverUrl = url,
            syncIntervalMinutes = sync,
            targetTheme = theme,
            isChartMergingEnabled = mergeCharts
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
}

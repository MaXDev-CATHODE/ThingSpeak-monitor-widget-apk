package com.thingspeak.monitor.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

/**
 * Persistence layer for global application settings.
 *
 * Responsibility: ONLY global preferences (SRP).
 */
@Singleton
class AppPreferences @Inject constructor(
    @com.thingspeak.monitor.core.di.SettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    companion object {
        /** Default foreground refresh interval: 1 second (near real-time). */
        const val DEFAULT_REFRESH_MS = 1_000L
    }

    private val refreshKey = longPreferencesKey("refresh_interval_ms")
    private val serverUrlKey = androidx.datastore.preferences.core.stringPreferencesKey("server_url")
    private val syncIntervalKey = androidx.datastore.preferences.core.longPreferencesKey("sync_interval_minutes")
    private val themeKey = androidx.datastore.preferences.core.stringPreferencesKey("theme_preference")
    private val migrationCompletedKey = androidx.datastore.preferences.core.booleanPreferencesKey("is_migration_completed")
    private val chartRangeKey = androidx.datastore.preferences.core.intPreferencesKey("last_chart_range_days")
    private val chartSmoothingKey = androidx.datastore.preferences.core.booleanPreferencesKey("is_chart_smoothing_enabled")
    private val chartFilterKey = androidx.datastore.preferences.core.stringPreferencesKey("last_chart_filter")
    private val chartMergingKey = androidx.datastore.preferences.core.booleanPreferencesKey("is_chart_merging_enabled")
    private val isWorkerScheduledKey = androidx.datastore.preferences.core.booleanPreferencesKey("is_worker_scheduled")
    private val isHighFrequencyEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("is_high_frequency_enabled")
    private val highFrequencyIntervalKey = androidx.datastore.preferences.core.longPreferencesKey("high_frequency_interval_minutes")

    /** Observes the migration status. */
    fun observeMigrationCompleted(): Flow<Boolean> =
        dataStore.data
            .map { it[migrationCompletedKey] ?: false }
            .distinctUntilChanged()

    /** Sets the migration status. */
    suspend fun setMigrationCompleted(completed: Boolean) {
        dataStore.edit { it[migrationCompletedKey] = completed }
    }

    /** Observes the refresh interval (emits only when this key changes). */
    fun observeRefreshInterval(): Flow<Long> =
        dataStore.data
            .map { it[refreshKey] ?: DEFAULT_REFRESH_MS }
            .distinctUntilChanged()

    /** Sets the refresh interval. */
    suspend fun setRefreshInterval(intervalMs: Long) {
        dataStore.edit { it[refreshKey] = intervalMs }
    }

    /** Observes the Custom Server URL. */
    fun observeServerUrl(): Flow<String> =
        dataStore.data
            .map { it[serverUrlKey] ?: "https://api.thingspeak.com/" }
            .distinctUntilChanged()

    /** Sets the Custom Server URL. */
    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[serverUrlKey] = url }
    }

    /** Observes the Background Sync Interval (in minutes). */
    fun observeSyncInterval(): Flow<Long> =
        dataStore.data
            .map { it[syncIntervalKey] ?: 30L } // Default 30 minutes
            .distinctUntilChanged()

    /** Sets the Background Sync Interval (in minutes). */
    suspend fun setSyncInterval(minutes: Long) {
        dataStore.edit { it[syncIntervalKey] = minutes }
    }

    /** Observes the Theme Preference. */
    fun observeThemePreference(): Flow<ThemePreference> =
        dataStore.data
            .map { prefs ->
                val themeName = prefs[themeKey] ?: ThemePreference.SYSTEM.name
                try {
                    ThemePreference.valueOf(themeName)
                } catch (e: Exception) {
                    ThemePreference.SYSTEM
                }
            }
            .distinctUntilChanged()

    /** Sets the Theme Preference. */
    suspend fun setThemePreference(theme: ThemePreference) {
        dataStore.edit { it[themeKey] = theme.name }
    }

    /** Observes the last used chart range. */
    fun observeChartRange(): Flow<Int> =
        dataStore.data.map { it[chartRangeKey] ?: 1 }.distinctUntilChanged()

    /** Sets the last used chart range. */
    suspend fun setChartRange(days: Int) {
        dataStore.edit { it[chartRangeKey] = days }
    }

    /** Observes if chart smoothing is enabled. */
    fun observeChartSmoothing(): Flow<Boolean> =
        dataStore.data.map { it[chartSmoothingKey] ?: false }.distinctUntilChanged()

    /** Sets the chart smoothing state. */
    suspend fun setChartSmoothing(enabled: Boolean) {
        dataStore.edit { it[chartSmoothingKey] = enabled }
    }

    /** Observes the last used chart data filter. */
    fun observeChartFilter(): Flow<String> =
        dataStore.data.map { it[chartFilterKey] ?: "NONE" }.distinctUntilChanged()

    /** Sets the last used chart data filter. */
    suspend fun setChartFilter(filterName: String) {
        dataStore.edit { it[chartFilterKey] = filterName }
    }

    /** Observes if fields should be merged into one chart. */
    fun observeChartMerging(): Flow<Boolean> =
        dataStore.data.map { it[chartMergingKey] ?: false }.distinctUntilChanged()

    /** Sets the chart merging state. */
    suspend fun setChartMerging(enabled: Boolean) {
        dataStore.edit { it[chartMergingKey] = enabled }
    }

    /** Observes if the background worker has been scheduled. */
    fun observeIsWorkerScheduled(): Flow<Boolean> =
        dataStore.data.map { it[isWorkerScheduledKey] ?: false }.distinctUntilChanged()

    /** Sets the background worker scheduled state. */
    suspend fun setIsWorkerScheduled(scheduled: Boolean) {
        dataStore.edit { it[isWorkerScheduledKey] = scheduled }
    }

    /** Observes if high-frequency monitoring is enabled. */
    fun observeIsHighFrequencyEnabled(): Flow<Boolean> =
        dataStore.data.map { it[isHighFrequencyEnabledKey] ?: false }.distinctUntilChanged()

    /** Sets the high-frequency monitoring state. */
    suspend fun setIsHighFrequencyEnabled(enabled: Boolean) {
        dataStore.edit { it[isHighFrequencyEnabledKey] = enabled }
    }

    /** Observes the high-frequency monitoring interval (in minutes). */
    fun observeHighFrequencyInterval(): Flow<Long> =
        dataStore.data.map { it[highFrequencyIntervalKey] ?: 5L }.distinctUntilChanged()

    /** Sets the high-frequency monitoring interval (in minutes). */
    suspend fun setHighFrequencyInterval(minutes: Long) {
        dataStore.edit { it[highFrequencyIntervalKey] = minutes }
    }
}

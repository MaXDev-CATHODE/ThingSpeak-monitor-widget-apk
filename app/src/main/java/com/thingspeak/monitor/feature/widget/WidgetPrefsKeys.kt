package com.thingspeak.monitor.feature.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Centralized preference keys and magic string constants for both widget types.
 * Eliminates hardcoded literals scattered across the widget module.
 */
object WidgetPrefsKeys {
    // ---- Preference Keys ----
    val KEY_CHANNEL_ID: Preferences.Key<Long> = longPreferencesKey("channel_id")
    val KEY_CHANNEL_NAME: Preferences.Key<String> = stringPreferencesKey("channel_name")
    val KEY_FIELD_NAMES: Preferences.Key<String> = stringPreferencesKey("field_names")
    val KEY_FIELD_UNITS: Preferences.Key<String> = stringPreferencesKey("field_units")
    val KEY_CHART_RESULTS: Preferences.Key<Int> = intPreferencesKey("chart_results")
    val KEY_ROUNDING: Preferences.Key<Int> = intPreferencesKey("rounding")
    val KEY_BG_COLOR: Preferences.Key<String> = stringPreferencesKey("bg_color")
    val KEY_TEXT_COLOR: Preferences.Key<String> = stringPreferencesKey("text_color")
    val KEY_TRANSPARENCY: Preferences.Key<Float> = floatPreferencesKey("transparency")
    val KEY_FONT_SIZE: Preferences.Key<Int> = intPreferencesKey("font_size")
    val KEY_IS_GLASS: Preferences.Key<Boolean> = booleanPreferencesKey("is_glass")
    val KEY_IS_REFRESHING: Preferences.Key<Boolean> = booleanPreferencesKey("is_refreshing")
    val KEY_CACHED_ENTRY: Preferences.Key<String> = stringPreferencesKey("cached_entry")
    val KEY_CHART_BITMAP: Preferences.Key<String> = stringPreferencesKey("chart_bitmap")
    val KEY_LAST_SYNC_STATUS: Preferences.Key<String> = stringPreferencesKey("last_sync_status")
    val KEY_CHANNEL_TIMEZONE: Preferences.Key<String> = stringPreferencesKey("channel_timezone")
    val KEY_VISIBLE_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("visible_fields")
    val KEY_VIOLATED_MIN_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("violated_min_fields")
    val KEY_VIOLATED_MAX_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("violated_max_fields")
    val KEY_MIN_SET_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("min_set_fields")
    val KEY_MAX_SET_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("max_set_fields")

    // ---- Magic Strings ----
    const val LOADING_PLACEHOLDER = "Loading..."
    const val STATUS_ERROR_SYNC = "ERROR_SYNC"
    const val STATUS_NONE = "NONE"
    const val ALERT_CONDITION_LESS_THAN = "LESS_THAN"
    const val ALERT_CONDITION_GREATER_THAN = "GREATER_THAN"

    val KEY_WIDGET_VISUALS_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("widget_visuals_customized")
}
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
    val KEY_CHART_FILE: Preferences.Key<String> = stringPreferencesKey("chart_file")
    val KEY_LAST_SYNC_STATUS: Preferences.Key<String> = stringPreferencesKey("last_sync_status")
    val KEY_CHANNEL_TIMEZONE: Preferences.Key<String> = stringPreferencesKey("channel_timezone")
    val KEY_VISIBLE_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("visible_fields")
    val KEY_VIOLATED_MIN_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("violated_min_fields")
    val KEY_VIOLATED_MAX_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("violated_max_fields")
    val KEY_MIN_SET_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("min_set_fields")
    val KEY_MAX_SET_FIELDS: Preferences.Key<Set<String>> = stringSetPreferencesKey("max_set_fields")
    val KEY_BG_COLOR_MODE: Preferences.Key<String> = stringPreferencesKey("bg_color_mode")

    // ---- Magic Strings ----
    const val LOADING_PLACEHOLDER = "Loading..."
    const val STATUS_ERROR_SYNC = "ERROR_SYNC"
    const val STATUS_NONE = "NONE"
    const val ALERT_CONDITION_LESS_THAN = "LESS_THAN"
    const val ALERT_CONDITION_GREATER_THAN = "GREATER_THAN"
    const val COLOR_MODE_CUSTOM = "custom"
    const val COLOR_MODE_SYSTEM = "system"

    val KEY_WIDGET_VISUALS_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("widget_visuals_customized")
    val KEY_HEAL_ATTEMPTED: Preferences.Key<Boolean> = booleanPreferencesKey("_heal_attempted")
    val KEY_HEAL_RETRY_COUNT: Preferences.Key<Int> = intPreferencesKey("_heal_retry_count")
    const val MAX_HEAL_RETRIES = 3
    val KEY_HEAL_LAST_ATTEMPT_MS: Preferences.Key<Long> = longPreferencesKey("_heal_last_attempt_ms")
    const val HEAL_COOLDOWN_MINUTES = 5L

    val KEY_BG_COLOR_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("_bg_color_customized")
    val KEY_TEXT_COLOR_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("_text_color_customized")
    val KEY_TRANSPARENCY_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("_transparency_customized")
    val KEY_FONT_SIZE_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("_font_size_customized")
    val KEY_IS_GLASS_CUSTOMIZED: Preferences.Key<Boolean> = booleanPreferencesKey("_is_glass_customized")

    const val STATUS_OFFLINE = "OFFLINE"

    // ---- Widget layout constants (dp) ----
    const val HEIGHT_TINY_THRESHOLD = 100
    const val HEIGHT_COMPACT_THRESHOLD = 140
    const val WIDTH_TINY_THRESHOLD = 150
    const val WIDTH_COMPACT_THRESHOLD = 200
    const val HEIGHT_SMALL_GRID = 120
    const val TINY_MAX_FIELDS = 2
    const val COMPACT_MAX_FIELDS = 4

    // ---- Log tags ----
    const val LOG_TAG_WIDGET = "TS_WIDGET"
    const val LOG_TAG_RECEIVER = "TS_RECEIVER"
    const val LOG_TAG_WORKER = "TS_WORKER"
    const val LOG_TAG_CONFIG = "TS_CONFIG"
    const val LOG_TAG_CHART = "TS_CHART"
}
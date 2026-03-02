package com.thingspeak.monitor.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence layer for alert thresholds per field.
 *
 * Responsibility: ONLY alerts (SRP).
 */
@Singleton
class AlertPreferences @Inject constructor(
    @com.thingspeak.monitor.core.di.AlertsDataStore private val dataStore: DataStore<Preferences>,
    json: Json,
) {
    @Serializable
    data class SavedAlert(
        val channelId: Long,
        val fieldNumber: Int,
        val fieldName: String = "",
        val minValue: Double? = null,
        val maxValue: Double? = null,
        val isEnabled: Boolean = true,
    )

    private val helper = DataStoreListHelper(
        json = json,
        key = stringPreferencesKey("alert_thresholds"),
        serializer = SavedAlert.serializer(),
    )

    /** Observes alerts (emits only when this key changes). */
    fun observe(): Flow<List<SavedAlert>> = helper.observe(dataStore.data)

    /** Adds or updates an alert (upsert by channelId + fieldNumber). */
    suspend fun save(alert: SavedAlert) {
        dataStore.edit { prefs ->
            helper.upsert(prefs, alert) {
                it.channelId == alert.channelId && it.fieldNumber == alert.fieldNumber
            }
        }
    }

    /** Removes an alert by channelId and fieldNumber. */
    suspend fun remove(channelId: Long, fieldNumber: Int) {
        dataStore.edit { prefs ->
            helper.remove(prefs) { it.channelId == channelId && it.fieldNumber == fieldNumber }
        }
    }

    /** Clears all alerts from DataStore (used after migration to Room). */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            helper.clear(prefs)
        }
    }
}

package com.thingspeak.monitor.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thingspeak.monitor.core.di.ChannelsDataStore
import com.thingspeak.monitor.core.utils.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence layer for saved ThingSpeak channels.
 *
 * Responsibility: ONLY the channel list (SRP).
 */
@Singleton
class ChannelPreferences @Inject constructor(
    @ChannelsDataStore private val dataStore: DataStore<Preferences>,
    private val cryptoManager: CryptoManager,
    private val json: Json,
) {
    /**
     * A channel saved by the user.
     *
     * [apiKey] is now encrypted via CryptoManager before saving.
     */
    @Serializable
    data class SavedChannel(
        val id: Long,
        val name: String,
        val apiKey: String? = null, // Transient-like: updated on observe
        val fieldNames: Map<Int, String> = emptyMap(),
        val lastSyncStatus: String = "NONE",
        val widgetBgColorHex: String? = "#FFFFFF",
        val widgetTransparency: Float = 1.0f,
        val widgetFontSize: Int = 12,
        val widgetVisibleFields: Set<Int>? = null,
        val displayNameMode: String = "NAME", // NAME, ID, COMBINED
        val displayFieldMode: String = "NAME", // NAME, ID, COMBINED
        val isGlassmorphismEnabled: Boolean = false,
        val chartRounding: Int = 2,
        val chartProcessingType: String = "NONE", // NONE, AVERAGE, MEDIAN, SUM
        val chartProcessingPeriod: Int = 0, // hours
        val lastSyncTime: Long = 0L,
        val preferredChartFields: Set<Int>? = null,
        val chartField: Int = 1
    )

    private val helper = DataStoreListHelper(
        json = json,
        key = stringPreferencesKey("saved_channels"),
        serializer = SavedChannel.serializer(),
    )

    /** Observes the channel list with decrypted API keys. */
    fun observe(): Flow<List<SavedChannel>> = helper.observe(dataStore.data)
        .map { list ->
            list.map { channel ->
                channel.copy(apiKey = cryptoManager.getApiKey(channel.id))
            }
        }

    /** Adds or updates a channel. */
    suspend fun save(channel: SavedChannel) {
        cryptoManager.saveApiKey(channel.id, channel.apiKey)
        dataStore.edit { prefs ->
            // Save channel WITHOUT the plain-text apiKey in DataStore
            helper.upsert(prefs, channel.copy(apiKey = null)) { it.id == channel.id }
        }
    }

    /** Removes a channel by ID. */
    suspend fun remove(channelId: Long) {
        cryptoManager.removeApiKey(channelId)
        dataStore.edit { prefs ->
            helper.remove(prefs) { it.id == channelId }
        }
    }

    /** Clears all channels from DataStore and their secured API keys. */
    suspend fun clearAll() {
        cryptoManager.clearAllApiKeys()
        dataStore.edit { prefs ->
            helper.clear(prefs)
        }
    }
}

package com.thingspeak.monitor.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thingspeak.monitor.core.di.ChannelsDataStore
import com.thingspeak.monitor.core.utils.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelPreferences @Inject constructor(
    @ChannelsDataStore private val dataStore: DataStore<Preferences>,
    private val cryptoManager: CryptoManager,
    private val json: Json,
) {
    private val helper = DataStoreListHelper(
        json = json,
        key = stringPreferencesKey("saved_channels"),
        serializer = SavedChannel.serializer(),
    )

    fun observe(): Flow<List<SavedChannel>> = helper.observe(dataStore.data)
        .map { list -> list.map { it.copy(apiKey = cryptoManager.getApiKey(it.id)) } }

    /** Adds or updates a channel. */
    suspend fun save(channel: SavedChannel) {
        cryptoManager.saveApiKey(channel.id, channel.apiKey)
        dataStore.edit { prefs -> 
            helper.upsert(prefs, channel.copy(apiKey = null)) { it.id == channel.id } 
        }
    }

    /** Removes a channel by ID. */
    suspend fun remove(channelId: Long) {
        cryptoManager.saveApiKey(channelId, null)
        dataStore.edit { prefs ->
            helper.remove(prefs) { it.id == channelId }
        }
    }

    /** Clears all channels. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            helper.clear(prefs)
        }
    }
}

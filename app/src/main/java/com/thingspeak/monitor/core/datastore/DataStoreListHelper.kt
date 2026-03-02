package com.thingspeak.monitor.core.datastore

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Generic helper eliminating CRUD duplication for serialized lists in DataStore.
 *
 * Each operation is O(N) on the list — acceptable for small collections (channels, alerts).
 * [distinctUntilChanged] prevents redundant deserialization when unrelated keys change.
 */
internal class DataStoreListHelper<T>(
    private val json: Json,
    private val key: Preferences.Key<String>,
    private val serializer: KSerializer<T>,
) {
    private val listSerializer = ListSerializer(serializer)

    /** Reactive stream of the list — emits ONLY when this key changes. */
    fun observe(data: Flow<Preferences>): Flow<List<T>> =
        data.map { prefs -> decode(prefs) }.distinctUntilChanged()

    /** Upsert: adds or replaces an element matching [predicate]. */
    fun upsert(prefs: MutablePreferences, item: T, predicate: (T) -> Boolean) {
        val current = decode(prefs).toMutableList()
        current.removeAll(predicate)
        current.add(item)
        prefs[key] = json.encodeToString(listSerializer, current)
    }

    /** Removes elements matching [predicate]. */
    fun remove(prefs: MutablePreferences, predicate: (T) -> Boolean) {
        val current = decode(prefs).toMutableList()
        if (current.removeAll(predicate)) {
            prefs[key] = json.encodeToString(listSerializer, current)
        }
    }

    /** Clears all items in the list. */
    fun clear(prefs: MutablePreferences) {
        prefs.remove(key)
    }

    private fun decode(prefs: Preferences): List<T> {
        val raw = prefs[key] ?: return emptyList()
        return try {
            val jsonArray = json.parseToJsonElement(raw)
            if (jsonArray is kotlinx.serialization.json.JsonArray) {
                jsonArray.mapNotNull { element ->
                    try {
                        json.decodeFromJsonElement(serializer, element)
                    } catch (e: Exception) {
                        Log.e("DataStoreListHelper", "Failed to decode list item for key '${key.name}': $element", e)
                        null
                    }
                }
            } else {
                json.decodeFromString(listSerializer, raw)
            }
        } catch (e: Exception) {
            Log.e("DataStoreListHelper", "Fatal corruption for key '${key.name}', returning empty list", e)
            emptyList()
        }
    }
}

package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.glance.state.GlanceStateDefinition
import java.io.File

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custom GlanceStateDefinition using DataStore Preferences.
 * Re-implemented to use standard context.preferencesDataStoreFile path management
 * and crucially caches instances to prevent Multiple DataStore instances exceptions.
 */
object WidgetPreferencesStateDefinition : GlanceStateDefinition<Preferences> {

    private const val DATA_STORE_FILENAME_PREFIX = "glance_widget_"
    private val dataStores = mutableMapOf<String, DataStore<Preferences>>()
    private val mutex = Mutex()

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ): DataStore<Preferences> {
        return mutex.withLock {
            dataStores.getOrPut(fileKey) {
                PreferenceDataStoreFactory.create {
                    context.preferencesDataStoreFile(DATA_STORE_FILENAME_PREFIX + fileKey)
                }
            }
        }
    }

    override fun getLocation(
        context: Context,
        fileKey: String,
    ): File {
        return context.preferencesDataStoreFile(DATA_STORE_FILENAME_PREFIX + fileKey)
    }
}

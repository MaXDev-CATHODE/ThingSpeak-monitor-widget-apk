package com.thingspeak.monitor.core.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChannelsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlertsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetBindingDataStore

/**
 * Hilt module providing separate DataStore instances for different domains.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = 
        createMultiProcessPrefs("thingspeak_settings", context)

    @Provides
    @Singleton
    @ChannelsDataStore
    fun provideChannelsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = 
        createMultiProcessPrefs("thingspeak_channels", context)

    @Provides
    @Singleton
    @AlertsDataStore
    fun provideAlertsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = 
        createMultiProcessPrefs("thingspeak_alerts", context)

    private fun createMultiProcessPrefs(name: String, context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { File(context.filesDir, "datastore/$name.preferences_pb") }
        )
    }
}

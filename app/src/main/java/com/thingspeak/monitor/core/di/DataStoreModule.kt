package com.thingspeak.monitor.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "thingspeak_settings")
private val Context.channelsDataStore: DataStore<Preferences> by preferencesDataStore(name = "thingspeak_channels")
private val Context.alertsDataStore: DataStore<Preferences> by preferencesDataStore(name = "thingspeak_alerts")

/**
 * Hilt module providing separate DataStore instances for different domains.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    @ChannelsDataStore
    fun provideChannelsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.channelsDataStore

    @Provides
    @Singleton
    @AlertsDataStore
    fun provideAlertsDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.alertsDataStore
}

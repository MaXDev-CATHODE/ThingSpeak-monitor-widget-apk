package com.thingspeak.monitor.core.di

import com.thingspeak.monitor.core.notifications.AlertManager
import com.thingspeak.monitor.core.notifications.AlertManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds system-related interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindAlertManager(
        alertManagerImpl: AlertManagerImpl
    ): AlertManager
}

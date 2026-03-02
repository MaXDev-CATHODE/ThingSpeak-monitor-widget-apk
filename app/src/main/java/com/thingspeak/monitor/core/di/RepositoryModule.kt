package com.thingspeak.monitor.core.di

import com.thingspeak.monitor.feature.channel.data.repository.ChannelRepositoryImpl
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding repository interfaces to their implementations.
 *
 * Thanks to [@Binds], Hilt knows that when requested for [ChannelRepository],
 * it should provide [ChannelRepositoryImpl].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository
}

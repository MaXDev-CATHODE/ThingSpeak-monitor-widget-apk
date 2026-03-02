package com.thingspeak.monitor.core.di

import com.thingspeak.monitor.feature.channel.data.local.ChannelFeedDao
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for Hilt dependencies in components not managed by Hilt (e.g., Glance Widgets).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun channelRepository(): ChannelRepository
    fun channelFeedDao(): ChannelFeedDao
    fun channelPreferences(): ChannelPreferences
    fun appPreferences(): com.thingspeak.monitor.core.datastore.AppPreferences
    fun checkAlertThresholdsUseCase(): com.thingspeak.monitor.feature.channel.domain.usecase.CheckAlertThresholdsUseCase
    fun getChannelFeedUseCase(): com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase
}

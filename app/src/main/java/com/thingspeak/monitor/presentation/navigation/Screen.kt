package com.thingspeak.monitor.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation routes definitions for the application.
 * Uses kotlinx.serialization for Type-Safe Navigation.
 */
@Serializable
sealed interface Screen {
    @Serializable
    data object Dashboard : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class Chart(
        val channelId: Long,
        val channelName: String,
        val apiKey: String?
    ) : Screen

    @Serializable
    data class ChannelSettings(val channelId: Long) : Screen

    @Serializable
    data class AlertRules(val channelId: Long) : Screen
}

package com.thingspeak.monitor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.thingspeak.monitor.feature.channel.data.local.ChannelFeedDao
import com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity

/**
 * Main Room database definition.
 *
 * Stores cached feeds for ThingSpeak channels.
 * Version is incremented on every schema change.
 */
@Database(
    entities = [
        FeedEntryEntity::class,
        com.thingspeak.monitor.feature.alert.data.local.AlertEntity::class,
        com.thingspeak.monitor.feature.alert.data.local.FiredAlertEntity::class,
        com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class ThingSpeakDatabase : RoomDatabase() {
    abstract fun channelFeedDao(): ChannelFeedDao
    abstract fun alertDao(): com.thingspeak.monitor.feature.alert.data.local.AlertDao
    abstract fun firedAlertDao(): com.thingspeak.monitor.feature.alert.data.local.FiredAlertDao
    abstract fun alertRuleDao(): com.thingspeak.monitor.feature.channel.data.local.AlertRuleDao
}

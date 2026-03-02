package com.thingspeak.monitor.core.di

import android.content.Context
import androidx.room.Room
import com.thingspeak.monitor.core.database.ThingSpeakDatabase
import com.thingspeak.monitor.feature.channel.data.local.ChannelFeedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module configuring the Room database.
 *
 * The database serves as a local cache repository (offline-first).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // No schema changes between 1 and 2 in this simplified version
        }
    }

    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fired_alerts` (
                    `channelId` INTEGER NOT NULL,
                    `fieldNumber` INTEGER NOT NULL,
                    `lastFiredEntryId` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`channelId`, `fieldNumber`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `fired_alerts` ADD COLUMN `lastFiredTimestamp` INTEGER")
        }
    }

    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_entries_channelId` ON `feed_entries` (`channelId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_channelId` ON `alerts` (`channelId`)")
            db.execSQL("ALTER TABLE `feed_entries` ADD COLUMN `lastUpdated` INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ThingSpeakDatabase {
        return Room.databaseBuilder(
            context,
            ThingSpeakDatabase::class.java,
            "thingspeak_monitor.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChannelFeedDao(database: ThingSpeakDatabase): ChannelFeedDao {
        return database.channelFeedDao()
    }

    @Provides
    fun provideAlertDao(db: ThingSpeakDatabase) = db.alertDao()

    @Provides
    fun provideFiredAlertDao(db: ThingSpeakDatabase) = db.firedAlertDao()

    @Provides
    fun provideAlertRuleDao(db: ThingSpeakDatabase) = db.alertRuleDao()
}

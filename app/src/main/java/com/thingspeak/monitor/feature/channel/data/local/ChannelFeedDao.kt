package com.thingspeak.monitor.feature.channel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the feed_entries table.
 *
 * Returns [Flow] so UI and Widget reactively observe data changes.
 */
@Dao
interface ChannelFeedDao {

    /**
     * Observes the latest entries for a given channel (sorted from newest).
     */
    @Query("SELECT * FROM feed_entries WHERE channelId = :channelId ORDER BY entryId DESC")
    fun observeFeedEntries(channelId: Long): Flow<List<FeedEntryEntity>>

    /**
     * V10: Limit data for chart widgets to prevent SLOW loading.
     */
    @Query("SELECT * FROM feed_entries WHERE channelId = :channelId ORDER BY entryId DESC LIMIT :limit")
    fun observeLatestFeedEntries(channelId: Long, limit: Int): Flow<List<FeedEntryEntity>>

    /**
     * V10: Super fast for ValueGridWidget which only needs one entry.
     */
    @Query("SELECT * FROM feed_entries WHERE channelId = :channelId ORDER BY entryId DESC LIMIT 1")
    fun observeLastEntry(channelId: Long): Flow<List<FeedEntryEntity>>

    /**
     * Inserts a list of entries into the database. Overwrites on conflict.
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FeedEntryEntity>)

    /**
     * Clears old entries for a channel before syncing fresh data.
     */
    @Query("DELETE FROM feed_entries WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)

    /**
     * Atomically inserts or updates entries for a channel.
     * We no longer delete before insert to preserve historical data not returned in the latest sync.
     */
    @Transaction
    suspend fun upsertFeed(entities: List<FeedEntryEntity>) {
        insertAll(entities)
    }

    /** Wipes all entries from all channels. */
    @Query("DELETE FROM feed_entries")
    suspend fun deleteAll()

    /** 
     * Cleanup old entries to prevent database bloating.
     * @param dateCutoff ISO 8601 date string (e.g. 2024-01-01T00:00:00Z)
     */
    @Query("DELETE FROM feed_entries WHERE createdAt < :dateCutoff")
    suspend fun deleteOldEntries(dateCutoff: String)
}

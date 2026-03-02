package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.*

/**
 * Tracks which alerts have already been fired to avoid duplicate notifications.
 *
 * For a given channel and field, we store the [lastFiredEntryId].
 * If the current latest [entryId] is same or smaller, we don't fire again.
 */
@Entity(
    tableName = "fired_alerts",
    primaryKeys = ["channelId", "fieldNumber"]
)
data class FiredAlertEntity(
    val channelId: Long,
    val fieldNumber: Int,
    val lastFiredEntryId: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FiredAlertDao {
    @Query("SELECT * FROM fired_alerts WHERE channelId = :channelId AND fieldNumber = :fieldNumber")
    suspend fun getFiredAlert(channelId: Long, fieldNumber: Int): FiredAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiredAlert(firedAlert: FiredAlertEntity)

    @Query("DELETE FROM fired_alerts WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: Long)

    @Query("DELETE FROM fired_alerts")
    suspend fun deleteAll()
}

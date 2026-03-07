package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.*

/**
 * Tracks which alerts have already been fired to avoid duplicate notifications.
 *
 * For a given channel and field, we store the [lastFiredEntryId] and a [violationSignature]
 * that describes the set of active violations. When the signature changes (e.g. from
 * GREATER_THAN to LESS_THAN), it means a different type of violation occurred and we should
 * re-fire the alert.
 */
@Entity(
    tableName = "fired_alerts",
    primaryKeys = ["channelId", "fieldNumber"]
)
data class FiredAlertEntity(
    val channelId: Long,
    val fieldNumber: Int,
    val lastFiredEntryId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val lastFiredTimestamp: Long? = null,
    val violationSignature: String = ""
)

@Dao
interface FiredAlertDao {
    @Query("SELECT * FROM fired_alerts WHERE channelId = :channelId AND fieldNumber = :fieldNumber")
    suspend fun getFiredAlert(channelId: Long, fieldNumber: Int): FiredAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiredAlert(firedAlert: FiredAlertEntity)

    @Query("DELETE FROM fired_alerts WHERE channelId = :channelId AND fieldNumber = :fieldNumber")
    suspend fun deleteFiredAlert(channelId: Long, fieldNumber: Int)

    @Query("DELETE FROM fired_alerts WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: Long)

    @Query("DELETE FROM fired_alerts")
    suspend fun deleteAll()
}

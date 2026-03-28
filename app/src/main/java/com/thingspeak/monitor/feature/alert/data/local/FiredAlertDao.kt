package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

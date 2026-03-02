package com.thingspeak.monitor.feature.alert.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE channelId = :channelId")
    fun observeAlertsForChannel(channelId: Long): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE channelId = :channelId")
    suspend fun getAlertsForChannel(channelId: Long): List<AlertEntity>

    @Query("SELECT * FROM alerts")
    fun observeAllAlerts(): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Delete
    suspend fun deleteAlert(alert: AlertEntity)

    @Query("DELETE FROM alerts WHERE channelId = :channelId AND fieldNumber = :fieldNumber")
    suspend fun deleteSpecificAlert(channelId: Long, fieldNumber: Int)

    @Query("DELETE FROM alerts WHERE channelId = :channelId")
    suspend fun deleteAlertsForChannel(channelId: Long)

    /** Wyciera wszystkie alerty. */
    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}

package com.thingspeak.monitor.feature.channel.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom alert rules.
 */
@Dao
interface AlertRuleDao {

    @Query("SELECT * FROM alert_rules")
    fun observeAllRules(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId")
    fun observeRulesForChannel(channelId: Long): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId")
    suspend fun getRulesForChannel(channelId: Long): List<AlertRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AlertRuleEntity)

    @Delete
    suspend fun deleteRule(rule: AlertRuleEntity)

    @Update
    suspend fun updateRule(rule: AlertRuleEntity)
}

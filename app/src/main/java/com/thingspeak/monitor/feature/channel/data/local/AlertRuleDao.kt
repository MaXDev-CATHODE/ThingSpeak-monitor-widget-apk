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

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId AND appWidgetId = :appWidgetId")
    fun observeRulesForWidget(channelId: Long, appWidgetId: Int): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId AND appWidgetId = :appWidgetId")
    suspend fun getRulesForWidget(channelId: Long, appWidgetId: Int): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId AND appWidgetId IS NULL")
    suspend fun getGlobalRulesForChannel(channelId: Long): List<AlertRuleEntity>

    @Query("SELECT * FROM alert_rules WHERE channelId = :channelId AND appWidgetId IS NULL")
    fun observeRulesForChannel(channelId: Long): Flow<List<AlertRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AlertRuleEntity)

    @Delete
    suspend fun deleteRule(rule: AlertRuleEntity)

    @Update
    suspend fun updateRule(rule: AlertRuleEntity)

    @Query("DELETE FROM alert_rules WHERE channelId = :channelId AND appWidgetId IS NULL")
    suspend fun deleteGlobalRulesForChannel(channelId: Long)
}

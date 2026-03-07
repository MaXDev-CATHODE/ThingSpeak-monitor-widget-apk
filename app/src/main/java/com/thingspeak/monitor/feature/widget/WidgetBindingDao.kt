package com.thingspeak.monitor.feature.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: WidgetBindingEntity)

    @Query("DELETE FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun deleteBinding(appWidgetId: Int)

    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    fun observeBinding(appWidgetId: Int): Flow<WidgetBindingEntity?>

    @Query("SELECT * FROM widget_bindings WHERE appWidgetId = :appWidgetId")
    suspend fun getBindingSync(appWidgetId: Int): WidgetBindingEntity?
}

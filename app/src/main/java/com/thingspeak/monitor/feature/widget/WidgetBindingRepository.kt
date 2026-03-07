package com.thingspeak.monitor.feature.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.thingspeak.monitor.core.di.WidgetBindingDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for persistent mapping of system AppWidgetId to ThingSpeak ChannelId.
 * This is the ultimate "Source of Truth" for widget configuration, bypassing
 * potential Glance State issues during widget creation.
 */
@Singleton
class WidgetBindingRepository @Inject constructor(
    private val widgetBindingDao: WidgetBindingDao
) {

    /**
     * Observe the channel ID bound to a specific appWidgetId.
     * Room's enableMultiInstanceInvalidation ensures this reacts to changes from App or Widget process.
     */
    fun observeChannelId(appWidgetId: Int): Flow<Long> {
        return widgetBindingDao.observeBinding(appWidgetId).map { entity ->
            val id = entity?.channelId ?: -1L
            android.util.Log.d("SNIPER_ID", "Repo lookup: appWidgetId=$appWidgetId -> channelId=$id")
            id
        }
    }

    /**
     * Persist the binding between appWidgetId and channelId in Room.
     */
    suspend fun saveBinding(appWidgetId: Int, channelId: Long) {
        android.util.Log.d("SNIPER_ID", "Repo SAVE: appWidgetId=$appWidgetId -> channelId=$channelId")
        try {
            widgetBindingDao.upsertBinding(WidgetBindingEntity(appWidgetId, channelId))
        } catch (e: Exception) {
            android.util.Log.e("SNIPER_ID", "Room exception on Save: widget=$appWidgetId", e)
        }
    }

    /**
     * Remove the binding when a widget is deleted.
     */
    suspend fun removeBinding(appWidgetId: Int) {
        android.util.Log.d("SNIPER_ID", "Repo DELETE: appWidgetId=$appWidgetId")
        widgetBindingDao.deleteBinding(appWidgetId)
    }

    /**
     * Get binding synchronously for actions.
     */
    suspend fun getBindingSync(appWidgetId: Int): Long {
        return widgetBindingDao.getBindingSync(appWidgetId)?.channelId ?: -1L
    }
}

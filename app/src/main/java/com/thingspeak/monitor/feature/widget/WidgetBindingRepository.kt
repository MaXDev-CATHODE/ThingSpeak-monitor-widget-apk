package com.thingspeak.monitor.feature.widget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
            android.util.Log.d(WIDGET_LOG_TAG, "Repo lookup: appWidgetId=$appWidgetId -> channelId=$id")
            id
        }
    }

    /**
     * Persist the binding between appWidgetId and channelId in Room.
     */
    suspend fun saveBinding(appWidgetId: Int, channelId: Long) {
        android.util.Log.d(WIDGET_LOG_TAG, "Repo SAVE: appWidgetId=$appWidgetId -> channelId=$channelId")
        try {
            widgetBindingDao.upsertBinding(WidgetBindingEntity(appWidgetId, channelId))
        } catch (e: Exception) {
            android.util.Log.e(WIDGET_LOG_TAG, "Room exception on Save: widget=$appWidgetId", e)
        }
    }

    /**
     * Remove the binding when a widget is deleted.
     */
    suspend fun removeBinding(appWidgetId: Int) {
        android.util.Log.d(WIDGET_LOG_TAG, "Repo DELETE: appWidgetId=$appWidgetId")
        widgetBindingDao.deleteBinding(appWidgetId)
    }

    suspend fun deleteOrphanedBindings(activeIds: Set<Int>) {
        widgetBindingDao.deleteOrphanedBindings(activeIds)
    }

    /**
     * Get binding synchronously for actions.
     */
    suspend fun getBindingSync(appWidgetId: Int): Long {
        return widgetBindingDao.getBindingSync(appWidgetId)?.channelId ?: -1L
    }
}

package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.thingspeak.monitor.feature.widget.WidgetBindingRepository

/**
 * Sniper-grade ID resolver to ensure consistent channel binding across all widget types and actions.
 */
object WidgetIdResolver {
    private const val TAG = "TS_DEBUG"
    private val PREF_CHANNEL_ID = longPreferencesKey("channel_id")

    /**
     * Resolves the channel ID synchronously (e.g. for Glance Actions).
     */
    suspend fun resolve(
        appWidgetId: Int,
        bindingRepo: WidgetBindingRepository,
        glancePrefs: Preferences
    ): Long {
        val boundId = bindingRepo.getBindingSync(appWidgetId)
        val prefId = glancePrefs[PREF_CHANNEL_ID] ?: -1L
        
        val effective = if (boundId > 0) boundId else prefId
        Log.d(TAG, "Sync Resolve for $appWidgetId: bound=$boundId, pref=$prefId -> effective=$effective")
        return effective
    }
}

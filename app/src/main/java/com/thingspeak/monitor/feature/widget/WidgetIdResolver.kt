package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.thingspeak.monitor.feature.widget.WidgetBindingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Sniper-grade ID resolver to ensure consistent channel binding across all widget types and actions.
 */
object WidgetIdResolver {
    private const val TAG = "SNIPER_ID"
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

    /**
     * Observes the effective channel ID as a Flow.
     */
    fun observe(
        appWidgetId: Int,
        bindingRepo: WidgetBindingRepository,
        glancePrefs: Preferences
    ): Flow<Long> {
        android.util.Log.v("AUDIT_V11", "Resolver [OBSERVE_START] appWidgetId=$appWidgetId")
        return bindingRepo.observeChannelId(appWidgetId).map { boundId ->
            val prefId = glancePrefs[PREF_CHANNEL_ID] ?: -1L
            val effective = if (boundId > 0) boundId else prefId
            
            if (effective <= 0L) {
                android.util.Log.e("AUDIT_V11", "Resolver [FAILED] No ID for $appWidgetId (bound=$boundId, pref=$prefId)")
            } else {
                android.util.Log.d("AUDIT_V11", "Resolver [OK] $appWidgetId: bound=$boundId, pref=$prefId -> effective=$effective")
            }
            effective
        }
    }
}

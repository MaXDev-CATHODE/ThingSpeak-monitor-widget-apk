package com.thingspeak.monitor.feature.widget

import android.content.Context
import com.thingspeak.monitor.core.worker.DataSyncWorker
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared logic for triggering background data synchronization from widgets.
 */
object WidgetSyncOrchestrator {
    // Throttle map to prevent loops when network is missing.
    // ConcurrentHashMap ensures thread safety during simultaneous access from multiple widgets.
    private val lastSyncAttemptMap = ConcurrentHashMap<Long, Long>()

    fun triggerSyncIfNeeded(
        context: Context, 
        channelId: Long, 
        entryPresent: Boolean, 
        lastSyncTime: Long, 
        isRefreshing: Boolean
    ) {
        val now = System.currentTimeMillis()
        val dataAgeMs = now - lastSyncTime
        val lastAttempt = lastSyncAttemptMap[channelId] ?: 0L
        val secondsSinceAttempt = (now - lastAttempt) / 1000
        
        // V11.2 - "100% Certainty":
        // For present data: 30s threshold (updated from 60s for better responsiveness).
        // For missing data: immediate attempt (if not throttled).
        val isStale = dataAgeMs > 30 * 1000L 
        val isMissing = !entryPresent
        
        // Safeguard: do not synchronize if refresh is in progress OR an attempt was made recently (< 30s).
        // 30s throttling is a safe compromise between "100% certainty" and battery drain in a loop.
        if ((isMissing || isStale) && !isRefreshing && secondsSinceAttempt > 30) {
            android.util.Log.i("WidgetSync", "Triggering proactive sync for $channelId. missing=$isMissing, stale=$isStale, last attempt=${secondsSinceAttempt}s ago")
            lastSyncAttemptMap[channelId] = now
            DataSyncWorker.runOnce(context)
        }
    }
}

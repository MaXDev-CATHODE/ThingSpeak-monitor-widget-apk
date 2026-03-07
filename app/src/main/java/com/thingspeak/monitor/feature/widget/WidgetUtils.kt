package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for the Glance widget.
 */
object WidgetUtils {
    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    private const val STALE_THRESHOLD_MS = 15 * 60 * 1000L // 15 minutes
    private const val ONE_HOUR_MS = 60 * 60 * 1000L

    fun parseIsoTime(iso: String): Long? {
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    fun isDataStale(createdAt: String, thresholdMs: Long = STALE_THRESHOLD_MS): Boolean {
        val timestamp = parseIsoTime(createdAt) ?: return true
        val elapsed = System.currentTimeMillis() - timestamp
        // Consider data stale if it's older than the sync interval plus a small buffer
        return elapsed > (thresholdMs + (2 * 60 * 1000L)) 
    }

    fun formatRelativeTime(context: Context, createdAt: String): String {
        val timestamp = parseIsoTime(createdAt) ?: return "—"
        return formatTime(context, timestamp)
    }

    fun formatTime(context: Context, timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        
        return when {
            diff < 0 -> context.getString(com.thingspeak.monitor.R.string.widget_time_just_now)
            diff < 60 * 1000L -> context.getString(com.thingspeak.monitor.R.string.widget_time_seconds_ago, diff / 1000)
            diff < 60 * 60 * 1000L -> context.getString(com.thingspeak.monitor.R.string.widget_time_minutes_ago, diff / (60 * 1000L))
            diff < 24 * 60 * 60 * 1000L -> context.getString(com.thingspeak.monitor.R.string.widget_time_hours_ago, diff / (60 * 60 * 1000L))
            else -> {
                val instant = Instant.ofEpochMilli(timestamp)
                val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.US)
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    }

    fun maskApiKey(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return "—"
        if (apiKey.length <= 4) return "**** "
        return "****" + apiKey.takeLast(4)
    }
}

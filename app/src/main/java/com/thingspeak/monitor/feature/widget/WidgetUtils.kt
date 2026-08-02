package com.thingspeak.monitor.feature.widget

import android.content.Context
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for the Glance widget.
 */
object WidgetUtils {

    fun parseIsoTime(iso: String): Long? {
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the entry with the newest [FeedEntry.createdAt] among entries whose
     * timestamp parses and is not in the future. Corrupt future timestamps
     * (e.g. year 2106 entries from the ThingSpeak API) are ignored.
     */
    fun selectLatestEntry(entries: List<FeedEntry>, now: Instant = Instant.now()): FeedEntry? {
        val nowMs = now.toEpochMilli()
        return entries
            .mapNotNull { entry ->
                parseIsoTime(entry.createdAt)?.let { timestamp -> entry to timestamp }
            }
            .filter { it.second <= nowMs }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Resolves the chart results count when pushing widget preferences.
     * Preserves the user's per-widget setting; only falls back to the channel
     * default when the widget has no value yet.
     */
    fun resolveChartResultsOnPush(existingChartResults: Int?, channelDefault: Int?): Int =
        existingChartResults ?: channelDefault ?: 60

    fun isDataStale(createdAt: String, thresholdMs: Long): Boolean {
        val timestamp = parseIsoTime(createdAt) ?: return true
        val elapsed = System.currentTimeMillis() - timestamp
        return elapsed > thresholdMs
    }

    /**
     * Formats an ISO 8601 string into a local HH:mm string.
     * @param isoDate e.g. "2024-01-01T12:00:00Z"
     * @param timezone Optional ThingSpeak timezone (e.g. "America/New_York")
     */
    fun formatTime(isoDate: String?, timezone: String? = null): String {
        if (isoDate == null) return "--:--"
        return try {
            val instant = Instant.parse(isoDate)
            val zoneId = getTimeZone(timezone)
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(zoneId)
            formatter.format(instant)
        } catch (e: Exception) {
            "--:--"
        }
    }

    /**
     * Formats an ISO 8601 string into a relative time string (e.g. "12:34 (5m ago)").
     */
    fun formatRelativeTime(isoDate: String?, timezone: String? = null): String {
        if (isoDate == null) return "Never"
        return try {
            val instant = Instant.parse(isoDate)
            val now = Instant.now()
            val diffSeconds = now.epochSecond - instant.epochSecond
            
            val zoneId = getTimeZone(timezone)
            val absoluteTime = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId).format(instant)

            when {
                diffSeconds < 0 -> absoluteTime
                diffSeconds < 60 -> "$absoluteTime (<1m)"
                diffSeconds < 3600 -> {
                    val mins = diffSeconds / 60
                    "$absoluteTime (${mins}m)"
                }
                diffSeconds < 86400 -> {
                    val hours = diffSeconds / 3600
                    "$absoluteTime (${hours}h)"
                }
                else -> {
                    val days = diffSeconds / 86400
                    "$absoluteTime (${days}d)"
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getTimeZone(timezone: String?): ZoneId {
        if (timezone.isNullOrBlank()) return ZoneId.systemDefault()
        return try {
            ZoneId.of(timezone)
        } catch (e: Exception) {
            // Handle cases like "GMT-05:00" which might need a different format or just work with ZoneId
            try {
                ZoneId.of(timezone.replace(" ", ""))
            } catch (e2: Exception) {
                ZoneId.systemDefault()
            }
        }
    }

    fun maskApiKey(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return "—"
        if (apiKey.length <= 4) return "**** "
        return "****" + apiKey.takeLast(4)
    }
}

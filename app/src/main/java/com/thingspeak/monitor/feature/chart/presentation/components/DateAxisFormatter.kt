package com.thingspeak.monitor.feature.chart.presentation.components

import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatter for X-Axis to display dates instead of raw Float values.
 * Dynamically adjusts the time format based on the visible zoom level:
 * - Zoomed out (>2 days): "MMM dd"
 * - Normal daily view: "HH:mm"
 * - Zoomed in (<1h visible): "HH:mm:ss"
 */
class DateAxisFormatter(
    var isDailyResource: Boolean = true,
    var baselineX: Long = 0L,
    var timeScale: Float = 1f,
    var chart: com.github.mikephil.charting.charts.LineChart? = null
) : ValueFormatter() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val timeSecondsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault())

    // Cache to prevent GC thrashing and CPU overload during pan/zoom
    private val formatCache = android.util.LruCache<Long, String>(250)
    private var lastFormatMode: Int = -1

    override fun getFormattedValue(value: Float): String {
        return try {
            val seconds = (value * timeScale).toLong() + baselineX
            
            // Determine visible range in seconds
            val visibleSeconds = chart?.let { it.visibleXRange * timeScale } ?: Float.MAX_VALUE
            
            // Choose format mode based on zoom level
            val formatMode = when {
                visibleSeconds <= 1800  -> 0       // <30min: show HH:mm:ss
                visibleSeconds <= 2 * 86400 -> 1   // <2d: show HH:mm
                visibleSeconds <= 7 * 86400 -> 3   // <7d: show "MMM dd HH:mm"
                else -> 2                          // >7d: show "MMM dd"
            }

            // Clear cache if format mode changed
            if (lastFormatMode != formatMode) {
                formatCache.evictAll()
                lastFormatMode = formatMode
            }
            
            val cachedValue = formatCache.get(seconds)
            if (cachedValue != null) return cachedValue

            val instant = Instant.ofEpochSecond(seconds)
            
            val result = when (formatMode) {
                0 -> timeSecondsFormatter.format(instant)     // HH:mm:ss
                1 -> timeFormatter.format(instant)            // HH:mm
                2 -> dateFormatter.format(instant)            // MMM dd
                3 -> dateTimeFormatter.format(instant)        // MMM dd HH:mm
                else -> timeFormatter.format(instant)
            }
            
            formatCache.put(seconds, result)
            return result
        } catch (e: Exception) {
            value.toString()
        }
    }
}

package com.thingspeak.monitor.feature.chart.presentation.components

import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatter for X-Axis to display dates instead of raw Float values.
 * Standardized for Epoch-offset (Delta Seconds) input (Agent 3.7.5).
 */
class DateAxisFormatter(
    var isDailyResource: Boolean = true,
    var baselineX: Long = 0L,
    var timeScale: Float = 1f, // Standardized to 1.0f (seconds)
    var chart: com.github.mikephil.charting.charts.BarLineChartBase<*>? = null,
    var sampleTimestamps: List<Long> = emptyList() 
) : ValueFormatter() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val timeSecondsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

    // Cache to prevent GC thrashing
    private val formatCache = android.util.LruCache<Long, String>(250)
    private var lastFormatMode: Int = -1

    override fun getFormattedValue(value: Float): String {
        return try {
            // SYSTEM X = TIME DELTA (Agent 3.7.1 Absolute Fix)
            val seconds = value.toLong() + baselineX
            
            // Determine visible range in seconds to choose format
            val visibleSeconds = chart?.let { (it.visibleXRange).toLong() } 
                ?: if (isDailyResource) 86400L else 7 * 86400L
            
            val formatMode = when {
                visibleSeconds <= 300       -> 0   // <5min: show HH:mm:ss
                visibleSeconds <= 86400     -> 1   // <24h: show HH:mm
                visibleSeconds <= 3 * 86400 -> 3   // <3d: show dd.MM HH:mm
                else                        -> 2   // >3d: show dd.MM
            }

            if (lastFormatMode != formatMode) {
                formatCache.evictAll()
                lastFormatMode = formatMode
            }
            
            val cachedValue = formatCache.get(seconds)
            if (cachedValue != null) return cachedValue

            val instant = Instant.ofEpochSecond(seconds)
            val result = when (formatMode) {
                0 -> timeSecondsFormatter.format(instant)
                1 -> timeFormatter.format(instant)
                2 -> dateFormatter.format(instant)
                3 -> dateTimeFormatter.format(instant)
                else -> timeFormatter.format(instant)
            }
            
            formatCache.put(seconds, result)
            return result
        } catch (e: Exception) {
            value.toString()
        }
    }
}

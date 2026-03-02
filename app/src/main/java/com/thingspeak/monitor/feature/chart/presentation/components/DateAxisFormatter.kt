package com.thingspeak.monitor.feature.chart.presentation.components

import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatter for X-Axis to display dates instead of raw Float values.
 *
 * @param isDailyResource if true, formats as Time (HH:mm), else Date (MMM dd).
 */
class DateAxisFormatter(
    var isDailyResource: Boolean = true,
    var baselineX: Long = 0L,
    var timeScale: Float = 1f,
    var chart: com.github.mikephil.charting.charts.LineChart? = null
) : ValueFormatter() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM").withZone(ZoneId.systemDefault())

    override fun getFormattedValue(value: Float): String {
        return try {
            // Re-add baseline to the offset value to get the actual timestamp
            val seconds = (value * timeScale).toLong() + baselineX
            
            // SNAPPING LOGIC: Round to nearest logical interval
            // 1h range -> snap to 10m (600s)
            // 24h range -> snap to 30m (1800s)
            // Longer -> snap to day
            
            val visibleSeconds = chart?.let { it.visibleXRange * timeScale } ?: Float.MAX_VALUE
            val isEffectivelyDaily = if (chart != null) visibleSeconds <= 24 * 3600 else isDailyResource
            
            val snapInterval = if (isEffectivelyDaily) {
                if (seconds % 3600 == 0L) 3600L // Already on hour
                else 600L // snap to 10 min
            } else {
                3600L // snap to hour for date ranges
            }

            val roundedSeconds = (seconds + (snapInterval / 2)) / snapInterval * snapInterval
            val instant = Instant.ofEpochSecond(roundedSeconds)
            
            if (isEffectivelyDaily) {
                timeFormatter.format(instant)
            } else {
                dateFormatter.format(instant)
            }
        } catch (e: Exception) {
            value.toString()
        }
    }
}

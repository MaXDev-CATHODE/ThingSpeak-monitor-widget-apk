package com.thingspeak.monitor.core.utils

import com.github.mikephil.charting.data.Entry

/**
 * Utility class for mathematical operations on chart data.
 */
object MathUtils {

    /**
     * Applies a simple moving average to a list of chart entries.
     * 
     * @param data The original list of entries.
     * @param windowSize The size of the moving window.
     * @return A smoothed list of entries.
     */
    fun applyMovingAverage(data: List<Entry>, windowSize: Int): List<Entry> {
        if (data.size < windowSize || windowSize <= 1) return data

        return data.indices.map { i ->
            if (i < windowSize - 1) {
                // Not enough data for a full window yet
                data[i]
            } else {
                val window = data.subList(i - windowSize + 1, i + 1)
                val avgY = window.map { it.y }.average().toFloat()
                Entry(data[i].x, avgY)
            }
        }
    }
}

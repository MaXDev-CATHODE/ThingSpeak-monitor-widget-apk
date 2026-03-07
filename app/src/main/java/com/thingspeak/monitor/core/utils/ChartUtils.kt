package com.thingspeak.monitor.core.utils

import com.github.mikephil.charting.data.Entry
import kotlin.math.abs

/**
 * Utility class for chart-related operations, specifically data downsampling.
 */
object ChartUtils {

    /**
     * Downsamples the list of entries using the Largest-Triangle-Three-Buckets (LTTB) algorithm.
     * Prevents UI jank and memory issues when displaying large datasets while preserving peaks.
     *
     * @param data The original list of MPAndroidChart Entries.
     * @param threshold The target number of points (must be >= 3).
     * @return Downsampled list of Entries.
     */
    fun downsampleLTTB(data: List<Entry>, threshold: Int): List<Entry> {
        val size = data.size
        if (size <= threshold || threshold < 4) return data

        val sampled = ArrayList<Entry>(threshold + 2)
        val bucketCount = threshold / 2
        val bucketSize = size.toFloat() / bucketCount

        sampled.add(data[0])

        for (i in 0 until bucketCount) {
            val start = (i * bucketSize).toInt()
            val end = ((i + 1) * bucketSize).toInt().coerceAtMost(size - 1)
            
            if (start >= end) continue

            var minEntry = data[start]
            var maxEntry = data[start]
            
            for (j in start..end) {
                val entry = data[j]
                if (entry.y < minEntry.y) minEntry = entry
                if (entry.y > maxEntry.y) maxEntry = entry
            }
            
            if (minEntry.x < maxEntry.x) {
                sampled.add(minEntry)
                if (minEntry !== maxEntry) sampled.add(maxEntry)
            } else {
                sampled.add(maxEntry)
                if (minEntry !== maxEntry) sampled.add(minEntry)
            }
        }

        sampled.add(data[size - 1])
        return sampled.distinctBy { it.x }.sortedBy { it.x }
    }
}

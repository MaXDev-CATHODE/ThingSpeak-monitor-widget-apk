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
        if (size <= threshold || threshold < 3) return data

        val sampled = mutableListOf<Entry>()
        val bucketSize = (size - 2).toDouble() / (threshold - 2)

        // Always keep the first point
        var a = 0
        sampled.add(data[a])

        for (i in 0 until threshold - 2) {
            // Calculate range for current bucket
            val rangeStart = (i * bucketSize).toInt() + 1
            val rangeEnd = ((i + 1) * bucketSize).toInt() + 1

            // Calculate range for next bucket (to find average point)
            val nextRangeStart = ((i + 1) * bucketSize).toInt() + 1
            val nextRangeEnd = ((i + 2) * bucketSize).toInt() + 1
            
            // Calculate average point of the next bucket
            var avgX = 0f
            var avgY = 0f
            var avgCount = 0
            val limit = if (nextRangeEnd < size) nextRangeEnd else size
            for (j in nextRangeStart until limit) {
                avgX += data[j].x
                avgY += data[j].y
                avgCount++
            }
            if (avgCount > 0) {
                avgX /= avgCount
                avgY /= avgCount
            } else {
                // If next bucket is empty (end of list), use the last point
                avgX = data[size - 1].x
                avgY = data[size - 1].y
            }

            // Find point in current bucket that forms the largest triangle area
            var maxArea = -1.0
            var nextA = rangeStart
            val pA = data[a]

            val currentLimit = if (rangeEnd < size) rangeEnd else size
            for (j in rangeStart until currentLimit) {
                val pB = data[j]
                // Area of triangle calculation (cross product)
                val area = abs(
                    (pA.x.toDouble() - avgX) * (pB.y.toDouble() - pA.y.toDouble()) -
                    (pA.x.toDouble() - pB.x.toDouble()) * (avgY - pA.y.toDouble())
                ) * 0.5

                if (area > maxArea) {
                    maxArea = area
                    nextA = j
                }
            }

            sampled.add(data[nextA])
            a = nextA // Selected point becomes point A for next calculation
        }

        // Always keep the last point
        sampled.add(data[size - 1])

        return sampled
    }
}

package com.thingspeak.monitor.feature.widget

import android.graphics.*
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

/**
 * Generates a simplified line chart bitmap for Glance widgets.
 * Designed to run in background workers without View context.
 */
object WidgetChartGenerator {

    /**
     * Resolves the Y-axis scale (minVal, maxVal) for a single series.
     *
     * When [isNormalized] is true (per-series scaling), each series is scaled
     * independently to its own data range, so all trends are equally visible
     * regardless of absolute value differences between sensors.
     *
     * When [isNormalized] is false (global scaling), [globalMin]/[globalMax]
     * are used – all series share the same axis, which compresses series with
     * smaller absolute values.
     *
     * Extracted as an internal function to allow unit testing without Android
     * Bitmap/Canvas dependencies.
     */
    internal fun resolveSeriesScale(
        dataPoints: List<Double>,
        isNormalized: Boolean,
        globalMin: Double,
        globalMax: Double
    ): Pair<Double, Double> {
        val minVal = if (isNormalized) (dataPoints.minOrNull() ?: 0.0) else globalMin
        val maxVal = if (isNormalized) (dataPoints.maxOrNull() ?: 1.0) else globalMax
        return Pair(minVal, maxVal)
    }

    /**
     * Computes the global Y-axis range across all requested field series.
     * Used only when [isNormalized] = false.
     *
     * Extracted as an internal function to allow unit testing without Android
     * Bitmap/Canvas dependencies.
     */
    internal fun computeGlobalRange(
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>
    ): Pair<Double, Double> {
        var globalMin = Double.MAX_VALUE
        var globalMax = -Double.MAX_VALUE
        for (entry in entries) {
            fieldIndices.forEach { fieldIdx ->
                val v = entry.fields[fieldIdx]?.toDoubleOrNull()
                if (v != null) {
                    if (v < globalMin) globalMin = v
                    if (v > globalMax) globalMax = v
                }
            }
        }
        if (globalMin == Double.MAX_VALUE) globalMin = 0.0
        if (globalMax == -Double.MAX_VALUE) globalMax = 1.0
        // Add slight padding to Y range to avoid lines touching edges
        val padding = (globalMax - globalMin) * 0.05
        return Pair(globalMin - padding, globalMax + padding)
    }

    fun generateSimpleChart(
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>,
        isNormalized: Boolean = true,
        width: Int = 400,
        height: Int = 150,
        bgColor: Int = Color.TRANSPARENT,
        fieldColorsOverride: Map<Int, String>? = null
    ): Bitmap? {
        if (entries.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        if (bgColor != Color.TRANSPARENT) {
            canvas.drawColor(bgColor)
        }

        val defaultFieldColors = listOf(
            "#4CAF50", "#2196F3", "#F44336", "#FFEB3B",
            "#9C27B0", "#FF9800", "#00BCD4", "#E91E63"
        )

        val (globalMin, globalMax) = if (!isNormalized) {
            computeGlobalRange(entries, fieldIndices)
        } else {
            Pair(0.0, 1.0) // unused when isNormalized=true
        }

        fieldIndices.sorted().forEach { fieldIdx ->
            val colorStr = fieldColorsOverride?.get(fieldIdx) 
                ?: defaultFieldColors.getOrElse(fieldIdx - 1) { "#808080" }
            val rawDataPoints = entries.mapNotNull { it.fields[fieldIdx]?.toDoubleOrNull() }
            if (rawDataPoints.size < 2) return@forEach

            // Downsample to max 100 points for widget performance
            val dataPoints = if (rawDataPoints.size > 100) {
                val step = rawDataPoints.size.toDouble() / 100
                (0 until 100).map { rawDataPoints[(it * step).toInt()] }
            } else {
                rawDataPoints
            }

            val (minVal, maxVal) = resolveSeriesScale(dataPoints, isNormalized, globalMin, globalMax)
            val range = (maxVal - minVal).coerceAtLeast(0.1)

            val parsedColor = try {
                Color.parseColor(colorStr)
            } catch (e: Exception) {
                android.util.Log.w("TS_DEBUG", "WidgetChartGenerator: Invalid color '$colorStr', using gray", e)
                Color.GRAY
            }
            val paint = Paint().apply {
                color = parsedColor
                strokeWidth = 3f
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = Path()
            val stepX = width.toFloat() / (dataPoints.size - 1)
            
            dataPoints.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - ((value - minVal) / range * height).toFloat()
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }
}

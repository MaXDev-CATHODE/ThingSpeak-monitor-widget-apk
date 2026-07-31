package com.thingspeak.monitor.feature.widget

import android.graphics.*
import android.content.Context
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

    internal fun generateSimpleChart(
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>,
        isNormalized: Boolean = true,
        width: Int = 480,
        height: Int = 200,
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
                android.util.Log.w(TAG, "Invalid color '$colorStr', using gray", e)
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
            val yPad = 3f // prevent stroke clipping at canvas edges
            val drawH = height - yPad * 2
            
            dataPoints.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yPad + drawH - ((value - minVal) / range * drawH).toFloat()
                
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

    /**
     * Density‑aware convenience overload.
     *
     * Computes the output bitmap dimensions from screen density so the chart
     * fills the widget proportionally on every device instead of using a
     * fixed 480 × 200 canvas.
     *
     * @param chartWidthDp  nominal chart width in dp (default 280 — a typical
     *                      3‑cell widget after subtracting header/fields).
     * @param chartHeightDp nominal chart height in dp (default 160).
     */
    fun generateSimpleChart(
        context: Context,
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>,
        isNormalized: Boolean = true,
        chartWidthDp: Int = 280,
        chartHeightDp: Int = 160,
        bgColor: Int = Color.TRANSPARENT,
        fieldColorsOverride: Map<Int, String>? = null
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        return generateSimpleChart(
            entries = entries,
            fieldIndices = fieldIndices,
            isNormalized = isNormalized,
            width = (chartWidthDp * density).toInt(),
            height = (chartHeightDp * density).toInt(),
            bgColor = bgColor,
            fieldColorsOverride = fieldColorsOverride
        )
    }

    /**
     * One‑shot helper: generate a density‑scaled chart and return it as a
     * Base64‑encoded PNG string, or null. Eliminates duplicated
     * bitmap‑to‑Base64 code in DataSyncWorker and DataSyncService.
     */
    internal fun generateChartBase64(
        context: Context,
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>,
        isNormalized: Boolean = true,
        chartWidthDp: Int = 280,
        chartHeightDp: Int = 160,
        fieldColorsOverride: Map<Int, String>? = null
    ): String? {
        val bitmap = generateSimpleChart(
            context = context,
            entries = entries,
            fieldIndices = fieldIndices,
            isNormalized = isNormalized,
            chartWidthDp = chartWidthDp,
            chartHeightDp = chartHeightDp,
            fieldColorsOverride = fieldColorsOverride
        ) ?: return null
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            bitmap.recycle()
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            bitmap.recycle()
            android.util.Log.w(TAG, "generateChartBase64: Base64 encoding failed", e)
            null
        }
    }

    private const val TAG = "WidgetChartGenerator"
}

package com.thingspeak.monitor.feature.widget

import android.graphics.*
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

/**
 * Generates a simplified line chart bitmap for Glance widgets.
 * Designed to run in background workers without View context.
 */
object WidgetChartGenerator {

    fun generateSimpleChart(
        entries: List<FeedEntry>,
        fieldIndices: Set<Int>,
        isNormalized: Boolean = false,
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
        
        var globalMin = Double.MAX_VALUE
        var globalMax = -Double.MAX_VALUE
        if (!isNormalized) {
            for (entry in entries) {
                // Only consider fields that are actually being drawn
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
            globalMin -= padding
            globalMax += padding
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

            val minVal = if (isNormalized) (dataPoints.minOrNull() ?: 0.0) else globalMin
            val maxVal = if (isNormalized) (dataPoints.maxOrNull() ?: 1.0) else globalMax
            
            val range = (maxVal - minVal).coerceAtLeast(0.1)

            val paint = Paint().apply {
                color = Color.parseColor(colorStr)
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

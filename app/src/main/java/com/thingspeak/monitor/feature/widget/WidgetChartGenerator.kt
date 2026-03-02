package com.thingspeak.monitor.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Utility for generating simple line chart bitmaps for Glance widgets.
 * Standard MPAndroidChart is not usable directly in Glance UI.
 */
object WidgetChartGenerator {

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    private val colors = listOf(
        0xFF2196F3.toInt(), // Blue for field 1
        0xFFF44336.toInt(), // Red for field 2
        0xFF4CAF50.toInt(), // Green for field 3
        0xFFFF9800.toInt(), // Orange for field 4
        0xFF9C27B0.toInt(), // Purple for field 5
        0xFF00BCD4.toInt(), // Cyan for field 6
        0xFFFFEB3B.toInt(), // Yellow for field 7
        0xFF795548.toInt()  // Brown for field 8
    )

    /**
     * Generates a line chart bitmap with memory safety guards.
     */
    fun generateLineChart(
        dataMap: Map<Int, List<Pair<Long, Double>>>,
        width: Int = 400,
        height: Int = 120,
        strokeWidth: Float = 3f
    ): Bitmap? {
        if (dataMap.isEmpty() || dataMap.values.all { it.size < 2 }) return null

        // Memory Shield: Cap dimensions to avoid TransactionTooLargeException
        val safeWidth = width.coerceAtMost(800)
        val safeHeight = height.coerceAtMost(300)

        return try {
            val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            var minTime = Long.MAX_VALUE
            var maxTime = Long.MIN_VALUE
            dataMap.values.forEach { list ->
                list.forEach { (time, _) ->
                    if (time < minTime) minTime = time
                    if (time > maxTime) maxTime = time
                }
            }
            if (minTime == Long.MAX_VALUE) return null
            val timeRange = (maxTime - minTime).let { if (it <= 0L) 1L else it }

            val yOffset = safeHeight * 0.1f

            // Draw simple grid (3 lines)
            gridPaint.color = 0x20888888.toInt()
            for (i in 0..2) {
                val gy = yOffset + (i * (safeHeight * 0.4f))
                canvas.drawLine(0f, gy, safeWidth.toFloat(), gy, gridPaint)
            }

            dataMap.forEach { (fieldNumber, values) ->
                if (values.size < 2) return@forEach

                // Normalization strictly per-field
                var minVal = values.minByOrNull { it.second }?.second ?: 0.0
                var maxVal = values.maxByOrNull { it.second }?.second ?: 1.0

                // Add padding if values are constant or very close to center the line
                if (maxVal - minVal < 0.001) {
                    val pad = if (maxVal == 0.0) 1.0 else Math.abs(maxVal * 0.1).coerceAtLeast(1.0)
                    minVal -= pad
                    maxVal += pad
                }

                val range = (maxVal - minVal).let { if (it <= 0.0) 1.0 else it }
                val yScale = (safeHeight * 0.8f) / range.toFloat() // Leave some margin

                val color = colors[(fieldNumber - 1) % colors.size]
                linePaint.color = color
                linePaint.strokeWidth = strokeWidth
                
                fillPaint.color = (color and 0x00FFFFFF) or 0x10000000 // Very subtle fill

                val path = Path()
                val fillPath = Path()

                values.forEachIndexed { index, (time, value) ->
                    val x = ((time - minTime).toFloat() / timeRange.toFloat()) * safeWidth
                    val y = safeHeight - ((value - minVal).toFloat() * yScale + yOffset)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, safeHeight.toFloat())
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    
                    if (index == values.size - 1) {
                        fillPath.lineTo(x, safeHeight.toFloat())
                        fillPath.close()
                    }
                }

                canvas.drawPath(fillPath, fillPaint)
                canvas.drawPath(path, linePaint)
            }

            bitmap
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

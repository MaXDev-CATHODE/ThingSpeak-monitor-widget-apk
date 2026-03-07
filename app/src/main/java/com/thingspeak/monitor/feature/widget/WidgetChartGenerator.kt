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
        // Added for improved visibility as per request
        this.color = 0 // Will be set in loop
        strokeWidth = 5f
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
        safeWidth: Int = 300,
        safeHeight: Int = 150,
        timeRangeMs: Long? = null,
        referenceNow: Long = System.currentTimeMillis()
    ): Bitmap? {
        if (dataMap.isEmpty()) {
            android.util.Log.w("WidgetChartGenerator", "generateLineChart: dataMap is empty")
            return null
        }
        
        val totalPoints = dataMap.values.sumOf { it.size }
        android.util.Log.d("WidgetChartGenerator", "generateLineChart: Fields=${dataMap.size}, Total points=$totalPoints")

        if (totalPoints < 1) return null

        // Memory Shield: Cap dimensions to avoid TransactionTooLargeException
        // Dimensions are now passed as safeWidth/safeHeight directly.
        // val safeWidth = width.coerceAtMost(800)
        // val safeHeight = height.coerceAtMost(300)

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
            
            // Reference time for the right edge of the chart (always 'now' if range is fixed)
            // referenceNow is passed from caller to ensure consistency with data filtering
            
            // Allow fixing the time range so charts span the requested time window even if data is sparse
            // The right edge (actualMaxTime) should be 'now' to show gaps if data is stale
            val actualMaxTime = if (timeRangeMs != null && timeRangeMs > 0) referenceNow else maxTime
            val actualMinTime = if (timeRangeMs != null && timeRangeMs > 0) {
                // Determine min time based on required range relative to referenceNow
                referenceNow - timeRangeMs
            } else {
                minTime
            }

            // Ensure we don't have a negative or zero range
            val effectiveMax = Math.max(actualMaxTime, maxTime) 
            val timeRange = (effectiveMax - actualMinTime).let { if (it <= 0L) 1L else it }

            val yOffset = safeHeight * 0.1f

            // Draw grid (3 horizontal lines + vertical markers for intervals)
            gridPaint.color = 0x20888888.toInt()
            for (i in 0..2) {
                val gy = yOffset + (i * (safeHeight * 0.4f))
                canvas.drawLine(0f, gy, safeWidth.toFloat(), gy, gridPaint)
            }
            
            // Draw vertical markers (e.g. 5 markers)
            for (i in 0..4) {
                val gx = (i / 4f) * safeWidth
                canvas.drawLine(gx, 0f, gx, safeHeight.toFloat(), gridPaint)
            }

            // Paint for data markers (points)
            val markerPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            dataMap.forEach { (fieldNumber, values) ->
                if (values.size < 1) return@forEach

                // Normalization strictly per-field
                var minVal = values.minByOrNull { it.second }?.second ?: 0.0
                var maxVal = values.maxByOrNull { it.second }?.second ?: 1.0

                // Add padding ONLY if values are exactly the same or variation is extremely small
                if (maxVal - minVal < 1e-12) {
                    val pad = if (maxVal == 0.0) 1.0 else Math.abs(maxVal * 0.1).coerceAtLeast(0.1)
                    minVal -= pad
                    maxVal += pad
                }
 
                val range = (maxVal - minVal).let { if (it <= 0.0) 1.0 else it }
                val yScale = (safeHeight * 0.8f) / range.toFloat() // Leave some margin
 
                val color = colors[(fieldNumber - 1) % colors.size]
                linePaint.color = color
                linePaint.strokeWidth = 4f // Slightly thicker for clarity
                markerPaint.color = color
                
                fillPaint.color = (color and 0x00FFFFFF) or 0x10000000 // Subtle fill

                val path = Path()
                val fillPath = Path()

                val sortedValues = values.sortedBy { it.first }

                sortedValues.forEachIndexed { index, (time, value) ->
                    val x = (((time - actualMinTime).toFloat() / timeRange.toFloat()) * safeWidth).coerceIn(0f, safeWidth.toFloat())
                    val y = safeHeight - ((value - minVal).toFloat() * yScale + yOffset)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, safeHeight.toFloat())
                        fillPath.lineTo(x, y)
                    } else {
                        // LINEAR path - as requested for clarity
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    
                    // Draw marker (point) - larger for visibility
                    canvas.drawCircle(x, y, 9f, markerPaint)
                    if (index == 0) android.util.Log.v("WidgetChartGenerator", "Field $fieldNumber first point: ($x, $y)")

                    if (index == sortedValues.size - 1) {
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

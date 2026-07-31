package com.thingspeak.monitor.feature.widget

import android.graphics.*
import android.content.Context
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

object WidgetChartGenerator {

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

        val seriesWithData = fieldIndices.filter { idx ->
            entries.count { it.fields[idx]?.toDoubleOrNull() != null } >= 2
        }
        if (seriesWithData.isEmpty()) return null

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
            Pair(0.0, 1.0)
        }

        seriesWithData.sorted().forEach { fieldIdx ->
            val colorStr = fieldColorsOverride?.get(fieldIdx)
                ?: defaultFieldColors.getOrElse(fieldIdx - 1) { "#808080" }
            val rawDataPoints = entries.mapNotNull { it.fields[fieldIdx]?.toDoubleOrNull() }
            if (rawDataPoints.size < 2) return@forEach

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
            val yPad = 3f
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

    /**
     * Generates a chart bitmap for a channel's feed entries and saves it directly to file cache.
     * Returns the file path, or null on failure. Shared by DataSyncWorker, DataSyncService,
     * and ThingSpeakGlanceWidget.updateAppWidget.
     */
    fun generateAndSaveChart(
        context: Context,
        channel: SavedChannel,
        entries: List<FeedEntry>,
        appWidgetId: Int
    ): String? {
        if (entries.isEmpty()) return null

        val fieldIndices = channel.preferredChartFields?.ifEmpty { null }
            ?: channel.widgetVisibleFields?.ifEmpty { null }
            ?: setOf(1)

        val bitmap = try {
            generateSimpleChart(
                context = context,
                entries = entries,
                fieldIndices = fieldIndices,
                isNormalized = channel.isNormalized,
                fieldColorsOverride = channel.fieldColors
            )
        } catch (e: Exception) {
            android.util.Log.w(WIDGET_LOG_TAG, "generateAndSaveChart: chart gen failed for ${channel.id}", e)
            return null
        } ?: return null

        return try {
            WidgetChartCache.save(context, appWidgetId, bitmap).also {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            bitmap.recycle()
            android.util.Log.w(WIDGET_LOG_TAG, "generateAndSaveChart: cache save failed for ${channel.id}", e)
            null
        }
    }

    private const val TAG = "WidgetChartGenerator"
}
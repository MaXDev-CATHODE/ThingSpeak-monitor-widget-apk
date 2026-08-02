package com.thingspeak.monitor.feature.chart.presentation

import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle
import java.time.Instant

/**
 * Pure Kotlin logic for processing ThingSpeak feeds into Chart bundles.
 * Optimized with Epoch-based X axis for proportional time representation (Agent 3.7.9).
 */
object ChartDataProcessor {

    fun processFeedsToBundles(
        feeds: List<FeedEntry>,
        currentRangeDays: Int,
        selectedFields: Set<Int>,
        isMergingEnabled: Boolean,
        isNormalized: Boolean,
        fieldNames: Map<Int, String>,
        chartColor: String? = null,
        fieldColorsMap: Map<Int, String> = emptyMap(),
        now: java.time.Instant = java.time.Instant.now(),
        resultsLimit: Int = 60,
        baselineXOverride: Long? = null,
        timezone: String? = null,
        processingType: String = "NONE",
        isBarHorizontal: Boolean = false,
        drawingStyle: LineDrawingStyle = LineDrawingStyle.CUBIC
    ): List<ChartDataBundle> {
        if (feeds.isEmpty()) return emptyList()

        val activeFields = selectedFields.ifEmpty { feeds.first().fields.keys.toSet() }.sorted()
        
        // 1. Prepare sorted feed context with logging
        val feedByTimestamp = feeds.mapNotNull { feed ->
            try { Instant.parse(feed.createdAt).epochSecond to feed } catch (e: Exception) { 
                android.util.Log.w(com.thingspeak.monitor.core.utils.APP_LOG_TAG, "Failed to parse timestamp: ${feed.createdAt}")
                null 
            }
        }
            // Ignore corrupt future timestamps (e.g. year 2106 entries from ThingSpeak API)
            // that would otherwise stretch the axis and empty the chart.
            .filter { it.first <= now.epochSecond }
            .sortedBy { it.first }

        if (feedByTimestamp.isEmpty()) {
            android.util.Log.e(com.thingspeak.monitor.core.utils.APP_LOG_TAG, "No valid feeds found after timestamp parsing!")
            return emptyList()
        }

        val defaultColors = listOf(
            "#2196F3", "#F44336", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4", "#FFC107", "#E91E63"
        )
        
        fun getColorForField(fieldIndex: Int): String {
            return fieldColorsMap[fieldIndex]
                ?: chartColor
                ?: defaultColors.getOrElse(fieldIndex - 1) { "#888888" }
        }

        fun safeParseColor(colorStr: String): Int {
            return try {
                val cleaned = colorStr.trim().removePrefix("#")
                when {
                    cleaned.length == 3 -> {
                        // Expand shorthand #RGB to #RRGGBB
                        val r = cleaned[0]; val g = cleaned[1]; val b = cleaned[2]
                        android.graphics.Color.parseColor("#FF$r$r$g$g$b$b")
                    }
                    cleaned.length == 6 -> {
                        android.graphics.Color.parseColor("#FF$cleaned")
                    }
                    cleaned.length == 8 -> {
                        android.graphics.Color.parseColor("#$cleaned")
                    }
                    else -> android.graphics.Color.BLACK
                }
            } catch (e: Exception) {
                android.graphics.Color.BLACK
            }
        }

        // 2. Fixed Axis Range Calculation (MANDATORY for 1D, 7D, 30D)
        val latestFeedTime = feedByTimestamp.lastOrNull()?.first ?: now.epochSecond
        val endTime = maxOf(now.epochSecond, latestFeedTime)
        val startTime = endTime - (currentRangeDays.toLong() * 86400L)
        
        val baselineX = startTime 
        val timeScale = 1.0f 
        
        val minXDelta = 0.0f
        val maxXDelta = (endTime - startTime).toFloat()

        // 3. Optimized Time-Bucket Sampling
        val targetCount = if (resultsLimit <= 0) 60 else resultsLimit
        val bucketSizeSeconds = (endTime - startTime).toDouble() / targetCount
        
        val buckets = Array(targetCount) { mutableListOf<Pair<Long, FeedEntry>>() }
        for (item in feedByTimestamp) {
            val ts = item.first
            if (ts < startTime || ts >= endTime) continue
            val bucketIndex = ((ts - startTime) / bucketSizeSeconds).toInt().coerceIn(0, targetCount - 1)
            buckets[bucketIndex].add(item)
        }

        val processedContext = if (processingType.uppercase() == "NONE" && feedByTimestamp.size <= 1000) {
            feedByTimestamp
        } else {
            (0 until targetCount).mapNotNull { i ->
                val bucketFeeds = buckets[i]
                if (bucketFeeds.isNotEmpty()) {
                    val representativeFeed = bucketFeeds.last()
                    val actualTs = representativeFeed.first
                    
                    val processedFeed = FeedEntry(
                        createdAt = representativeFeed.second.createdAt,
                        fields = activeFields.associateWith { fieldIdx ->
                            val values = bucketFeeds.mapNotNull { it.second.fields[fieldIdx]?.toDoubleOrNull() }
                            if (values.isEmpty()) return@associateWith null
                            
                            val resultValue = when (processingType.uppercase()) {
                                "MAX" -> values.maxOrNull()
                                "MIN" -> values.minOrNull()
                                "SUM" -> values.sum()
                                else -> values.average() 
                            }
                            resultValue?.toString()
                        }.filterValues { it != null } as Map<Int, String>
                    )
                    actualTs to processedFeed
                } else null
            }
        }

        if (drawingStyle == LineDrawingStyle.BAR) {
            return processBarCharts(
                context = processedContext,
                activeFields = activeFields,
                isMergingEnabled = isMergingEnabled,
                isNormalized = isNormalized,
                fieldNames = fieldNames,
                baselineX = baselineX,
                timeScale = timeScale,
                minX = minXDelta,
                maxX = maxXDelta,
                isHorizontal = isBarHorizontal,
                timezone = timezone,
                colorProvider = { idx -> safeParseColor(getColorForField(idx)) }
            )
        }

        return processLineCharts(
            processedContext, activeFields,
            isMergingEnabled, isNormalized, fieldNames, baselineX,
            timeScale, minXDelta, maxXDelta, drawingStyle, timezone, { idx -> safeParseColor(getColorForField(idx)) }
        )
    }

    private fun processBarCharts(
        context: List<Pair<Long, FeedEntry>>,
        activeFields: List<Int>,
        isMergingEnabled: Boolean,
        isNormalized: Boolean,
        fieldNames: Map<Int, String>,
        baselineX: Long,
        timeScale: Float,
        minX: Float,
        maxX: Float,
        isHorizontal: Boolean,
        timezone: String?,
        colorProvider: (Int) -> Int
    ): List<ChartDataBundle> {
        if (context.isEmpty()) return emptyList()
        val timestamps = context.map { it.first }

        if (isMergingEnabled && activeFields.size > 1) {
            // Grouped bars require index-based X axis for stability
            val dataSets = activeFields.mapNotNull { fieldIndex ->
                val entries = context.mapIndexed { index, (ts, feed) ->
                    BarEntry(index.toFloat(), feed.fields[fieldIndex]?.toFloatOrNull() ?: 0f)
                }
                if (entries.isEmpty()) return@mapNotNull null
                
                val finalEntries = if (isNormalized) normalizeBarEntries(entries) else entries
                val set = BarDataSet(finalEntries, fieldNames[fieldIndex] ?: "Field $fieldIndex")
                set.color = colorProvider(fieldIndex)
                set.setDrawValues(false)
                set
            }
            if (dataSets.isEmpty()) return emptyList()

            val barData = BarData(dataSets)
            val groupCount = context.size
            val dataSetCount = dataSets.size
            
            // Standard MPAndroidChart grouping formula: (barWidth + barSpace) * dataSetCount + groupSpace = 1.00
            val barWidth = 0.25f
            val barSpace = 0.05f
            val groupSpace = 1.0f - (barWidth + barSpace) * dataSetCount
            
            barData.barWidth = barWidth
            barData.groupBars(0f, groupSpace.coerceAtLeast(0.01f), barSpace)

            // Adjust bounds for index-based axis
            val bundleMinX = 0f
            val bundleMaxX = groupCount.toFloat()

            return listOf(ChartDataBundle.Bar(
                title = "Merged Bars",
                barData = barData,
                baselineX = baselineX, // Not used for X value calculation but kept for metadata
                timeScale = 0f,        // 0f signals index-based formatting
                xAxisMin = bundleMinX,
                xAxisMax = bundleMaxX,
                isHorizontal = isHorizontal,
                sampleTimestamps = timestamps,
                timezone = timezone
            ))
        } else {
            // Single field or non-merged: stay with epoch-based for smooth scrolling
            val dynamicBarWidth = if (context.size > 1) {
                0.8f * (maxX - minX) / context.size
            } else 0.8f

            val padding = (dynamicBarWidth * 1.5f).coerceAtMost(300f) 
            val bundleMinX = minX - padding
            val bundleMaxX = maxX + padding

            return activeFields.mapNotNull { fieldIndex ->
                val entries = context.map { (ts, feed) ->
                    val xVal = (ts - baselineX).toFloat() / timeScale
                    BarEntry(xVal, feed.fields[fieldIndex]?.toFloatOrNull() ?: 0f)
                }
                if (entries.isEmpty()) return@mapNotNull null
                
                val finalEntries = if (isNormalized) normalizeBarEntries(entries) else entries
                val set = BarDataSet(finalEntries, fieldNames[fieldIndex] ?: "Field $fieldIndex")
                set.color = colorProvider(fieldIndex)
                set.setDrawValues(false)
                
                ChartDataBundle.Bar(
                    title = fieldNames[fieldIndex] ?: "Field $fieldIndex",
                    barData = BarData(set).apply { barWidth = dynamicBarWidth },
                    baselineX = baselineX,
                    timeScale = timeScale,
                    xAxisMin = bundleMinX,
                    xAxisMax = bundleMaxX,
                    isHorizontal = isHorizontal,
                    sampleTimestamps = timestamps,
                    timezone = timezone
                )
            }
        }
    }

    private fun processLineCharts(
        context: List<Pair<Long, FeedEntry>>,
        activeFields: List<Int>,
        isMergingEnabled: Boolean,
        isNormalized: Boolean,
        fieldNames: Map<Int, String>,
        baselineX: Long,
        timeScale: Float,
        minX: Float,
        maxX: Float,
        drawingStyle: LineDrawingStyle,
        timezone: String?,
        colorProvider: (Int) -> Int
    ): List<ChartDataBundle> {
        val timestamps = context.map { it.first }
        if (isMergingEnabled) {
            val dataSets = activeFields.mapNotNull { fieldIndex ->
                val entries = context.map { (ts, feed) ->
                    val xVal = (ts - baselineX).toFloat() / timeScale
                    Entry(xVal, feed.fields[fieldIndex]?.toFloatOrNull() ?: 0f)
                }
                if (entries.isEmpty()) return@mapNotNull null
                
                val finalEntries = if (isNormalized) normalizeEntries(entries) else entries
                val set = LineDataSet(finalEntries, fieldNames[fieldIndex] ?: "Field $fieldIndex")
                set.color = colorProvider(fieldIndex)
                set
            }
            if (dataSets.isEmpty()) return emptyList()
            return listOf(ChartDataBundle.Line("Merged View", LineData(dataSets), baselineX, timeScale, minX, maxX, drawingStyle, timestamps, timezone))
        } else {
            return activeFields.mapNotNull { fieldIndex ->
                val entries = context.map { (ts, feed) ->
                    val xVal = (ts - baselineX).toFloat() / timeScale
                    Entry(xVal, feed.fields[fieldIndex]?.toFloatOrNull() ?: 0f)
                }
                if (entries.isEmpty()) return@mapNotNull null
                
                val finalEntries = if (isNormalized) normalizeEntries(entries) else entries
                val set = LineDataSet(finalEntries, fieldNames[fieldIndex] ?: "Field $fieldIndex")
                set.color = colorProvider(fieldIndex)
                ChartDataBundle.Line(fieldNames[fieldIndex] ?: "Field $fieldIndex", LineData(set), baselineX, timeScale, minX, maxX, drawingStyle, timestamps, timezone)
            }
        }
    }

    private fun normalizeEntries(entries: List<Entry>): List<Entry> {
        val minY = entries.minOf { it.y }; val maxY = entries.maxOf { it.y }
        val range = maxY - minY
        return entries.map { e -> Entry(e.x, if (range != 0f) ((e.y - minY) / range) * 100f else 50f) }
    }

    private fun normalizeBarEntries(entries: List<BarEntry>): List<BarEntry> {
        val minY = entries.minOf { it.y }; val maxY = entries.maxOf { it.y }
        val range = maxY - minY
        return entries.map { e -> BarEntry(e.x, if (range != 0f) ((e.y - minY) / range) * 100f else 50f) }
    }
}

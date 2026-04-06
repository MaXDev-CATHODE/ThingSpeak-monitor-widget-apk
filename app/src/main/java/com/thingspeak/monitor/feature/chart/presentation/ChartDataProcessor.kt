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
        drawingStyle: LineDrawingStyle = LineDrawingStyle.CUBIC,
        chartColor: String? = null,
        fieldColorsMap: Map<Int, String> = emptyMap(),
        now: Instant = Instant.now(),
        resultsLimit: Int = 60,
        baselineXOverride: Long? = null,
        timezone: String? = null
    ): List<ChartDataBundle> {
        if (feeds.isEmpty()) return emptyList()

        val activeFields = selectedFields.ifEmpty { feeds.first().fields.keys.toSet() }.sorted()
        
        // 1. Prepare sorted feed context with logging
        val feedByTimestamp = feeds.mapNotNull { feed ->
            try { Instant.parse(feed.createdAt).epochSecond to feed } catch (e: Exception) { 
                android.util.Log.w("TS_DEBUG", "Failed to parse timestamp: ${feed.createdAt}")
                null 
            }
        }.sortedBy { it.first }

        if (feedByTimestamp.isEmpty()) {
            android.util.Log.e("TS_DEBUG", "No valid feeds found after timestamp parsing!")
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
                if (colorStr.startsWith("#")) {
                    val color = colorStr.substring(1).toLong(16)
                    if (colorStr.length == 7) (0xFF000000 or color).toInt() else color.toInt()
                } else {
                    0xFF000000.toInt() // Default to black
                }
            } catch (e: Exception) {
                0xFF000000.toInt()
            }
        }

        // 2. Fixed Axis Range Calculation (MANDATORY for 1D, 7D, 30D)
        val endTime = now.epochSecond
        val startTime = endTime - (currentRangeDays.toLong() * 86400L)
        
        // USE START_TIME as baselineX for PERFECT coordinate alignment (Agent 3.7.6)
        val baselineX = startTime 
        val timeScale = 1.0f // Always use seconds
        
        val minXDelta = 0.0f
        val maxXDelta = (endTime - startTime).toFloat()

        // 3. Optimized Time-Bucket Sampling O(N) - (Agent 3.7.9 PERFORMANCE)
        val targetCount = if (resultsLimit <= 0) 60 else resultsLimit
        val bucketSizeSeconds = (endTime - startTime).toDouble() / targetCount
        
        android.util.Log.d("TS_DEBUG", "Processing $currentRangeDays days for $targetCount buckets. Range: [$startTime - $endTime]. BucketSize: $bucketSizeSeconds s")

        // Pre-allocate buckets to avoid O(M*N) filter overhead
        val buckets = Array(targetCount) { mutableListOf<Pair<Long, FeedEntry>>() }
        for (item in feedByTimestamp) {
            val ts = item.first
            if (ts < startTime || ts >= endTime) continue
            val bucketIndex = ((ts - startTime) / bucketSizeSeconds).toInt().coerceIn(0, targetCount - 1)
            buckets[bucketIndex].add(item)
        }

        var dataBucketCount = 0
        var zeroBucketCount = 0

        val processedContext = List(targetCount) { i ->
            val bucketFeeds = buckets[i]
            val bStart = startTime + (i * bucketSizeSeconds).toLong()

            if (bucketFeeds.isNotEmpty()) {
                dataBucketCount++
                // Use the LATEST feed's timestamp in the bucket as the representative X coordinate
                // to match the visual point with the actual measurement time.
                val representativeFeed = bucketFeeds.last()
                val actualTs = representativeFeed.first
                
                val avgFeed = FeedEntry(
                    createdAt = representativeFeed.second.createdAt,
                    fields = activeFields.associateWith { fieldIdx ->
                        var sum = 0.0
                        var count = 0
                        for (bf in bucketFeeds) {
                            val v = bf.second.fields[fieldIdx]?.toDoubleOrNull()
                            if (v != null) {
                                sum += v
                                count++
                            }
                        }
                        if (count > 0) (sum / count).toString() else "0.0"
                    }
                )
                actualTs to avgFeed
            } else {
                zeroBucketCount++
                val middleTs = bStart + (bucketSizeSeconds / 2).toLong()
                val emptyFeed = FeedEntry(
                    createdAt = Instant.ofEpochSecond(middleTs).toString(),
                    fields = activeFields.associateWith { "0.0" }
                )
                middleTs to emptyFeed
            }
        }
        
        android.util.Log.i("TS_DEBUG", "Bucket stats: DataBuckets=$dataBucketCount, ZeroBuckets=$zeroBucketCount (Total=$targetCount)")
        if (dataBucketCount > 0) {
           val firstData = feedByTimestamp.first().first
           val lastData = feedByTimestamp.last().first
           android.util.Log.d("TS_DEBUG", "Data Range: $firstData to $lastData | Chart Range: $startTime to $endTime")
        }

        if (drawingStyle == LineDrawingStyle.BAR) {
            return processBarCharts(
                processedContext, activeFields, 
                isMergingEnabled, isNormalized, fieldNames, baselineX, 
                timeScale, minXDelta, maxXDelta, timezone, { idx -> safeParseColor(getColorForField(idx)) }
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
        timezone: String?,
        colorProvider: (Int) -> Int
    ): List<ChartDataBundle> {
        val timestamps = context.map { it.first }
        // Agent 3.7.6: Calculate a stable bar width based on bucket size.
        val dynamicBarWidth = if (context.size > 1) {
            val totalTimeRange = maxX - minX
            val avgGap = totalTimeRange / (context.size)
            0.8f * avgGap 
        } else 0.8f

        val padding = (dynamicBarWidth * 1.5f).coerceAtMost(300f / timeScale) 
        val bundleMinX = minX - padding
        val bundleMaxX = maxX + padding

        if (isMergingEnabled) {
            val dataSets = activeFields.mapNotNull { fieldIndex ->
                val entries = context.map { (ts, feed) ->
                    val xVal = (ts - baselineX).toFloat() / timeScale
                    BarEntry(xVal, feed.fields[fieldIndex]?.toFloatOrNull() ?: 0f)
                }
                if (entries.isEmpty()) return@mapNotNull null
                
                val finalEntries = if (isNormalized) normalizeBarEntries(entries) else entries
                val set = BarDataSet(finalEntries, fieldNames[fieldIndex] ?: "Field $fieldIndex")
                set.color = colorProvider(fieldIndex)
                set.setDrawValues(false)
                set
            }
            if (dataSets.isEmpty()) return emptyList()
            return listOf(ChartDataBundle.Bar("Merged Bars", BarData(dataSets).apply { barWidth = dynamicBarWidth }, baselineX, timeScale, bundleMinX, bundleMaxX, timestamps, timezone))
        } else {
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
                ChartDataBundle.Bar(fieldNames[fieldIndex] ?: "Field $fieldIndex", BarData(set).apply { barWidth = dynamicBarWidth }, baselineX, timeScale, bundleMinX, bundleMaxX, timestamps, timezone)
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

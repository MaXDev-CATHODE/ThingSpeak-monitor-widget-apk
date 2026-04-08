package com.thingspeak.monitor.feature.chart.presentation

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChartDataProcessorTest {

    @Test
    fun `processFeedsToBundles correctly scales daily data`() {
        val now = Instant.now()
        val feeds = listOf(
            FeedEntry(createdAt = now.minusSeconds(3600).toString(), fields = mapOf(1 to "10.0")),
            FeedEntry(createdAt = now.toString(), fields = mapOf(1 to "20.0"))
        )
        
        val bundles = ChartDataProcessor.processFeedsToBundles(
            feeds = feeds,
            currentRangeDays = 1,
            selectedFields = setOf(1),
            isMergingEnabled = false,
            isNormalized = false,
            fieldNames = mapOf(1 to "Temp"),
            now = now
        )
        
        assertEquals(1, bundles.size)
        val bundle = bundles.first() as ChartDataBundle.Line
        assertEquals(2, bundle.lineData.entryCount)
        assertEquals(1f, bundle.timeScale)
        // Entry 0 should be at 86400 - 3600 = 82800f
        assertEquals(82800f, bundle.lineData.getDataSetByIndex(0).getEntryForIndex(0).x)
        // Entry 1 should be at 86400f
        assertEquals(86400f, bundle.lineData.getDataSetByIndex(0).getEntryForIndex(1).x)
    }

    @Test
    fun `processFeedsToBundles scales 30D data by minutes`() {
        val now = Instant.now()
        val feeds = listOf(
            FeedEntry(createdAt = now.minusSeconds(3600).toString(), fields = mapOf(1 to "10.0")),
            FeedEntry(createdAt = now.toString(), fields = mapOf(1 to "20.0"))
        )
        
        val bundles = ChartDataProcessor.processFeedsToBundles(
            feeds = feeds,
            currentRangeDays = 30,
            selectedFields = setOf(1),
            isMergingEnabled = false,
            isNormalized = false,
            fieldNames = mapOf(1 to "Temp"),
            now = now
        )
        
        val bundle = bundles.first() as ChartDataBundle.Line
        val entry1X = bundle.lineData.getDataSetByIndex(0).getEntryForIndex(1).x
        assertEquals(1f, bundle.timeScale)
        // Baseline is 30 days ago (2592000s). Entry 1 is 'now' (offset = 2592000s)
        assertEquals(2592000f, entry1X)
    }
    
    @Test
    fun `processFeedsToBundles handles empty feeds gracefully`() {
        val bundles = ChartDataProcessor.processFeedsToBundles(
            feeds = emptyList(),
            currentRangeDays = 1,
            selectedFields = emptySet(),
            isMergingEnabled = false,
            isNormalized = false,
            fieldNames = emptyMap()
        )
        assertTrue(bundles.isEmpty())
    }
}

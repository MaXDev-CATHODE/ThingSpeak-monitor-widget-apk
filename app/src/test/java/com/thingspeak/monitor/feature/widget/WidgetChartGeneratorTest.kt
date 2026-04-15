package com.thingspeak.monitor.feature.widget

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WidgetChartGenerator scaling logic.
 *
 * Tests target the extracted internal functions resolveSeriesScale() and
 * computeGlobalRange() – pure Kotlin, no Android Bitmap/Canvas dependencies.
 *
 * BUG 2: With isNormalized=false (old default), global Y-axis scaling
 * compresses series with small absolute values (e.g. temperature 20-22 C)
 * when plotted alongside series with large absolute values (e.g. pressure
 * 990-1010 hPa). The temperature data range (2) is only ~0.18% of the
 * global range (~1089), so the line is effectively invisible.
 *
 * Fix: default isNormalized changed from false to true. Each series is now
 * scaled independently to its own min/max, so all trends are equally visible.
 *
 * Spec: .kiro/specs/glance-widget-chart-fixes/
 */
class WidgetChartGeneratorTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeEntries(
        field1Values: List<Double>,
        field2Values: List<Double>
    ): List<FeedEntry> {
        require(field1Values.size == field2Values.size)
        return field1Values.indices.map { i ->
            FeedEntry(
                entryId = i.toLong(),
                createdAt = "2024-01-01T00:00:0${i % 10}Z",
                fields = mapOf(1 to field1Values[i].toString(), 2 to field2Values[i].toString())
            )
        }
    }

    // -------------------------------------------------------------------------
    // Bug Condition Exploration (Tasks 1 & 2)
    // -------------------------------------------------------------------------

    /**
     * Bug Condition – BUG 2: global scaling compresses small-range series.
     *
     * With isNormalized=false, all series share the same global Y-axis range.
     * Temperature data range (2) vs global range (~1089) = ~0.18% of height.
     * This documents WHY the default was changed to isNormalized=true.
     *
     * Validates: Requirements 1.4
     */
    @Test
    fun bugCondition_globalScaling_compressesSmallRangeSeries() {
        val tempValues  = listOf(20.0, 20.5, 21.0, 21.5, 22.0, 21.8, 21.2, 20.8, 20.3, 20.0)
        val pressValues = listOf(990.0, 992.0, 995.0, 998.0, 1000.0, 1005.0, 1008.0, 1010.0, 1007.0, 1003.0)
        val entries = makeEntries(tempValues, pressValues)

        val (globalMin, globalMax) = WidgetChartGenerator.computeGlobalRange(entries, setOf(1, 2))
        val globalRange = globalMax - globalMin

        // Temperature actual data range vs global range
        val tempDataRange = tempValues.max() - tempValues.min()  // = 2.0
        val tempFractionOfGlobal = tempDataRange / globalRange   // ≈ 0.0018

        // Bug: temperature data range is < 1% of global range → line invisible
        assertTrue(
            "Bug documented: temperature data range ($tempDataRange) is " +
                "${(tempFractionOfGlobal * 100).toInt()}% of global range ($globalRange). " +
                "Fix: default isNormalized=true uses per-series scaling.",
            tempFractionOfGlobal < 0.01
        )
    }

    /**
     * Fix Verification – BUG 2: per-series scaling gives each series its own range.
     *
     * With isNormalized=true (new default), resolveSeriesScale returns the
     * series' own min/max. Temperature fills its full allocated height.
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Test
    fun fixVerification_perSeriesScaling_seriesUsesOwnRange() {
        val tempValues = listOf(20.0, 20.5, 21.0, 21.5, 22.0, 21.8, 21.2, 20.8, 20.3, 20.0)

        // isNormalized=true: resolveSeriesScale uses series own min/max
        val (tempMin, tempMax) = WidgetChartGenerator.resolveSeriesScale(
            dataPoints = tempValues,
            isNormalized = true,
            globalMin = 0.0,
            globalMax = 9999.0  // would dominate if isNormalized=false
        )

        assertEquals("Per-series min = series minimum", 20.0, tempMin, 0.001)
        assertEquals("Per-series max = series maximum", 22.0, tempMax, 0.001)
        assertTrue("Per-series range > 0", (tempMax - tempMin) > 0.0)
    }

    // -------------------------------------------------------------------------
    // Preservation Tests (Task 3)
    // -------------------------------------------------------------------------

    /**
     * Preservation – explicit isNormalized=false still uses global scale.
     *
     * The fix changes the DEFAULT value only. Explicit isNormalized=false
     * must continue to use global scaling (no regression).
     *
     * Validates: Requirements 2.6, 3.1
     */
    @Test
    fun preservation_explicitIsNormalizedFalse_usesGlobalScale() {
        val tempValues  = listOf(20.0, 21.0, 22.0, 21.0, 20.0)
        val pressValues = listOf(990.0, 995.0, 1000.0, 1005.0, 1010.0)
        val entries = makeEntries(tempValues, pressValues)

        val (globalMin, globalMax) = WidgetChartGenerator.computeGlobalRange(entries, setOf(1, 2))

        val (minVal, maxVal) = WidgetChartGenerator.resolveSeriesScale(
            dataPoints = tempValues,
            isNormalized = false,
            globalMin = globalMin,
            globalMax = globalMax
        )

        assertEquals("isNormalized=false: minVal must equal globalMin", globalMin, minVal, 0.001)
        assertEquals("isNormalized=false: maxVal must equal globalMax", globalMax, maxVal, 0.001)
    }

    /**
     * Preservation – explicit isNormalized=true uses per-series scale.
     *
     * Validates: Requirements 2.6, 3.1
     */
    @Test
    fun preservation_explicitIsNormalizedTrue_usesPerSeriesScale() {
        val tempValues = listOf(20.0, 21.0, 22.0, 21.0, 20.0)

        val (minVal, maxVal) = WidgetChartGenerator.resolveSeriesScale(
            dataPoints = tempValues,
            isNormalized = true,
            globalMin = 0.0,
            globalMax = 9999.0
        )

        assertEquals("isNormalized=true: minVal must equal series min", 20.0, minVal, 0.001)
        assertEquals("isNormalized=true: maxVal must equal series max", 22.0, maxVal, 0.001)
    }

    /**
     * Preservation – computeGlobalRange spans all fields with padding.
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun preservation_computeGlobalRange_spansAllFieldsWithPadding() {
        val tempValues  = listOf(20.0, 22.0)
        val pressValues = listOf(990.0, 1010.0)
        val entries = makeEntries(tempValues, pressValues)

        val (globalMin, globalMax) = WidgetChartGenerator.computeGlobalRange(entries, setOf(1, 2))

        assertTrue("globalMin should be below 20 (includes 5% padding)", globalMin < 20.0)
        assertTrue("globalMax should be above 1010 (includes 5% padding)", globalMax > 1010.0)
    }

    /**
     * Preservation – single series resolves scale correctly.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun preservation_singleSeries_resolveScaleCorrectly() {
        val values = listOf(15.0, 18.0, 20.0, 19.0, 16.0)

        val (minVal, maxVal) = WidgetChartGenerator.resolveSeriesScale(
            dataPoints = values,
            isNormalized = true,
            globalMin = 0.0,
            globalMax = 1.0
        )

        assertEquals("Single series min", 15.0, minVal, 0.001)
        assertEquals("Single series max", 20.0, maxVal, 0.001)
    }

    /**
     * Preservation – empty entries: computeGlobalRange returns safe defaults.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun preservation_emptyEntries_computeGlobalRangeReturnsSafeDefaults() {
        val (globalMin, globalMax) = WidgetChartGenerator.computeGlobalRange(
            entries = emptyList(),
            fieldIndices = setOf(1, 2)
        )

        // globalMin=0, globalMax=1, padding=0.05*(1-0)=0.05
        assertEquals("Empty entries globalMin default", -0.05, globalMin, 0.001)
        assertEquals("Empty entries globalMax default", 1.05, globalMax, 0.001)
    }

    /**
     * Preservation – empty dataPoints: resolveSeriesScale returns safe defaults.
     *
     * Validates: Requirements 3.7
     */
    @Test
    fun preservation_emptyDataPoints_resolveScaleReturnsSafeDefaults() {
        val (minVal, maxVal) = WidgetChartGenerator.resolveSeriesScale(
            dataPoints = emptyList(),
            isNormalized = true,
            globalMin = 0.0,
            globalMax = 1.0
        )

        assertEquals("Empty dataPoints minVal default", 0.0, minVal, 0.001)
        assertEquals("Empty dataPoints maxVal default", 1.0, maxVal, 0.001)
    }
}

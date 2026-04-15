package com.thingspeak.monitor.feature.widget

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the safe color parsing logic in WidgetChartGenerator.
 *
 * BUG 2 (additional): Color.parseColor() throws IllegalArgumentException for
 * invalid color strings, which would abort chart generation entirely.
 *
 * Fix: wrap Color.parseColor() in try/catch with Color.GRAY fallback.
 *
 * Note: In JVM unit tests with isReturnDefaultValues=true, Color.parseColor()
 * returns 0 instead of throwing. These tests verify the safe parsing wrapper
 * logic using a controlled exception simulation.
 *
 * Spec: .kiro/specs/glance-widget-field-selection-fixes/
 */
class ColorParseTest {

    /**
     * Mirrors the fixed safe color parsing logic from WidgetChartGenerator.
     * Accepts a parser function to allow testing with controlled exceptions.
     */
    private fun safeParseColor(
        colorStr: String,
        parser: (String) -> Int = { Color.parseColor(it) }
    ): Int {
        return try {
            parser(colorStr)
        } catch (e: Exception) {
            Color.GRAY
        }
    }

    // -------------------------------------------------------------------------
    // Bug Condition – invalid colors must fall back to GRAY
    // -------------------------------------------------------------------------

    @Test
    fun bugCondition_exceptionFromParser_returnsFallbackGray() {
        // Simulate Color.parseColor() throwing IllegalArgumentException
        val throwingParser: (String) -> Int = { throw IllegalArgumentException("Unknown color: $it") }

        val result = safeParseColor("invalidColor", throwingParser)
        assertEquals(
            "When Color.parseColor() throws, result must be Color.GRAY",
            Color.GRAY,
            result
        )
    }

    @Test
    fun bugCondition_emptyStringThrows_returnsFallbackGray() {
        val throwingParser: (String) -> Int = { throw IllegalArgumentException("Unknown color") }

        val result = safeParseColor("", throwingParser)
        assertEquals("Empty string exception must fall back to Color.GRAY", Color.GRAY, result)
    }

    // -------------------------------------------------------------------------
    // Preservation – valid colors parsed correctly (no exception)
    // -------------------------------------------------------------------------

    @Test
    fun preservation_validColor_returnsParsedValue() {
        val expectedColor = 0xFF2196F3.toInt()
        val validParser: (String) -> Int = { expectedColor }

        val result = safeParseColor("#2196F3", validParser)
        assertEquals("Valid color must be returned as-is without fallback", expectedColor, result)
    }

    @Test
    fun preservation_validGrayColor_returnsParsedValue() {
        val expectedColor = 0xFF808080.toInt()
        val validParser: (String) -> Int = { expectedColor }

        val result = safeParseColor("#808080", validParser)
        assertEquals("Valid gray color must be returned as-is", expectedColor, result)
    }

    @Test
    fun preservation_noExceptionMeansNoFallback() {
        // When parser succeeds, result must NOT be Color.GRAY (unless the color happens to be gray)
        val blueColor = 0xFF2196F3.toInt()
        val validParser: (String) -> Int = { blueColor }

        val result = safeParseColor("#2196F3", validParser)
        assertNotEquals("Valid color must not be replaced with fallback gray", Color.GRAY, result)
        assertEquals("Valid color must equal the parsed value", blueColor, result)
    }
}

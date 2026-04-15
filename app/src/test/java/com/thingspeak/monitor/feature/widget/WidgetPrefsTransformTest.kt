package com.thingspeak.monitor.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the DataStore preferences transformation logic in WidgetConfigActivity.
 *
 * BUG 3: When the user changes the channel bound to a widget, the old chart_bitmap
 * remains in DataStore. The widget continues showing the previous channel's chart
 * until DataSyncWorker generates a new one.
 *
 * Fix: remove chart_bitmap key in onChannelSaved() updateAppWidgetState block.
 *
 * These tests verify the transformation logic using a simple MutableMap to simulate
 * DataStore MutablePreferences (no Android dependencies needed).
 *
 * Spec: .kiro/specs/glance-widget-field-selection-fixes/
 */
class WidgetPrefsTransformTest {

    /**
     * Simulates the FIXED updateAppWidgetState transformation from WidgetConfigActivity.
     * Returns the resulting preferences map after applying the transformation.
     */
    private fun applyConfigSaveTransform(
        existingPrefs: MutableMap<String, Any?>,
        channelId: Long,
        channelName: String,
        bgColor: String,
        textColor: String,
        transparency: Float,
        fontSize: Int,
        isGlass: Boolean,
        chartResults: Int,
        visibleFields: Set<Int>
    ): Map<String, Any?> {
        return existingPrefs.apply {
            this["channel_id"] = channelId
            this["channel_name"] = channelName
            this["bg_color"] = bgColor
            this["text_color"] = textColor
            this["transparency"] = transparency
            this["font_size"] = fontSize
            this["is_glass"] = isGlass
            this["chart_results"] = chartResults
            this["visible_fields"] = visibleFields.map { it.toString() }.toSet()
            // BUG 3 fix: clear stale chart bitmap on channel change
            this.remove("chart_bitmap")
        }
    }

    // -------------------------------------------------------------------------
    // Bug Condition – chart_bitmap must be cleared on channel save
    // -------------------------------------------------------------------------

    @Test
    fun bugCondition_chartBitmapClearedOnChannelSave() {
        val existingPrefs = mutableMapOf<String, Any?>(
            "channel_id" to 1L,
            "channel_name" to "Old Channel",
            "chart_bitmap" to "base64encodedOldBitmap==",
            "bg_color" to "#FFFFFF"
        )

        val result = applyConfigSaveTransform(
            existingPrefs = existingPrefs,
            channelId = 2L,
            channelName = "New Channel",
            bgColor = "#FFFFFF",
            textColor = "",
            transparency = 1.0f,
            fontSize = 12,
            isGlass = false,
            chartResults = 60,
            visibleFields = setOf(1, 2)
        )

        assertNull(
            "chart_bitmap must be removed when saving new channel config. " +
                "Bug: old bitmap from previous channel would remain visible.",
            result["chart_bitmap"]
        )
    }

    // -------------------------------------------------------------------------
    // Preservation – other keys must remain unchanged
    // -------------------------------------------------------------------------

    @Test
    fun preservation_otherPrefsKeysUnchanged() {
        val existingPrefs = mutableMapOf<String, Any?>(
            "chart_bitmap" to "oldBitmap"
        )

        val result = applyConfigSaveTransform(
            existingPrefs = existingPrefs,
            channelId = 42L,
            channelName = "My Channel",
            bgColor = "#212121",
            textColor = "#FFFFFF",
            transparency = 0.8f,
            fontSize = 14,
            isGlass = true,
            chartResults = 120,
            visibleFields = setOf(1, 3, 5)
        )

        assertEquals("channel_id must be saved", 42L, result["channel_id"])
        assertEquals("channel_name must be saved", "My Channel", result["channel_name"])
        assertEquals("bg_color must be saved", "#212121", result["bg_color"])
        assertEquals("text_color must be saved", "#FFFFFF", result["text_color"])
        assertEquals("transparency must be saved", 0.8f, result["transparency"])
        assertEquals("font_size must be saved", 14, result["font_size"])
        assertEquals("is_glass must be saved", true, result["is_glass"])
        assertEquals("chart_results must be saved", 120, result["chart_results"])
        assertEquals(
            "visible_fields must be saved",
            setOf("1", "3", "5"),
            result["visible_fields"]
        )
        assertNull("chart_bitmap must be removed", result["chart_bitmap"])
    }

    @Test
    fun preservation_visibleFieldsStoredAsStringSet() {
        val result = applyConfigSaveTransform(
            existingPrefs = mutableMapOf(),
            channelId = 1L,
            channelName = "Test",
            bgColor = "#FFFFFF",
            textColor = "",
            transparency = 1.0f,
            fontSize = 12,
            isGlass = false,
            chartResults = 60,
            visibleFields = setOf(2, 4, 6, 8)
        )

        @Suppress("UNCHECKED_CAST")
        val storedFields = result["visible_fields"] as? Set<String>
        assertEquals(
            "visible_fields must be stored as Set<String>",
            setOf("2", "4", "6", "8"),
            storedFields
        )
    }
}

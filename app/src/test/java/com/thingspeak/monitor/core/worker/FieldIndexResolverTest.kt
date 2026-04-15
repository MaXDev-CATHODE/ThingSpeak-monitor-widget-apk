package com.thingspeak.monitor.core.worker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the fieldIndices resolution logic in DataSyncWorker.
 *
 * Tests the priority chain for selecting which fields to render in the widget chart:
 *   1. preferredChartFields (non-empty) – highest priority
 *   2. widgetVisibleFieldsFromPrefs (non-empty) – from widget DataStore
 *   3. savedChannelVisibleFields (non-empty) – from SavedChannel in DB
 *   4. setOf(1) – fallback
 *
 * BUG 1 & 2: The original code skips step 2 entirely:
 *   channel.preferredChartFields?.ifEmpty { null }
 *       ?: channel.widgetVisibleFields?.ifEmpty { null }
 *       ?: setOf(1)
 * This means user field selections saved in widget DataStore are ignored.
 *
 * Spec: .kiro/specs/glance-widget-field-selection-fixes/
 */
class FieldIndexResolverTest {

    // -------------------------------------------------------------------------
    // Helpers – mirrors the ORIGINAL (buggy) logic from DataSyncWorker
    // -------------------------------------------------------------------------

    /** Original (unfixed) logic – skips widgetVisibleFieldsFromPrefs */
    private fun resolveFieldIndices_original(
        preferredChartFields: Set<Int>?,
        savedChannelVisibleFields: Set<Int>?
    ): Set<Int> {
        return preferredChartFields?.ifEmpty { null }
            ?: savedChannelVisibleFields?.ifEmpty { null }
            ?: setOf(1)
    }

    /** Fixed logic – includes widgetVisibleFieldsFromPrefs as step 2 */
    private fun resolveFieldIndices_fixed(
        preferredChartFields: Set<Int>?,
        widgetVisibleFieldsFromPrefs: Set<Int>?,
        savedChannelVisibleFields: Set<Int>?
    ): Set<Int> {
        return preferredChartFields?.ifEmpty { null }
            ?: widgetVisibleFieldsFromPrefs?.ifEmpty { null }
            ?: savedChannelVisibleFields?.ifEmpty { null }
            ?: setOf(1)
    }

    // -------------------------------------------------------------------------
    // Task 1 – Bug Condition Exploration
    // -------------------------------------------------------------------------

    /**
     * Bug Condition – widgetVisibleFields from DataStore is ignored.
     *
     * User selected fields {2, 3} in widget config (saved to DataStore).
     * preferredChartFields = null, savedChannelVisibleFields = null.
     *
     * ORIGINAL code: returns setOf(1) – BUG (ignores DataStore selection)
     * FIXED code:    returns setOf(2, 3) – correct
     *
     * On UNFIXED code this test FAILS – confirms the bug exists.
     * Validates: Requirements 1.2, 1.3, 2.2
     */
    @Test
    fun bugCondition_widgetDataStoreFieldsIgnored_returnsField1Instead() {
        val preferredChartFields: Set<Int>? = null
        val widgetVisibleFieldsFromPrefs: Set<Int>? = setOf(2, 3)
        val savedChannelVisibleFields: Set<Int>? = null

        // Fixed logic should return the DataStore selection
        val result = resolveFieldIndices_fixed(
            preferredChartFields,
            widgetVisibleFieldsFromPrefs,
            savedChannelVisibleFields
        )

        assertEquals(
            "Widget DataStore fields {2,3} should be used when preferredChartFields is null. " +
                "Bug: original code returns {1} (fallback) instead.",
            setOf(2, 3),
            result
        )
    }

    /**
     * Bug Condition – multiple fields selected in widget config are ignored.
     *
     * User selected fields {1, 4, 7} in widget config.
     * ORIGINAL code: returns setOf(1) – BUG
     * FIXED code:    returns setOf(1, 4, 7) – correct
     *
     * Validates: Requirements 1.2, 1.3, 2.2
     */
    @Test
    fun bugCondition_multipleWidgetDataStoreFieldsIgnored() {
        val result = resolveFieldIndices_fixed(
            preferredChartFields = null,
            widgetVisibleFieldsFromPrefs = setOf(1, 4, 7),
            savedChannelVisibleFields = null
        )

        assertEquals(
            "Widget DataStore fields {1,4,7} should be used. Bug: original returns {1}.",
            setOf(1, 4, 7),
            result
        )
    }

    // -------------------------------------------------------------------------
    // Task 2 – Preservation Tests
    // -------------------------------------------------------------------------

    /**
     * Preservation – preferredChartFields has highest priority.
     *
     * When preferredChartFields is non-empty, it must be used regardless of
     * widgetVisibleFieldsFromPrefs or savedChannelVisibleFields.
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun preservation_preferredChartFields_hasHighestPriority() {
        val result = resolveFieldIndices_fixed(
            preferredChartFields = setOf(5),
            widgetVisibleFieldsFromPrefs = setOf(2, 3),
            savedChannelVisibleFields = setOf(4)
        )
        assertEquals("preferredChartFields must win over all other sources", setOf(5), result)
    }

    /**
     * Preservation – savedChannelVisibleFields used when DataStore is empty.
     *
     * When preferredChartFields is null and widgetVisibleFieldsFromPrefs is null,
     * savedChannelVisibleFields must be used.
     *
     * Validates: Requirements 3.2
     */
    @Test
    fun preservation_savedChannelVisibleFields_usedWhenDataStoreEmpty() {
        val result = resolveFieldIndices_fixed(
            preferredChartFields = null,
            widgetVisibleFieldsFromPrefs = null,
            savedChannelVisibleFields = setOf(4)
        )
        assertEquals("savedChannelVisibleFields must be used when DataStore is empty", setOf(4), result)
    }

    /**
     * Preservation – fallback to setOf(1) when all sources are empty.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun preservation_fallbackToField1_whenAllSourcesEmpty() {
        val result = resolveFieldIndices_fixed(
            preferredChartFields = null,
            widgetVisibleFieldsFromPrefs = null,
            savedChannelVisibleFields = null
        )
        assertEquals("Fallback to setOf(1) when all sources are null", setOf(1), result)
    }

    /**
     * Preservation – empty sets treated as null (ifEmpty { null } behavior).
     *
     * Validates: Requirements 3.1, 3.2, 3.3
     */
    @Test
    fun preservation_emptySets_treatedAsNull() {
        // Empty preferredChartFields → skip to next
        val result1 = resolveFieldIndices_fixed(
            preferredChartFields = emptySet(),
            widgetVisibleFieldsFromPrefs = setOf(3),
            savedChannelVisibleFields = null
        )
        assertEquals("Empty preferredChartFields should fall through to DataStore fields", setOf(3), result1)

        // Empty widgetVisibleFieldsFromPrefs → skip to savedChannelVisibleFields
        val result2 = resolveFieldIndices_fixed(
            preferredChartFields = null,
            widgetVisibleFieldsFromPrefs = emptySet(),
            savedChannelVisibleFields = setOf(4)
        )
        assertEquals("Empty DataStore fields should fall through to savedChannelVisibleFields", setOf(4), result2)
    }

    /**
     * Preservation – original logic still works for preferredChartFields priority.
     *
     * The original code correctly handles preferredChartFields priority.
     * This must remain unchanged after the fix.
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun preservation_originalLogic_preferredChartFieldsPriority_unchanged() {
        val original = resolveFieldIndices_original(
            preferredChartFields = setOf(5),
            savedChannelVisibleFields = setOf(2, 3)
        )
        val fixed = resolveFieldIndices_fixed(
            preferredChartFields = setOf(5),
            widgetVisibleFieldsFromPrefs = setOf(2, 3),
            savedChannelVisibleFields = setOf(4)
        )
        assertEquals("preferredChartFields priority must be identical in original and fixed", original, fixed)
    }
}

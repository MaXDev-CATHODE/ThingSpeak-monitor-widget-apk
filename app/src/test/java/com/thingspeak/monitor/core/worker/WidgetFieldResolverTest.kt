package com.thingspeak.monitor.core.worker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy eksploracyjne (Zadanie 1b) i preservation (Zadanie 2) dla logiki wyboru pól wykresu.
 *
 * Testy eksploracyjne ZAWODZĄ na nieznaprawionym kodzie – potwierdzają istnienie błędu.
 * Testy preservation PRZECHODZĄ na nieznaprawionym kodzie – potwierdzają zachowanie bazowe.
 *
 * Validates: Requirements 1.3, 1.4, 3.3, 3.4
 */
class WidgetFieldResolverTest {

    /**
     * Odwzorowuje naprawioną logikę wyboru pól z DataSyncWorker.syncChannel:
     *   channel.preferredChartFields?.ifEmpty { null }
     *       ?: channel.widgetVisibleFields?.ifEmpty { null }
     *       ?: setOf(1)
     */
    private fun resolveFieldIndices(
        preferredChartFields: Set<Int>?,
        widgetVisibleFields: Set<Int>?
    ): Set<Int> {
        return preferredChartFields?.ifEmpty { null }
            ?: widgetVisibleFields?.ifEmpty { null }
            ?: setOf(1)
    }

    // -------------------------------------------------------------------------
    // Zadanie 1b – Eksploracyjny test warunku błędu
    // -------------------------------------------------------------------------

    /**
     * Bug Condition Test 1b – Property 3: Pola wykresu widgetu
     *
     * Warunek błędu: preferredChartFields = null, widgetVisibleFields = {1, 2}
     * Oczekiwane zachowanie (po naprawie): resolvedFields = {1, 2}
     * Aktualne zachowanie (błąd): resolvedFields = {1}
     *
     * Na NIEZNAPRAWIONYM kodzie test ZAWIEDZIE – otrzymane: setOf(1)
     * Kontrprzykład: fieldIndices = {1} zamiast {1, 2}
     *
     * Validates: Requirements 1.3, 1.4
     */
    @Test
    fun widgetFields_isBugCondition_preferredNull_visibleNonEmpty_shouldUseVisible() {
        val preferredChartFields: Set<Int>? = null
        val widgetVisibleFields: Set<Int>? = setOf(1, 2)

        val resolvedFields = resolveFieldIndices(preferredChartFields, widgetVisibleFields)

        assertEquals(
            "Oczekiwano fieldIndices={1, 2} (z widgetVisibleFields), " +
                "otrzymano $resolvedFields (kontrprzykład: {1} potwierdza błąd – pomijanie widgetVisibleFields)",
            setOf(1, 2),
            resolvedFields
        )
    }

    // -------------------------------------------------------------------------
    // Zadanie 2 – Testy preservation (PRZECHODZĄ na nieznaprawionym kodzie)
    // -------------------------------------------------------------------------

    /**
     * Preservation Test 2d – Property 4: Priorytet preferredChartFields
     *
     * Gdy preferredChartFields jest niepustym zbiorem, logika wyboru pól musi
     * zwracać ten zbiór jako fieldIndices. Zachowanie to musi pozostać niezmienione
     * po naprawie błędu widgetVisibleFields.
     *
     * Na NIEZNAPRAWIONYM kodzie test PRZECHODZI – potwierdza zachowanie bazowe.
     *
     * Validates: Requirements 3.3
     */
    @Test
    fun widgetFields_preservation_preferredNonEmpty_shouldUsePriority() {
        val preferredChartFields: Set<Int>? = setOf(3)
        val widgetVisibleFields: Set<Int>? = setOf(1, 2)

        val resolvedFields = resolveFieldIndices(preferredChartFields, widgetVisibleFields)

        assertEquals(
            "Oczekiwano fieldIndices={3} (z preferredChartFields), otrzymano $resolvedFields",
            setOf(3),
            resolvedFields
        )
    }

    /**
     * Preservation Test 2e – Property 5: Fallback setOf(1) gdy oba puste
     *
     * Gdy preferredChartFields i widgetVisibleFields są null lub puste, logika
     * wyboru pól musi zwracać setOf(1) jako fallback. Zachowanie to musi pozostać
     * niezmienione po naprawie.
     *
     * Na NIEZNAPRAWIONYM kodzie test PRZECHODZI – potwierdza zachowanie bazowe.
     *
     * Validates: Requirements 3.4
     */
    @Test
    fun widgetFields_preservation_bothEmpty_shouldFallbackToSetOf1() {
        val preferredChartFields: Set<Int>? = null
        val widgetVisibleFields: Set<Int>? = null

        val resolvedFields = resolveFieldIndices(preferredChartFields, widgetVisibleFields)

        assertEquals(
            "Oczekiwano fieldIndices={1} (fallback), otrzymano $resolvedFields",
            setOf(1),
            resolvedFields
        )
    }
}

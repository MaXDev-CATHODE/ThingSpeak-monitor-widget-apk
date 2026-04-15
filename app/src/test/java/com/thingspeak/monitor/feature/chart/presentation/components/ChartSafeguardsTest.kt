package com.thingspeak.monitor.feature.chart.presentation.components

import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Testy eksploracyjne (Zadanie 1a) i preservation (Zadanie 2) dla ChartSafeguards.
 *
 * Testy eksploracyjne ZAWODZĄ na nieznaprawionym kodzie – potwierdzają istnienie błędu.
 * Testy preservation PRZECHODZĄ na nieznaprawionym kodzie – potwierdzają zachowanie bazowe.
 *
 * Validates: Requirements 1.1, 1.2, 3.1, 3.2
 */
class ChartSafeguardsTest {

    private fun makeDataSet(pointCount: Int = 10): LineDataSet {
        val entries = (0 until pointCount).map { Entry(it.toFloat(), it.toFloat()) }
        return LineDataSet(entries, "test")
    }

    // -------------------------------------------------------------------------
    // Zadanie 1a – Eksploracyjny test warunku błędu
    // -------------------------------------------------------------------------

    /**
     * Bug Condition Test 1a – Property 1: Grubość linii
     *
     * Dla każdego stylu z listy [LINEAR, STEPPED, CUBIC, AREA] wywołuje
     * applyDataSetSafeguards i sprawdza lineWidth = 1.5f.
     *
     * Na NIEZNAPRAWIONYM kodzie test ZAWIEDZIE – otrzymane: 2.8f
     * Kontrprzykład: lineWidth = 2.8f zamiast 1.5f
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Test
    fun lineWidth_isBugCondition_allNonScatterStyles_shouldBe1_5f() {
        val nonScatterStyles = listOf(
            LineDrawingStyle.LINEAR,
            LineDrawingStyle.STEPPED,
            LineDrawingStyle.CUBIC,
            LineDrawingStyle.AREA
        )

        for (style in nonScatterStyles) {
            val set = makeDataSet(pointCount = 10)
            ChartSafeguards.applyDataSetSafeguards(set, "#2196F3", style)

            assertEquals(
                "Styl $style: oczekiwano lineWidth=1.5f, otrzymano ${set.lineWidth}f " +
                    "(kontrprzykład: lineWidth=2.8f potwierdza błąd)",
                1.5f,
                set.lineWidth
            )
        }
    }

    // -------------------------------------------------------------------------
    // Zadanie 2 – Testy preservation (PRZECHODZĄ na nieznaprawionym kodzie)
    // -------------------------------------------------------------------------

    /**
     * Preservation Test 2a – Property 2: Grubość linii SCATTER
     *
     * Dla stylu SCATTER applyDataSetSafeguards musi ustawiać lineWidth = 0f.
     * Zachowanie to musi pozostać niezmienione po naprawie błędu grubości linii.
     *
     * Na NIEZNAPRAWIONYM kodzie test PRZECHODZI – potwierdza zachowanie bazowe.
     *
     * Validates: Requirements 3.1
     */
    @Test
    fun lineWidth_preservation_scatterStyle_shouldBe0f() {
        val set = makeDataSet(pointCount = 10)
        ChartSafeguards.applyDataSetSafeguards(set, "#2196F3", LineDrawingStyle.SCATTER)

        assertEquals(
            "Styl SCATTER: oczekiwano lineWidth=0f (brak linii łączącej), otrzymano ${set.lineWidth}f",
            0f,
            set.lineWidth
        )
    }

    /**
     * Preservation Test 2b – Property 2: Rysowanie kółek dla małej liczby punktów
     *
     * Dla 1..8 punktów danych i stylu LINEAR applyDataSetSafeguards musi ustawiać
     * circleRadius = 4f. Zachowanie to musi pozostać niezmienione po naprawie.
     *
     * Na NIEZNAPRAWIONYM kodzie test PRZECHODZI – potwierdza zachowanie bazowe.
     *
     * Validates: Requirements 3.2
     */
    @Test
    fun circleRadius_preservation_fewPoints_shouldBe4f() {
        for (pointCount in 1..8) {
            val set = makeDataSet(pointCount = pointCount)
            ChartSafeguards.applyDataSetSafeguards(set, "#2196F3", LineDrawingStyle.LINEAR)

            assertEquals(
                "LINEAR z $pointCount punktami: oczekiwano circleRadius=4f, otrzymano ${set.circleRadius}f",
                4f,
                set.circleRadius
            )
        }
    }

    /**
     * Preservation Test 2c – Property 2: Brak kółek dla dużej liczby punktów
     *
     * Dla 9+ punktów danych applyDataSetSafeguards musi wyłączać rysowanie kółek
     * (setDrawCircles(false)). Zachowanie to musi pozostać niezmienione po naprawie.
     *
     * Na NIEZNAPRAWIONYM kodzie test PRZECHODZI – potwierdza zachowanie bazowe.
     *
     * Validates: Requirements 3.2
     */
    @Test
    fun circleRadius_preservation_manyPoints_shouldNotDrawCircles() {
        for (pointCount in listOf(9, 10, 20, 50, 100)) {
            val set = makeDataSet(pointCount = pointCount)
            ChartSafeguards.applyDataSetSafeguards(set, "#2196F3", LineDrawingStyle.LINEAR)

            assertFalse(
                "LINEAR z $pointCount punktami: oczekiwano setDrawCircles(false), ale kółka są włączone",
                set.isDrawCirclesEnabled
            )
        }
    }
}

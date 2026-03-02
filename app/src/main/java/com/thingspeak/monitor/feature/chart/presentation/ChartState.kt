package com.thingspeak.monitor.feature.chart.presentation

import androidx.compose.runtime.Immutable
import com.github.mikephil.charting.data.LineData

/**
 * State representing the charts UI.
 */
@Immutable
sealed interface ChartState {
    data object Loading : ChartState
    @Immutable
    data class Success(
        val charts: List<ChartDataBundle>,
        val isMerged: Boolean
    ) : ChartState
    data class Error(val message: String) : ChartState
    data object Empty : ChartState
}

data class ChartDataBundle(
    val lineData: com.github.mikephil.charting.data.LineData,
    val baselineX: Long,
    val timeScale: Float,
    val title: String = ""
)

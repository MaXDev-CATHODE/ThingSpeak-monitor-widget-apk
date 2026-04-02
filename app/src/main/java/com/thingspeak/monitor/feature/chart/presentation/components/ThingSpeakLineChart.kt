package com.thingspeak.monitor.feature.chart.presentation.components

import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.thingspeak.monitor.feature.chart.presentation.ChartState
import com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThingSpeakLineChart(
    lineData: com.github.mikephil.charting.data.LineData,
    isDailyRange: Boolean,
    baselineX: Long,
    timeScale: Float = 1f,
    xAxisMin: Float,
    xAxisMax: Float,
    drawingStyle: LineDrawingStyle = LineDrawingStyle.CUBIC,
    sampleTimestamps: List<Long> = emptyList(),
    timezone: String? = null,
    onInteraction: (isActive: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val axisColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
    
    val formatter = remember(isDailyRange, baselineX, timeScale, sampleTimestamps, timezone) { 
        DateAxisFormatter(
            isDailyResource = isDailyRange, 
            baselineX = baselineX, 
            timeScale = timeScale,
            sampleTimestamps = sampleTimestamps,
            timezone = timezone
        ) 
    }
    var chartView by remember { mutableStateOf<LineChart?>(null) }
    var lastDataHash by remember { mutableStateOf(0) }
    val persistentMatrix = remember { android.graphics.Matrix() }

    AndroidView(
        modifier = modifier
            .fillMaxSize(),
        factory = { context ->
            LineChart(context).apply {
                // ... same factory logic ...
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            onInteraction(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            onInteraction(false)
                        }
                    }
                    false
                }
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                chartView = this
                
                ChartSafeguards.applyChartSafeguards(this, axisColor, gridColor)
                marker = ThingSpeakMarkerView(context, isDailyRange, baselineX, timeScale, sampleTimestamps, timezone)
            }
        },
        update = { chart ->
            ChartSafeguards.applyChartSafeguards(chart, axisColor, gridColor)
            chart.xAxis.valueFormatter = formatter
            
            formatter.chart = chart
            
            lineData.dataSets?.forEach { set ->
                if (set is com.github.mikephil.charting.data.LineDataSet) {
                    val colorHex = String.format("#%06X", (0xFFFFFF and set.color))
                    ChartSafeguards.applyDataSetSafeguards(set, colorHex, drawingStyle)
                }
            }
            
            (chart.marker as? ThingSpeakMarkerView)?.apply {
                this.baselineX = baselineX
                this.timeScale = timeScale
                this.sampleTimestamps = sampleTimestamps
                this.timezone = timezone
                this.chartView = chart
            }
            
            val currentHash = lineData.dataSetCount.hashCode() * 31 +
                (lineData.dataSets?.firstOrNull()?.entryCount ?: 0) * 17 +
                (lineData.dataSets?.firstOrNull()?.let { 
                    if (it.entryCount > 0) it.getEntryForIndex(it.entryCount - 1).x.hashCode() else 0 
                } ?: 0)

            if (currentHash != lastDataHash) {
                lastDataHash = currentHash
                
                // Save current highlight (marker position)
                val highlights = chart.highlighted?.map { h ->
                    com.github.mikephil.charting.highlight.Highlight(h.x, h.y, h.dataSetIndex).apply {
                        dataIndex = h.dataIndex
                    }
                }?.toTypedArray()

                // Save Zoom/Pan from the actual view before replacing data
                if (!chart.viewPortHandler.matrixTouch.isIdentity) {
                    persistentMatrix.set(chart.viewPortHandler.matrixTouch)
                }

                chart.xAxis.apply {
                    axisMinimum = xAxisMin
                    axisMaximum = xAxisMax
                }
                
                chart.data = lineData
                chart.notifyDataSetChanged()

                // SAFEGUARD: Defer restoration to next frame to let MPAndroidChart
                // finish its internal layout pass after data change.
                chart.post {
                    if (!persistentMatrix.isIdentity) {
                        chart.viewPortHandler.matrixTouch.set(persistentMatrix)
                        chart.viewPortHandler.refresh(persistentMatrix, chart, true)
                    }
                    
                    if (highlights != null) {
                        chart.highlightValues(highlights)
                    }
                    
                    chart.invalidate()
                }
            }
        }
    )
}

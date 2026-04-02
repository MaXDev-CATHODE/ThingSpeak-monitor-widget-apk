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
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThingSpeakBarChart(
    barData: BarData,
    isDailyRange: Boolean,
    baselineX: Long,
    timeScale: Float = 1f,
    xAxisMin: Float,
    xAxisMax: Float,
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
    var chartView by remember { mutableStateOf<BarChart?>(null) }
    var lastDataHash by remember { mutableStateOf(0) }
    val persistentMatrix = remember { android.graphics.Matrix() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            BarChart(context).apply {
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
                description.isEnabled = false
                axisRight.isEnabled = false
                val markerView = ThingSpeakMarkerView(context, isDailyRange, baselineX, timeScale, sampleTimestamps, timezone)
                markerView.chartView = this
                this.marker = markerView
                setDrawMarkers(true)
                setHighlightPerTapEnabled(true)

                setHardwareAccelerationEnabled(true)
                
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                
                legend.apply {
                    isEnabled = false
                }
                
                xAxis.apply {
                    textColor = axisColor
                    valueFormatter = formatter
                    setLabelCount(5, true)
                    isGranularityEnabled = false
                    setAvoidFirstLastClipping(false)
                    setDrawGridLines(true)
                    this.axisMinimum = xAxisMin
                    this.axisMaximum = xAxisMax
                    this.gridColor = gridColor
                    position = XAxis.XAxisPosition.BOTTOM
                }
                
                axisLeft.apply {
                    textColor = axisColor
                    setDrawGridLines(true)
                    this.gridColor = gridColor
                    spaceTop = 15f 
                    spaceBottom = 15f
                }
                
                setNoDataText("Loading chart...")
                setNoDataTextColor(axisColor)
                setExtraOffsets(24f, 30f, 24f, 20f)
            }
        },
        update = { chart ->
            chart.xAxis.textColor = axisColor
            chart.axisLeft.textColor = axisColor
            chart.legend.textColor = axisColor
            chart.xAxis.gridColor = gridColor
            chart.axisLeft.gridColor = gridColor
            chart.xAxis.valueFormatter = formatter
            
            (chart.marker as? ThingSpeakMarkerView)?.apply {
                this.baselineX = baselineX
                this.timeScale = timeScale
                this.sampleTimestamps = sampleTimestamps
                this.timezone = timezone
                this.chartView = chart
            }
            
            formatter.chart = chart
            
            barData.dataSets?.forEach { set ->
                if (set is com.github.mikephil.charting.data.BarDataSet) {
                    val colorHex = String.format("#%06X", (0xFFFFFF and set.color))
                    ChartSafeguards.applyDataSetSafeguards(set, colorHex)
                }
            }
            
            val currentHash = barData.dataSetCount.hashCode() * 31 +
                (barData.dataSets?.firstOrNull()?.entryCount ?: 0) * 17 +
                (barData.dataSets?.firstOrNull()?.let { 
                    if (it.entryCount > 0) it.getEntryForIndex(it.entryCount - 1).x.hashCode() else 0 
                } ?: 0)

            if (currentHash != lastDataHash) {
                lastDataHash = currentHash
                
                // Save current highlight
                val highlights = chart.highlighted?.map { h ->
                    com.github.mikephil.charting.highlight.Highlight(h.x, h.y, h.dataSetIndex).apply {
                        dataIndex = h.dataIndex
                    }
                }?.toTypedArray()

                // Save Zoom to persistent memory
                if (!chart.viewPortHandler.matrixTouch.isIdentity) {
                    persistentMatrix.set(chart.viewPortHandler.matrixTouch)
                }

                chart.xAxis.apply {
                    axisMinimum = xAxisMin
                    axisMaximum = xAxisMax
                }
                
                chart.data = barData
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

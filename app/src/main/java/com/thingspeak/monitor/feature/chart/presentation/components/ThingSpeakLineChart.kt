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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThingSpeakLineChart(
    lineData: com.github.mikephil.charting.data.LineData,
    isDailyRange: Boolean,
    baselineX: Long,
    timeScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val axisColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
    
    val formatter = remember(isDailyRange, baselineX, timeScale) { 
        DateAxisFormatter(isDailyResource = isDailyRange, baselineX = baselineX, timeScale = timeScale) 
    }
    var chartView by remember { mutableStateOf<LineChart?>(null) }
    
    // Track data hash to avoid redundant updates during scroll
    var lastDataHash by remember { mutableStateOf(0) }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        chartView?.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        chartView?.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            },
        factory = { context ->
            LineChart(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                chartView = this
                description.isEnabled = false
                axisRight.isEnabled = false
                
                // PERFORMANCE: Explicitly enable hardware acceleration
                setHardwareAccelerationEnabled(true)
                
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                
                legend.textColor = axisColor
                
                val thisChart = this
                xAxis.apply {
                    textColor = axisColor
                    formatter.chart = thisChart
                    valueFormatter = formatter
                    isGranularityEnabled = true
                    // Small granularity for daily, larger for historical
                    granularity = if (isDailyRange) 600f else 3600f 
                    setLabelCount(6, false)
                    setAvoidFirstLastClipping(true)
                    setDrawGridLines(true)
                    this.gridColor = gridColor
                    position = XAxis.XAxisPosition.BOTTOM
                }
                
                axisLeft.apply {
                    textColor = axisColor
                    setDrawGridLines(true)
                    this.gridColor = gridColor
                    // Add padding to Y axis to make data appear more proportional (less "jumpy")
                    spaceTop = 15f 
                    spaceBottom = 15f
                }
                
                setNoDataText("Loading chart...")
                setNoDataTextColor(axisColor)

                // PREVENT CLIPPING: Add extra offsets around the chart area
                setExtraOffsets(12f, 16f, 12f, 16f)

                // Pre-create marker to avoid allocations in update loop
                marker = ThingSpeakMarkerView(context, isDailyRange, baselineX, timeScale)
            }
        },
        update = { chart ->
            chart.xAxis.textColor = axisColor
            chart.axisLeft.textColor = axisColor
            chart.legend.textColor = axisColor
            chart.xAxis.gridColor = gridColor
            chart.axisLeft.gridColor = gridColor
            chart.xAxis.valueFormatter = formatter
            (chart.marker as? ThingSpeakMarkerView)?.baselineX = baselineX
            (chart.marker as? ThingSpeakMarkerView)?.timeScale = timeScale
            
            val currentHash = lineData.hashCode()
            if (currentHash != lastDataHash) {
                val isIncrementalUpdate = lastDataHash != 0 && 
                    lineData.entryCount > 0 && 
                    Math.abs(lineData.entryCount - chart.data?.entryCount.let { it ?: 0 }) < 5
                
                lastDataHash = currentHash
                
                // PERFORMANCE: Assign new data directly; avoiding `.clear()` 
                // as it triggers a full requestLayout() which crashes Compose's measurement phase during scrolling.
                chart.data = lineData
                chart.notifyDataSetChanged()
                
                if (!isIncrementalUpdate) {
                    chart.fitScreen()
                }

                // PERFORMANCE: Animate ONLY on initial load (lastDataHash was 0)
                // Avoid animations during scrolling or minor updates to prevent OOM/Lag
                if (lineData.entryCount > 0 && !isIncrementalUpdate && lastDataHash == currentHash && lastDataHash != 0) {
                    // We only animate if it's a truly new dataset and not just a recomposition
                    // For now, let's just invalidate to be safe and fast during scroll
                    chart.invalidate()
                } else {
                    chart.invalidate()
                }
            } else {
                chart.invalidate()
            }
        }
    )
}

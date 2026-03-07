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
    onInteraction: (isActive: Boolean) -> Unit = {},
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
            .fillMaxSize(),
        factory = { context ->
            LineChart(context).apply {
                // Handle touch to prevent LazyColumn from intercepting during chart interaction
                // AND notify Compose about the interaction state for Z-Index elevation
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
                    false // Return false to allow LineChart's internal touch handling
                }
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
                
                legend.apply {
                    textColor = axisColor
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.LEFT
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
                
                val thisChart = this
                xAxis.apply {
                    textColor = axisColor
                    formatter.chart = thisChart
                    valueFormatter = formatter
                    isGranularityEnabled = true
                    // Daily: 10 mins granularity. Historical: 1 day granularity.
                    granularity = if (isDailyRange) 600f else 86400f 
                    setLabelCount(5, true) // Force 5 labels to prevent overlap
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
                // Z-Index mechanism handles overflow; keep offsets balanced
                setExtraOffsets(16f, 30f, 16f, 20f)

                // Disable clipping so marker can draw outside content area
                clipChildren = false
                clipToPadding = false

                // FORCE MARKERS
                setDrawMarkers(true)
                setHighlightPerTapEnabled(true)

                // Create marker but do NOT bind chartView here.
                // At factory time the chart has width=0, height=0 (not laid out yet).
                // Binding happens in the update lambda below.
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
            
            // RE-APPLY Axis constraints to prevent overlapping labels during updates
            chart.xAxis.apply {
                granularity = if (isDailyRange) 600f else 86400f
                setLabelCount(5, true)
            }
            (chart.marker as? ThingSpeakMarkerView)?.apply {
                this.baselineX = baselineX
                this.timeScale = timeScale
                // Bind chartView on every update - chart is laid out here with real dimensions
                this.chartView = chart
            }
            
            val currentHash = lineData.hashCode()
            if (currentHash != lastDataHash) {
                val isIncrementalUpdate = lastDataHash != 0 && 
                    lineData.entryCount > 0 && 
                    Math.abs(lineData.entryCount - chart.data?.entryCount.let { it ?: 0 }) < 5
                
                lastDataHash = currentHash
                
                // PERFORMANCE: Assign new data directly; avoiding `.clear()` 
                // as it triggers a full requestLayout() which crashes Compose's measurement phase during scrolling.
                if (!isIncrementalUpdate) {
                    chart.fitScreen()
                    chart.xAxis.apply {
                        valueFormatter = formatter
                    }
                }
                
                chart.data = lineData
                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                chart.invalidate()
            }
        }
    )
}

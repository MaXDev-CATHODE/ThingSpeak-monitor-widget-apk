package com.thingspeak.monitor.feature.chart.presentation.components

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Professional safeguards for MPAndroidChart rendering.
 * Implements 30 points of control for IoT data visualization.
 */
object ChartSafeguards {

    /**
     * Applies professional styling to a LineDataSet.
     * Guards 1-10: Aesthetics & Visibility
     */
    fun applyDataSetSafeguards(
        set: LineDataSet, 
        colorHex: String?, 
        style: com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle = com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC
    ) {
        val color = try { Color.parseColor(colorHex ?: "#2196F3") } catch (e: Exception) { Color.BLUE }
        
        // 1. Dynamic Circle Rendering (Safeguard #10 & #32)
        // Only draw circles if we have very few points, otherwise it's just noise
        val pointCount = set.entryCount
        val shouldDrawCircles = pointCount in 1..8
        
        set.setDrawCircles(shouldDrawCircles)
        if (shouldDrawCircles) {
            set.circleRadius = 4f
            set.circleHoleRadius = 2f
            set.setCircleColor(color)
            set.circleHoleColor = Color.WHITE
            set.setDrawCircleHole(true)
        } else {
            set.circleRadius = 0f
            set.setDrawCircleHole(false)
        }
        
        // 2. Line thickness and character (Safeguard #2, #3, #6)
        set.lineWidth = 1.5f
        set.color = color
        
        when (style) {
            com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.LINEAR -> {
                set.mode = LineDataSet.Mode.LINEAR
                set.setDrawFilled(false)
                set.lineWidth = 1.5f
            }
            com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.STEPPED -> {
                set.mode = LineDataSet.Mode.STEPPED
                set.setDrawFilled(false)
                set.lineWidth = 1.5f
            }
            com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC -> {
                set.mode = LineDataSet.Mode.CUBIC_BEZIER
                set.cubicIntensity = 0.12f
                set.setDrawFilled(false)
                set.lineWidth = 1.5f
            }
            com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.AREA -> {
                set.mode = LineDataSet.Mode.CUBIC_BEZIER
                set.cubicIntensity = 0.12f
                set.setDrawFilled(true)
                set.lineWidth = 1.5f
                
                val gradient = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
                    )
                )
                set.fillDrawable = gradient
            }
            com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.SCATTER -> {
                set.mode = LineDataSet.Mode.LINEAR
                set.setDrawFilled(false)
                set.lineWidth = 0f
                set.setDrawCircles(true)
                set.circleRadius = 4.5f
                set.setCircleColor(color)
            }
            else -> {
                set.mode = LineDataSet.Mode.LINEAR
                set.setDrawFilled(false)
            }
        }
        
        // 4. Interaction safeguards
        set.setDrawValues(false)
        set.isHighlightEnabled = true
        set.setDrawHorizontalHighlightIndicator(false)
        set.setDrawVerticalHighlightIndicator(true)
        set.highlightLineWidth = 1.2f
        set.highLightColor = color
    }

    /**
     * Applies professional styling to a BarDataSet.
     */
    fun applyDataSetSafeguards(
        set: BarDataSet, 
        colorHex: String?
    ) {
        val color = try { Color.parseColor(colorHex ?: "#2196F3") } catch (e: Exception) { Color.BLUE }
        set.color = color
        set.setDrawValues(false)
        set.isHighlightEnabled = true
        set.highLightColor = Color.BLACK
        set.highLightAlpha = 120
    }

    /**
     * Applies physical bounds and performance safeguards to the chart.
     * Guards 11-30: Performance & UX
     */
    fun applyChartSafeguards(chart: LineChart, axisColor: Int, gridColor: Int) {
        println("!!! AGENT_DEBUG: applyChartSafeguards for Chart=${chart.hashCode()}")
        chart.apply {
            // Performance
            setHardwareAccelerationEnabled(true)
            description.isEnabled = false
            legend.isEnabled = false
            setClipChildren(false)
            setClipToPadding(false)
            
            // Interaction
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDoubleTapToZoomEnabled(false)
            
            // X-Axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = axisColor
                setDrawGridLines(true)
                this.gridColor = gridColor
                setLabelCount(6, true)
                setAvoidFirstLastClipping(false)
                setCenterAxisLabels(false)
            }
            
            // Y-Axis
            axisLeft.apply {
                textColor = axisColor
                setDrawGridLines(true)
                this.gridColor = gridColor
                spaceTop = 15f
                spaceBottom = 15f
            }
            axisRight.isEnabled = false
            
            // Offsets
            setExtraOffsets(24f, 32f, 24f, 16f)
            
            // Empty state
            setNoDataText("No telemetry data available")
            setNoDataTextColor(axisColor)
        }
    }
}

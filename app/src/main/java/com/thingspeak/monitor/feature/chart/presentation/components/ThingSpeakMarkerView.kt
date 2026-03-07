package com.thingspeak.monitor.feature.chart.presentation.components

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.thingspeak.monitor.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom marker (tooltip) for MPAndroidChart.
 * Clamped to chart VIEW bounds (= Canvas clip bounds) so the tooltip
 * is always fully visible and never clipped by the parent Compose Card.
 */
class ThingSpeakMarkerView(
    context: Context,
    private val isDaily: Boolean,
    var baselineX: Long = 0L,
    var timeScale: Float = 1f
) : MarkerView(context, R.layout.layout_chart_marker) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        val instant = Instant.ofEpochSecond((e.x * timeScale).toLong() + baselineX)
        val timeStr = dateTimeFormatter.format(instant)
        
        android.util.Log.d("ChartMarker", "refreshContent: Entry=(${e.x}, ${e.y}) | Time=$timeStr")
        tvContent.text = context.getString(R.string.chart_marker_content, e.y, timeStr)

        super.refreshContent(e, highlight)
    }

    override fun draw(canvas: android.graphics.Canvas, posX: Float, posY: Float) {
        val offset = getOffsetForDrawingAtPoint(posX, posY)
        android.util.Log.d("ChartMarker", "DRAW: Point=($posX,$posY) offset=(${offset.x},${offset.y}) Size=(${width}x${height})")
        super.draw(canvas, posX, posY)
    }

    override fun getOffset(): MPPointF {
        // Default: centered horizontally, 20px ABOVE the point to avoid being hidden by finger
        return MPPointF((-(width / 2)).toFloat(), (-height - 20f))
    }

    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val markerW = width.toFloat()
        val markerH = height.toFloat()
        
        // Get the CHART VIEW dimensions (= Canvas clip boundaries).
        // This is what Android actually clips to when drawing.
        val chart = chartView
        val viewW: Float
        val viewH: Float
        if (chart != null && chart.width > 0 && chart.height > 0) {
            viewW = chart.width.toFloat()
            viewH = chart.height.toFloat()
        } else {
            val m = context.resources.displayMetrics
            viewW = m.widthPixels.toFloat()
            viewH = m.heightPixels.toFloat()
        }

        val pad = 10f // Increased safety padding from view edge

        // Start with default offset (centered 20px above point)
        var x = -(markerW / 2f)
        var y = -markerH - 20f

        // --- CLAMP X ---
        // Right edge
        if (posX + x + markerW > viewW - pad) {
            x = viewW - posX - markerW - pad
        }
        // Left edge
        if (posX + x < pad) {
            x = -posX + pad
        }

        // --- CLAMP Y ---
        // If marker would go above chart view top → flip BELOW point (with gap)
        if (posY + y < pad) {
            y = 20f + 10f  // 30px gap below the point if it flips
        }
        // If marker now overflows bottom → push it up
        if (posY + y + markerH > viewH - pad) {
            y = viewH - posY - markerH - pad
        }
        // Final safety: if STILL above top after all adjustments, clamp to top
        if (posY + y < pad) {
            y = pad - posY
        }

        val absLeft = posX + x
        val absTop = posY + y
        val absRight = absLeft + markerW
        val absBottom = absTop + markerH
        val cvNull = chart == null
        android.util.Log.d("ChartMarker", "VIEW:(${viewW}x${viewH}) cvNull=$cvNull | Point:($posX,$posY) | MarkerAbs:($absLeft,$absTop,$absRight,$absBottom) | Offset:($x,$y)")

        return MPPointF(x, y)
    }
}

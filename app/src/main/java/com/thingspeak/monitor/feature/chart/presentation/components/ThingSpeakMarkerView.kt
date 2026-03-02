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
 */
class ThingSpeakMarkerView(
    context: Context,
    private val isDaily: Boolean,
    var baselineX: Long = 0L,
    var timeScale: Float = 1f
) : MarkerView(context, R.layout.layout_chart_marker) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        // Re-add baseline to the offset value to get the actual timestamp
        val instant = Instant.ofEpochSecond((e.x * timeScale).toLong() + baselineX)
        val timeStr = if (isDaily) timeFormatter.format(instant) else dateFormatter.format(instant)
        
        tvContent.text = context.getString(R.string.chart_marker_content, e.y, timeStr)

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}

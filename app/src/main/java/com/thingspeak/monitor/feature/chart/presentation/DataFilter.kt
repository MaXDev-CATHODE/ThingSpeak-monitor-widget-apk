package com.thingspeak.monitor.feature.chart.presentation

/**
 * Data processing filters for charts, modeled after ThingView.
 */
enum class DataFilter {
    NONE,
    MEDIAN,
    MEAN,
    ROUND,
    SUM
}

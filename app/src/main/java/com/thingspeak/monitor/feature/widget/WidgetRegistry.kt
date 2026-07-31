package com.thingspeak.monitor.feature.widget

import androidx.glance.appwidget.GlanceAppWidget

object WidgetRegistry {
    val ALL_CLASSES: List<Class<out GlanceAppWidget>> = listOf(
        ThingSpeakGlanceWidget::class.java,
        ValueGridWidget::class.java
    )

    val FACTORIES: Map<Class<out GlanceAppWidget>, () -> GlanceAppWidget> = mapOf(
        ThingSpeakGlanceWidget::class.java to { ThingSpeakGlanceWidget() },
        ValueGridWidget::class.java to { ValueGridWidget() }
    )

    val CHART_WIDGET_CLASSES: Set<Class<out GlanceAppWidget>> = setOf(
        ThingSpeakGlanceWidget::class.java
    )

    fun isChartWidget(cls: Class<out GlanceAppWidget>): Boolean =
        cls in CHART_WIDGET_CLASSES

    fun create(cls: Class<out GlanceAppWidget>): GlanceAppWidget =
        FACTORIES[cls]?.invoke()
            ?: throw IllegalArgumentException("Unknown widget class: $cls")
}
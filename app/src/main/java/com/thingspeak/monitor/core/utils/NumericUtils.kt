package com.thingspeak.monitor.core.utils

/**
 * Safely converts a nullable string to a Double.
 * ThingSpeak might send "NaN" or "null" as strings.
 */
fun String?.safeToDouble(): Double? {
    if (this == null) return null
    val floatValue = this.toFloatOrNull()
    return if (floatValue != null && !floatValue.isNaN()) {
        floatValue.toDouble()
    } else null
}

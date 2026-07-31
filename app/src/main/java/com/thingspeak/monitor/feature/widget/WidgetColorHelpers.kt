package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.content.res.Configuration
import java.io.ByteArrayOutputStream

fun isSystemDarkMode(context: Context): Boolean {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMode == Configuration.UI_MODE_NIGHT_YES
}

fun darkModeAutoBgColor(data: WidgetData, context: Context): String? {
    val hex = data.bgColorHex
    if (isSystemDarkMode(context) && hex != null) {
        val color = try { android.graphics.Color.parseColor(hex) } catch (_: Exception) { null }
        if (color != null && isColorDark(color).not()) {
            val darkColor = try {
                context.resources.getColor(android.R.color.background_dark, null)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#212121")
            }
            return String.format("#%06X", 0xFFFFFF and darkColor)
        }
    }
    return hex
}

fun resolveSystemAwareBackground(
    prefHex: String?,
    isDarkMode: Boolean,
    context: Context,
    colorMode: String? = WidgetPrefsKeys.COLOR_MODE_CUSTOM
): Int {
    if (colorMode == WidgetPrefsKeys.COLOR_MODE_SYSTEM) {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val accentRes = if (isDarkMode)
                    android.R.color.system_accent1_200
                else
                    android.R.color.system_accent1_500
                context.resources.getColor(accentRes, context.theme)
            } catch (_: Exception) {
                if (isDarkMode) android.graphics.Color.parseColor("#212121")
                else android.graphics.Color.parseColor("#FFFFFF")
            }
        } else {
            if (isDarkMode) android.graphics.Color.parseColor("#212121")
            else android.graphics.Color.parseColor("#FFFFFF")
        }
    }
    return try {
        android.graphics.Color.parseColor(prefHex ?: "#FFFFFF")
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }
}

fun darkModeAutoTextColor(data: WidgetData, isDarkBg: Boolean): androidx.compose.ui.graphics.Color {
    val tc = data.textColor
    if (tc != null && tc.startsWith("#")) {
        return try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(tc)) }
            catch (_: Exception) { if (isDarkBg) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black }
    }
    return if (isDarkBg) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
}

fun isColorDark(color: Int): Boolean {
    val darkness = 1 - (0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)) / 255
    return darkness >= 0.5
}

fun bitmapToBase64(bitmap: android.graphics.Bitmap?, quality: Int = 90): String? {
    if (bitmap == null) return null
    return try {
        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, quality, stream)
        android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }
}
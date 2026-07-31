package com.thingspeak.monitor.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * File-based cache for widget chart bitmaps.
 * Replaces the previous approach of storing large Base64 strings in DataStore,
 * which degraded performance for complex charts.
 *
 * Files are stored in the app's internal cache directory and are automatically
 * cleaned by the system when disk space is low.
 */
object WidgetChartCache {

    private const val CHART_DIR = "widget_charts"
    private const val TAG = "ChartCache"

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, CHART_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun chartFile(context: Context, appWidgetId: Int): File =
        File(cacheDir(context), "chart_${appWidgetId}.png")

    /**
     * Saves a chart bitmap to the internal cache.
     * @return the absolute file path, or null if save failed.
     */
    fun save(context: Context, appWidgetId: Int, bitmap: Bitmap): String? {
        return try {
            val file = chartFile(context, appWidgetId)
            file.outputStream().use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, fos)
            }
            Log.d(TAG, "Chart saved for widget $appWidgetId (${file.length()} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chart for $appWidgetId", e)
            null
        }
    }

    /**
     * Loads a chart bitmap from the cache file.
     * @param filePath absolute path to the chart file.
     */
    fun load(filePath: String?): Bitmap? {
        if (filePath == null) return null
        return try {
            val file = File(filePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(filePath)
            } else {
                Log.w(TAG, "Chart file not found: $filePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chart from $filePath", e)
            null
        }
    }

    /**
     * Removes all chart files for a widget.
     */
    fun clear(context: Context, appWidgetId: Int) {
        try {
            chartFile(context, appWidgetId).delete()
            Log.d(TAG, "Chart cache cleared for $appWidgetId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chart for $appWidgetId", e)
        }
    }

    /**
     * Cleans all cached chart files.
     */
    private fun clearAll(context: Context) {
        try {
            cacheDir(context).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all charts", e)
        }
    }
}
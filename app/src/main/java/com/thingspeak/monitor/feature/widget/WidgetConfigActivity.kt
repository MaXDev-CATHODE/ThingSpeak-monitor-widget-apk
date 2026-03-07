package com.thingspeak.monitor.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.worker.DataSyncWorker
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import javax.inject.Inject

/**
 * Configuration activity launched when the user adds a ThingSpeak widget.
 *
 * Sets [Activity.RESULT_CANCELED] immediately so that pressing back
 * without saving does not add the widget. On successful save, persists
 * channel data, updates the widget, and finishes with [Activity.RESULT_OK].
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var channelPreferences: ChannelPreferences

    @Inject
    lateinit var widgetBindingRepository: WidgetBindingRepository

    @Inject
    lateinit var repository: com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        scope.launch {
            // ... (loading initial state as before) ...
            val glanceId = findGlanceId(appWidgetId)

            val prefs = if (glanceId != null) {
                getAppWidgetState<Preferences>(this@WidgetConfigActivity, WidgetPreferencesStateDefinition, glanceId)
            } else null
            
            val savedChannelId = prefs?.get(longPreferencesKey("channel_id"))
            val savedBgColor = prefs?.get(stringPreferencesKey("bg_color"))
            val savedTransparency = prefs?.get(floatPreferencesKey("transparency"))
            val savedFontSize = prefs?.get(intPreferencesKey("font_size"))
            val savedVisibleFields = prefs?.get(stringSetPreferencesKey("visible_fields"))?.map { it.toInt() }?.toSet()
            val savedChartField = prefs?.get(intPreferencesKey("chart_field"))
            val savedIsGlass = prefs?.get(booleanPreferencesKey("is_glass"))

            // Initial snapshot to find the saved settings
            val initialChannels = channelPreferences.observe().first()
            val existingChannel = savedChannelId?.let { idVal ->
                initialChannels.find { it.id == idVal }
            }

            setContent {
                val allChannels by channelPreferences.observe().collectAsState(initial = initialChannels)
                var isSaving by remember { mutableStateOf(false) }
                ThingSpeakMonitorTheme {
                    WidgetConfigScreen(
                        initialChannelId = existingChannel?.id,
                        initialApiKey = existingChannel?.apiKey,
                        initialChannelName = existingChannel?.name,
                        initialBgColorHex = savedBgColor ?: existingChannel?.widgetBgColorHex,
                        initialTransparency = savedTransparency ?: existingChannel?.widgetTransparency,
                        initialFontSize = savedFontSize ?: existingChannel?.widgetFontSize,
                        initialVisibleFields = savedVisibleFields ?: existingChannel?.widgetVisibleFields,
                        initialChartField = savedChartField,
                        initialIsGlass = savedIsGlass,
                        isSaving = isSaving,
                        availableChannels = allChannels,
                        onRefreshRequest = { channelId, key ->
                            scope.launch {
                                try {
                                    repository.refreshFeed(channelId, key)
                                } catch (e: Exception) {
                                    android.util.Log.e("WidgetConfig", "Failed to refresh metadata for $channelId", e)
                                }
                            }
                        },
                        onSave = { channelId, apiKey, channelName, bgColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan ->
                            isSaving = true
                            onChannelSaved(appWidgetId, channelId, apiKey, channelName, bgColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan, savedChannelId)
                        },
                    )
                }
            }
        }
    }

    private fun onChannelSaved(
        appWidgetId: Int,
        channelId: Long,
        apiKey: String,
        channelName: String,
        widgetBgColorHex: String?,
        widgetTransparency: Float,
        widgetFontSize: Int,
        widgetVisibleFields: Set<Int>,
        chartField: Int,
        isGlass: Boolean,
        chartTimespan: Int,
        savedChannelId: Long?
    ) {
        scope.launch {
            try {
                android.util.Log.d("SNIPER_V5", "onChannelSaved triggered for channel $channelId, widget $appWidgetId")
                
                // 1. Save Channel Preferences (Persistent)
                val existingChannels = channelPreferences.observe().first()
                val existing = existingChannels.find { it.id == channelId }
                
                val updatedChannel = (existing ?: ChannelPreferences.SavedChannel(id = channelId, name = channelName)).copy(
                    name = channelName.ifBlank { existing?.name ?: "Channel $channelId" },
                    apiKey = apiKey.takeIf { it.isNotBlank() },
                    widgetBgColorHex = widgetBgColorHex,
                    widgetTransparency = widgetTransparency,
                    widgetFontSize = widgetFontSize,
                    widgetVisibleFields = widgetVisibleFields,
                    isGlassmorphismEnabled = isGlass,
                    chartField = chartField,
                    chartProcessingPeriod = chartTimespan
                )
                channelPreferences.save(updatedChannel)
                android.util.Log.d("NUCLEAR_V8", "1. Channel prefs saved for $channelId")

                // 2. CRITICAL SYNC BINDING (NUCLEAR V8)
                // We WAIT for this to finish before allowing the activity to close.
                widgetBindingRepository.saveBinding(appWidgetId, channelId)
                android.util.Log.d("NUCLEAR_V8", "2. Binding DB saved synchronicly: $appWidgetId -> $channelId")

                // 3. Atomic Background Save (survives activity finish)
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                val appContext = applicationContext
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    try {
                        android.util.Log.e("NUCLEAR_V8", ">>> STARTING ASYNC SYNC V8 for $appWidgetId")

                        // Glance State (Best Effort)
                        val gId = findGlanceId(appWidgetId)
                        if (gId != null) {
                            updateAppWidgetState(appContext, WidgetPreferencesStateDefinition, gId) { p ->
                                p.toMutablePreferences().apply {
                                    this[longPreferencesKey("channel_id")] = channelId
                                    this[stringPreferencesKey("bg_color")] = widgetBgColorHex ?: "#FFFFFF"
                                    this[floatPreferencesKey("transparency")] = widgetTransparency
                                    this[intPreferencesKey("font_size")] = widgetFontSize
                                    this[stringSetPreferencesKey("visible_fields")] = widgetVisibleFields.map { it.toString() }.toSet()
                                    this[intPreferencesKey("chart_field")] = chartField
                                    this[booleanPreferencesKey("is_glass")] = isGlass
                                    this[intPreferencesKey("chart_timespan")] = chartTimespan
                                    this[booleanPreferencesKey("is_refreshing")] = true
                                }
                            }
                            android.util.Log.d("NUCLEAR_V8", "Async: DataStore updated for $appWidgetId")
                        }
                        
                        ThingSpeakGlanceWidget().updateAll(appContext)

                        // ULTIMATE SYSTEM SIGNAL
                        val updateIntent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                            setPackage(packageName)
                        }
                        appContext.sendBroadcast(updateIntent)
                        android.util.Log.d("NUCLEAR_V8", "Async: System signals sent.")

                        // Sync
                        com.thingspeak.monitor.core.worker.DataSyncWorker.runOnce(appContext)
                        android.util.Log.e("NUCLEAR_V8", "Async: Worker enqueued.")
                    } catch (e: Exception) {
                        android.util.Log.e("NUCLEAR_V8", "Async: FATAL ERROR", e)
                        ThingSpeakGlanceWidget().updateAll(appContext)
                    }
                }

                // 4. Finalize Activity ON MAIN THREAD
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    setResult(android.app.Activity.RESULT_OK, Intent().apply {
                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    })
                    
                    android.util.Log.e("NUCLEAR_V8", ">>> RESULT_OK sent to system for $appWidgetId. Finishing.")
                    finish()
                }

            } catch (e: Exception) {
                android.util.Log.e("NUCLEAR_V8", "FATAL save error", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private suspend fun updateGlanceWidget(appWidgetId: Int) {
        try {
            val glanceId = GlanceAppWidgetManager(this)
                .getGlanceIdBy(appWidgetId)
            ThingSpeakGlanceWidget().update(this, glanceId)
        } catch (_: Exception) {
            // Widget may not yet be placed; ignore gracefully
        }
    }

    private suspend fun findGlanceId(appWidgetId: Int): androidx.glance.GlanceId? {
        var retries = 5
        while (retries > 0) {
            try {
                val manager = GlanceAppWidgetManager(this)
                // Strategy 1: Official way
                val officialId = manager.getGlanceIdBy(appWidgetId)
                if (officialId != null) {
                    android.util.Log.d("WidgetConfig", "Found GlanceId via official way for $appWidgetId")
                    return officialId
                }
                
                // Strategy 2: Search all known widget instances
                val foundId = manager.getGlanceIds(ThingSpeakGlanceWidget::class.java).find { 
                    manager.getAppWidgetId(it) == appWidgetId 
                } ?: manager.getGlanceIds(ValueGridWidget::class.java).find { 
                    manager.getAppWidgetId(it) == appWidgetId 
                }
                
                if (foundId != null) {
                    android.util.Log.d("WidgetConfig", "Found GlanceId via exhaustive search for $appWidgetId")
                    return foundId
                }
            } catch (e: Exception) {
                android.util.Log.e("WidgetConfig", "Error searching for GlanceId for $appWidgetId", e)
            }
            
            android.util.Log.w("WidgetConfig", "GlanceId not found for $appWidgetId, retrying in 100ms... ($retries left)")
            kotlinx.coroutines.delay(100)
            retries--
        }
        android.util.Log.e("WidgetConfig", "FAILED to find GlanceId for $appWidgetId after all retries")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

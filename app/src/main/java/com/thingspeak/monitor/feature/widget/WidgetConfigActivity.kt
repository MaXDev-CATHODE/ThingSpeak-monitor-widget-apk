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
                        onSave = { channelId, apiKey, channelName, bgColor, transparency, fontSize, visibleFields, chartField, isGlass ->
                            isSaving = true
                            onChannelSaved(appWidgetId, channelId, apiKey, channelName, bgColor, transparency, fontSize, visibleFields, chartField, isGlass, savedChannelId)
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
        savedChannelId: Long?
    ) {
        scope.launch {
            try {
                // Ensure save is not interrupted by activity destruction
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val glanceId = findGlanceId(appWidgetId)
                    android.util.Log.d("WidgetConfig", "Atomic save start: ch=$channelId, bg=$widgetBgColorHex, glanceId=$glanceId")
                    
                    // Fetch existing channel to preserve fieldNames and other metadata
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
                        chartField = chartField
                    )
                    
                    channelPreferences.save(updatedChannel)

                    // DETECT IF DATA REFRESH IS NEEDED
                    val isNewChannelForWidget = channelId != savedChannelId
                    val apiKeysDiffer = (existing?.apiKey ?: "") != (apiKey ?: "")
                    val connectivityChanged = existing == null || isNewChannelForWidget || apiKeysDiffer

                    android.util.Log.d("WidgetConfig", "Save logic: connectivityChanged=$connectivityChanged (isNew=$isNewChannelForWidget, keyDiff=$apiKeysDiffer). oldCh=$savedChannelId, newCh=$channelId")

                    if (glanceId != null) {
                        try {
                            updateAppWidgetState(this@WidgetConfigActivity, WidgetPreferencesStateDefinition, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    this[longPreferencesKey("channel_id")] = channelId
                                    if (widgetBgColorHex != null) {
                                        this[stringPreferencesKey("bg_color")] = widgetBgColorHex
                                    } else {
                                        remove(stringPreferencesKey("bg_color"))
                                    }
                                    this[floatPreferencesKey("transparency")] = widgetTransparency
                                    this[intPreferencesKey("font_size")] = widgetFontSize
                                    this[stringSetPreferencesKey("visible_fields")] = widgetVisibleFields.map { it.toString() }.toSet()
                                    this[intPreferencesKey("chart_field")] = chartField
                                    this[booleanPreferencesKey("is_glass")] = isGlass
                                    
                                    // Only show loading if we are actually starting a sync
                                    if (connectivityChanged) {
                                        this[booleanPreferencesKey("is_refreshing")] = true
                                    }
                                }
                            }
                            ThingSpeakGlanceWidget().update(this@WidgetConfigActivity, glanceId)
                            
                            // Force update via classic Intent for certainty
                            val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                setClass(this@WidgetConfigActivity, WidgetReceiver::class.java)
                                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                            }
                            sendBroadcast(intent)
                            
                            android.util.Log.d("WidgetConfig", "Visual styles saved, Glance update/updateAll/Broadcast triggered")
                        } catch (e: Exception) {
                            android.util.Log.e("WidgetConfig", "Failed to update Glance state", e)
                        }
                    }

                    if (connectivityChanged) {
                        // Trigger data refresh in background via WorkManager (persistent)
                        DataSyncWorker.runOnce(this@WidgetConfigActivity.applicationContext)
                        android.util.Log.d("WidgetConfig", "Sync worker triggered due to connectivity changes")
                    } else {
                        android.util.Log.d("WidgetConfig", "Skipping sync worker - only visual changes")
                    }
                }

                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                })
                
                // Give Glance a moment to handle the update before finishing
                kotlinx.coroutines.delay(300)
                
                finish()
                android.util.Log.d("WidgetConfig", "Activity finished (delayed 300ms)")
            } catch (e: Exception) {
                android.util.Log.e("WidgetConfig", "Error saving widget config", e)
                finish()
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
        return try {
            val manager = GlanceAppWidgetManager(this)
            // Strategy 1: Official way
            val officialId = manager.getGlanceIdBy(appWidgetId)
            if (officialId != null) return officialId
            
            // Strategy 2: Search all known widget instances
            manager.getGlanceIds(ThingSpeakGlanceWidget::class.java).find { 
                manager.getAppWidgetId(it) == appWidgetId 
            } ?: manager.getGlanceIds(ValueGridWidget::class.java).find { 
                manager.getAppWidgetId(it) == appWidgetId 
            }
        } catch (e: Exception) {
            android.util.Log.e("WidgetConfig", "Failed to find glanceId for $appWidgetId", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import com.thingspeak.monitor.core.worker.DataSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository

@AndroidEntryPoint
class ValueGridWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var channelPreferences: ChannelPreferences

    @Inject
    lateinit var repository: ChannelRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isConfigurationDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ThingSpeakMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val savedChannels by channelPreferences.observe().collectAsState(initial = emptyList())
                    val coroutineScope = rememberCoroutineScope()
                    var isSaving by remember { mutableStateOf(false) }

                    // We will reuse WidgetConfigScreen but probably modify it later to hide Chart Selection totally
                    // or just use it as is since Chart Selection was removed for now
                    WidgetConfigScreen(
                        isSaving = isSaving,
                        isGridMode = true,
                        availableChannels = savedChannels,
                        onRefreshRequest = { channelId, key ->
                            coroutineScope.launch {
                                try {
                                    repository.refreshFeed(channelId, key)
                                } catch (e: Exception) {
                                    android.util.Log.e("ValueGridWidgetConfig", "Failed to refresh metadata for $channelId", e)
                                }
                            }
                        },
                        onSave = { channelId, apiKey, name, bgColor, transparency, fontSize, visibleFields, chartField, isGlass ->
                            isSaving = true
                            coroutineScope.launch {
                                // 1. Save or Update Channel in DataStore
                                val existingChannel = savedChannels.find { it.id == channelId }
                                val updatedChannel = existingChannel?.copy(
                                    apiKey = apiKey.takeIf { it.isNotBlank() },
                                    name = name,
                                    widgetBgColorHex = bgColor,
                                    widgetTransparency = transparency,
                                    widgetFontSize = fontSize,
                                    widgetVisibleFields = visibleFields,
                                    isGlassmorphismEnabled = isGlass,
                                    chartField = chartField
                                ) ?: ChannelPreferences.SavedChannel(
                                    id = channelId,
                                    apiKey = apiKey.takeIf { it.isNotBlank() },
                                    name = name,
                                    widgetBgColorHex = bgColor,
                                    widgetTransparency = transparency,
                                    widgetFontSize = fontSize,
                                    widgetVisibleFields = visibleFields,
                                    isGlassmorphismEnabled = isGlass,
                                    chartField = chartField
                                )
                                channelPreferences.save(updatedChannel)

                                // 2. Associate exact GlanceId with this configuration
                                try {
                                    val glanceId = findGlanceId(appWidgetId)
                                    
                                    val safeBgColor = bgColor ?: "#FFFFFF"
                                    
                                    if (glanceId != null) {
                                        // Make sure it uses WidgetPreferencesStateDefinition (the internal one for widgets)
                                        updateAppWidgetState(this@ValueGridWidgetConfigActivity, WidgetPreferencesStateDefinition, glanceId) { prefs ->
                                            prefs.toMutablePreferences().apply {
                                                this[longPreferencesKey("channel_id")] = channelId
                                                this[stringPreferencesKey("api_key")] = apiKey
                                                this[stringPreferencesKey("channel_name")] = name
                                                this[stringPreferencesKey("bg_color")] = safeBgColor
                                                this[floatPreferencesKey("transparency")] = transparency
                                                this[intPreferencesKey("font_size")] = fontSize
                                                this[stringSetPreferencesKey("visible_fields")] = visibleFields.map { it.toString() }.toSet()
                                                this[intPreferencesKey("chart_field")] = chartField
                                                this[booleanPreferencesKey("is_glass")] = isGlass
                                                this[booleanPreferencesKey("is_refreshing")] = true
                                            }
                                        }
                                        ValueGridWidget().update(this@ValueGridWidgetConfigActivity, glanceId)
                                    } else {
                                        ValueGridWidget().updateAll(this@ValueGridWidgetConfigActivity)
                                    }
                                    
                                    // Send broadcast for legacy reasons or launcher updates
                                    val intent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                        setClass(this@ValueGridWidgetConfigActivity, ValueGridWidgetReceiver::class.java)
                                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                    }
                                    sendBroadcast(intent)
                                    
                                } catch (e: Exception) {
                                    android.util.Log.e("ValueGridWidgetConfig", "Failed to find GlanceId for configuration", e)
                                    ValueGridWidget().updateAll(this@ValueGridWidgetConfigActivity)
                                }

                                // 3. Sync data
                                DataSyncWorker.runOnce(applicationContext)

                                isConfigurationDone = true
                                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, result)
                                
                                // Give a moment for state to persist
                                kotlinx.coroutines.delay(300)
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private suspend fun findGlanceId(appWidgetId: Int): androidx.glance.GlanceId? {
        return try {
            val manager = GlanceAppWidgetManager(this)
            // Strategy 1: Official way
            val officialId = manager.getGlanceIdBy(appWidgetId)
            if (officialId != null) return officialId
            
            // Strategy 2: Search ValueGridWidget instances
            manager.getGlanceIds(ValueGridWidget::class.java).find { 
                manager.getAppWidgetId(it) == appWidgetId 
            }
        } catch (e: Exception) {
            android.util.Log.e("ValueGridWidgetConfig", "Failed to find glanceId for $appWidgetId", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isConfigurationDone && !isChangingConfigurations) {
            setResult(RESULT_CANCELED)
        }
    }
}

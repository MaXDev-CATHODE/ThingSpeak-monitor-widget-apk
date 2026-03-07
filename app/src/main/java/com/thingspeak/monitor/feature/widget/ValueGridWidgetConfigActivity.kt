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
    lateinit var widgetBindingRepository: WidgetBindingRepository

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
            android.util.Log.d("SNIPER_CONFIG", "Activity started with appWidgetId=$appWidgetId")
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
                        onSave = { channelId, apiKey, name, bgColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan ->
                            isSaving = true
                            coroutineScope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    try {
                                        android.util.Log.d("SNIPER_V5", "onSave triggered for channel $channelId, widget $appWidgetId")

                                        // 1. Save Channel Preferences
                                        val existingChannel = savedChannels.find { it.id == channelId }
                                        val updatedChannel = (existingChannel ?: ChannelPreferences.SavedChannel(id = channelId, name = name)).copy(
                                            apiKey = apiKey.takeIf { it.isNotBlank() },
                                            name = name,
                                            widgetBgColorHex = bgColor,
                                            widgetTransparency = transparency,
                                            widgetFontSize = fontSize,
                                            widgetVisibleFields = visibleFields,
                                            isGlassmorphismEnabled = isGlass,
                                            chartField = chartField,
                                            chartProcessingPeriod = chartTimespan
                                        )
                                        channelPreferences.save(updatedChannel)
                                        android.util.Log.d("NUCLEAR_V8", "1. Channel prefs saved for $channelId")

                                        // 2. CRITICAL SYNC BINDING (NUCLEAR V8)
                                        // We WAIT for this to finish before allowing the activity to close.
                                        // This ensures the widget will see the binding when it refreshes.
                                        widgetBindingRepository.saveBinding(appWidgetId, channelId)
                                        android.util.Log.d("NUCLEAR_V8", "2. Binding DB saved synchronicly: $appWidgetId -> $channelId")

                                        // 3. Launch heavy/async background tasks
                                        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                                        val appContext = applicationContext
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                                            try {
                                                android.util.Log.e("NUCLEAR_V8", ">>> STARTING ASYNC SYNC V8 for $appWidgetId")
                                                
                                                // Update Glance State (Best Effort)
                                                val gId = findGlanceId(appWidgetId)
                                                if (gId != null) {
                                                    updateAppWidgetState(appContext, WidgetPreferencesStateDefinition, gId) { p ->
                                                        p.toMutablePreferences().apply {
                                                            this[longPreferencesKey("channel_id")] = channelId
                                                            this[stringPreferencesKey("api_key")] = apiKey ?: ""
                                                            this[stringPreferencesKey("channel_name")] = name
                                                            this[stringPreferencesKey("bg_color")] = bgColor ?: "#FFFFFF"
                                                            this[floatPreferencesKey("transparency")] = transparency
                                                            this[intPreferencesKey("font_size")] = fontSize
                                                            this[stringSetPreferencesKey("visible_fields")] = visibleFields.map { it.toString() }.toSet()
                                                            this[intPreferencesKey("chart_field")] = chartField
                                                            this[booleanPreferencesKey("is_glass")] = isGlass
                                                            this[intPreferencesKey("chart_timespan")] = chartTimespan
                                                            this[booleanPreferencesKey("is_refreshing")] = true
                                                        }
                                                    }
                                                    android.util.Log.d("NUCLEAR_V8", "Async: DataStore updated for $appWidgetId")
                                                }
                                                
                                                ValueGridWidget().updateAll(appContext)
                                                
                                                // SYSTEM SIGNAL
                                                val updateIntent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                                    setPackage(packageName)
                                                }
                                                appContext.sendBroadcast(updateIntent)
                                                android.util.Log.d("NUCLEAR_V8", "Async: System signals sent.")

                                                // Background Refresh
                                                repository.refreshFeed(channelId, apiKey)
                                                com.thingspeak.monitor.core.worker.DataSyncWorker.runOnce(appContext)
                                                android.util.Log.e("NUCLEAR_V8", "Async: Worker enqueued.")
                                            } catch (e: Exception) {
                                                android.util.Log.e("NUCLEAR_V8", "Async: FATAL ERROR", e)
                                                ValueGridWidget().updateAll(appContext)
                                            }
                                        }

                                        // 4. Finalize Activity ON MAIN THREAD
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isConfigurationDone = true
                                            val resultIntent = Intent().apply {
                                                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            }
                                            setResult(RESULT_OK, resultIntent)
                                            android.util.Log.e("NUCLEAR_V8", ">>> RESULT_OK sent to system. Finishing Activity.")
                                            finish()
                                        }

                                    } catch (e: Exception) {
                                        android.util.Log.e("NUCLEAR_V8", "FATAL onSave error", e)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            finish()
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
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
                    android.util.Log.d("ValueGridConfig", "Found GlanceId via official way for $appWidgetId")
                    return officialId
                }
                
                // Strategy 2: Search ValueGridWidget instances
                val foundId = manager.getGlanceIds(ValueGridWidget::class.java).find { 
                    manager.getAppWidgetId(it) == appWidgetId 
                }
                
                if (foundId != null) {
                    android.util.Log.d("ValueGridConfig", "Found GlanceId via exhaustive search for $appWidgetId")
                    return foundId
                }
            } catch (e: Exception) {
                android.util.Log.e("ValueGridConfig", "Error searching for GlanceId for $appWidgetId", e)
            }

            android.util.Log.w("ValueGridConfig", "GlanceId not found for $appWidgetId, retrying... ($retries left)")
            kotlinx.coroutines.delay(100)
            retries--
        }
        android.util.Log.e("ValueGridConfig", "FAILED to find GlanceId for $appWidgetId after all retries")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isConfigurationDone && !isChangingConfigurations) {
            setResult(RESULT_CANCELED)
        }
    }
}

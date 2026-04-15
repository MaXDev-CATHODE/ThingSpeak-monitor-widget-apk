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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.datastore.SavedChannel
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

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        scope.launch {
            val glanceId = findGlanceId(appWidgetId)
            val prefs = if (glanceId != null) getAppWidgetState<Preferences>(this@WidgetConfigActivity, WidgetPreferencesStateDefinition, glanceId) else null
            
            val savedChannelId = prefs?.get(longPreferencesKey("channel_id"))
            val savedBgColor = prefs?.get(stringPreferencesKey("bg_color"))
            val savedTextColor = prefs?.get(stringPreferencesKey("text_color"))
            val savedTransparency = prefs?.get(floatPreferencesKey("transparency"))
            val savedFontSize = prefs?.get(intPreferencesKey("font_size"))
            val savedIsGlass = prefs?.get(booleanPreferencesKey("is_glass"))
            val savedVisibleFields = prefs?.get(stringSetPreferencesKey("visible_fields"))?.mapNotNull { it.toIntOrNull() }?.toSet()
            
            var initialChannels: List<SavedChannel> = emptyList()
            channelPreferences.observe().take(1).collect { initialChannels = it }
            
            val initialAlertRules = if (savedChannelId != null) {
                repository.getAlertRules(savedChannelId, null)
            } else emptyList()

            val existing = savedChannelId?.let { idVal -> initialChannels.find { it.id == idVal } }

            setContent {
                val allChannels by channelPreferences.observe().collectAsState(initial = initialChannels)
                var isSaving by remember { mutableStateOf(false) }
                ThingSpeakMonitorTheme {
                    WidgetConfigScreen(
                        initialChannelId = existing?.id,
                        initialApiKey = existing?.apiKey,
                        initialChannelName = existing?.name,
                        initialAlertRules = initialAlertRules,
                        initialBgColorHex = savedBgColor ?: existing?.widgetBgColorHex,
                        initialTransparency = savedTransparency ?: existing?.widgetTransparency ?: 1.0f,
                        initialIsGlass = savedIsGlass ?: existing?.isGlassmorphismEnabled,
                        initialChartTimespan = existing?.chartProcessingPeriod,
                        initialChartTimespanStr = existing?.chartTimespan,
                        initialChartResults = existing?.chartResults ?: 60,
                        initialFontSize = savedFontSize ?: existing?.widgetFontSize ?: 12,
                        initialTextColorHex = savedTextColor ?: existing?.widgetTextColorHex,
                        initialVisibleFields = savedVisibleFields ?: existing?.widgetVisibleFields ?: emptySet(),
                        isSaving = isSaving,
                        availableChannels = allChannels,
                        onRefreshRequest = { chanId, key ->
                            scope.launch { 
                                val ch = allChannels.find { it.id == chanId }
                                try { repository.refreshFeed(chanId, key, chartTimespan = ch?.chartTimespan) } catch (e: Exception) {} 
                            }
                        },
                        onSave = { chanId, apiKey, chanName, bgColor, txtColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan, chartTimespanStr, chResults, alertRules ->
                            isSaving = true
                            onChannelSaved(appWidgetId, chanId, apiKey, chanName, bgColor, txtColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan, chartTimespanStr, chResults, alertRules)
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
        widgetTextColorHex: String?,
        widgetTransparency: Float,
        widgetFontSize: Int,
        widgetVisibleFields: Set<Int>,
        chartField: Int,
        isGlass: Boolean,
        chartTimespan: Int,
        chartTimespanStr: String,
        chartResultsCount: Int,
        alertRules: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>
    ) {
        scope.launch {
            try {
                // Unified Alerts: rules are global per channel (appWidgetId = null)
                repository.deleteGlobalAlertRules(channelId)
                alertRules.forEach { rule ->
                    repository.saveAlertRule(rule.copy(appWidgetId = null))
                }
                android.util.Log.d("TS_DEBUG", "Unified Alert rules saved for channel $channelId")
                // 1. Update Core Channel Info (API Key, Name Only)
                val existingChannel = channelPreferences.observe().first().find { it.id == channelId }
                val updatedChannel = (existingChannel ?: SavedChannel(id = channelId, name = channelName)).copy(
                    id = channelId,
                    name = channelName,
                    apiKey = apiKey,
                    lastSyncStatus = "NONE",
                    lastSyncTime = System.currentTimeMillis()
                )
                channelPreferences.save(updatedChannel)
                android.util.Log.d("NUCLEAR_TRACER", "Core Channel info saved (Visuals skipped for isolation)")

                widgetBindingRepository.saveBinding(appWidgetId, channelId)

                // Sync with Room DB for app UI consistency (Core Info Only)
                repository.observeChannel(channelId).first()?.let { roomChannel ->
                    repository.updateChannel(roomChannel.copy(
                        name = channelName,
                        apiKey = apiKey
                        // Visuals skipped to avoid interference with Main App (Agent 2.1 Fix)
                    ))
                }

                val appContext = applicationContext
                val gId = findGlanceId(appWidgetId)
                if (gId != null) {
                    updateAppWidgetState(appContext, WidgetPreferencesStateDefinition, gId) { p ->
                        p.toMutablePreferences().apply {
                            this[longPreferencesKey("channel_id")] = channelId
                            this[stringPreferencesKey("channel_name")] = channelName
                            this[stringPreferencesKey("bg_color")] = widgetBgColorHex ?: "#FFFFFF"
                            this[stringPreferencesKey("text_color")] = widgetTextColorHex ?: ""
                            this[floatPreferencesKey("transparency")] = widgetTransparency
                            this[intPreferencesKey("font_size")] = widgetFontSize
                            this[booleanPreferencesKey("is_glass")] = isGlass
                            this[intPreferencesKey("chart_results")] = chartResultsCount
                            this[stringSetPreferencesKey("visible_fields")] = widgetVisibleFields.map { it.toString() }.toSet()
                            // Clear stale chart bitmap so widget shows "Loading Chart..." until
                            // DataSyncWorker generates a fresh one for the newly selected channel
                            this.remove(stringPreferencesKey("chart_bitmap"))
                        }
                    }
                }
                
                ThingSpeakGlanceWidget().updateAll(appContext)
                ValueGridWidget().updateAll(appContext)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    setResult(Activity.RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                    finish()
                }
            } catch (e: Exception) { finish() }
        }
    }

    private suspend fun findGlanceId(appWidgetId: Int): androidx.glance.GlanceId? {
        return try {
            val manager = GlanceAppWidgetManager(this)
            manager.getGlanceIdBy(appWidgetId)
        } catch (e: Exception) { null }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

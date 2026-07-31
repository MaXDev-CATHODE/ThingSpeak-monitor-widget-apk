package com.thingspeak.monitor.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.first
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
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
    private val saveGuard = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastRefreshTimestamp = java.util.concurrent.atomic.AtomicLong(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        scope.launch {
            val glanceId = findWidgetGlanceId(this@WidgetConfigActivity, appWidgetId, widgetClasses = listOf(ThingSpeakGlanceWidget::class.java))
            val prefs = if (glanceId != null) getAppWidgetState<Preferences>(this@WidgetConfigActivity, WidgetPreferencesStateDefinition, glanceId) else null
            
            val savedChannelId = prefs?.get(WidgetPrefsKeys.KEY_CHANNEL_ID)
            val savedBgColor = prefs?.get(WidgetPrefsKeys.KEY_BG_COLOR)
            val savedTextColor = prefs?.get(WidgetPrefsKeys.KEY_TEXT_COLOR)
            val savedTransparency = prefs?.get(WidgetPrefsKeys.KEY_TRANSPARENCY)
            val savedFontSize = prefs?.get(WidgetPrefsKeys.KEY_FONT_SIZE)
            val savedIsGlass = prefs?.get(WidgetPrefsKeys.KEY_IS_GLASS)
            val savedBgColorMode = prefs?.get(WidgetPrefsKeys.KEY_BG_COLOR_MODE)
            val savedVisibleFields = prefs?.get(WidgetPrefsKeys.KEY_VISIBLE_FIELDS)?.mapNotNull { it.toIntOrNull() }?.toSet()
            
            var initialChannels = channelPreferences.observe().first()
            
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
                        initialBgColorMode = savedBgColorMode,
                        initialChartTimespan = existing?.chartProcessingPeriod,
                        initialChartTimespanStr = existing?.chartTimespan,
                        initialChartResults = existing?.chartResults ?: 60,
                        initialFontSize = savedFontSize ?: existing?.widgetFontSize ?: 12,
                        initialTextColorHex = savedTextColor ?: existing?.widgetTextColorHex,
                        initialVisibleFields = savedVisibleFields ?: existing?.widgetVisibleFields ?: emptySet(),
                        isSaving = isSaving,
                        availableChannels = allChannels,
                        onRefreshRequest = { chanId, key ->
                            val now = System.currentTimeMillis()
                            if (now - lastRefreshTimestamp.getAndSet(now) < 3000L) {
                                android.util.Log.d(WIDGET_LOG_TAG, "WidgetConfig: refresh debounced for $chanId")
                                return@WidgetConfigScreen
                            }
                            scope.launch { 
                                val ch = allChannels.find { it.id == chanId }
                                try { repository.refreshFeed(chanId, key, chartTimespan = ch?.chartTimespan) } catch (e: Exception) { 
                                    android.util.Log.e(WIDGET_LOG_TAG, "WidgetConfig: refreshFeed failed for $chanId", e) 
                                } 
                            }
                        },
                        onSave = { chanId, apiKey, chanName, bgColor, txtColor, transparency, fontSize, visibleFields, chartField, isGlass, colorMode, chartTimespan, chartTimespanStr, chResults, alertRules ->
                            if (!saveGuard.compareAndSet(false, true)) return@WidgetConfigScreen
                            isSaving = true
                            onChannelSaved(appWidgetId, chanId, apiKey, chanName, bgColor, txtColor, transparency, fontSize, visibleFields, chartField, isGlass, colorMode, chartTimespan, chartTimespanStr, chResults, alertRules)
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
        bgColorMode: String?,
        chartTimespan: Int,
        chartTimespanStr: String,
        chartResultsCount: Int,
        alertRules: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>
    ) {
        scope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext, com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
                )
                WidgetUpdateHelper.saveWidgetCoreConfig(
                    entryPoint = entryPoint,
                    channelPreferences = channelPreferences,
                    widgetBindingRepository = widgetBindingRepository,
                    appWidgetId = appWidgetId,
                    channelId = channelId,
                    apiKey = apiKey,
                    channelName = channelName,
                    alertRules = alertRules
                )

                val appContext = applicationContext
                val gId = findWidgetGlanceId(appContext, appWidgetId, widgetClasses = listOf(ThingSpeakGlanceWidget::class.java))
                if (gId != null) {
                    updateAppWidgetState(appContext, WidgetPreferencesStateDefinition, gId) { p ->
                        p.toMutablePreferences().apply {
                            this[WidgetPrefsKeys.KEY_CHANNEL_ID] = channelId
                            this[WidgetPrefsKeys.KEY_CHANNEL_NAME] = channelName
                            this[WidgetPrefsKeys.KEY_BG_COLOR] = widgetBgColorHex ?: "#FFFFFF"
                            this[WidgetPrefsKeys.KEY_TEXT_COLOR] = widgetTextColorHex ?: ""
                            this[WidgetPrefsKeys.KEY_TRANSPARENCY] = widgetTransparency
                            this[WidgetPrefsKeys.KEY_FONT_SIZE] = widgetFontSize
                            this[WidgetPrefsKeys.KEY_IS_GLASS] = isGlass
                            this[WidgetPrefsKeys.KEY_IS_REFRESHING] = true
                            this[WidgetPrefsKeys.KEY_CHART_RESULTS] = chartResultsCount
                            this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS] = widgetVisibleFields.map { it.toString() }.toSet()
                            this[WidgetPrefsKeys.KEY_WIDGET_VISUALS_CUSTOMIZED] = true
                            this[WidgetPrefsKeys.KEY_BG_COLOR_MODE] = bgColorMode ?: WidgetPrefsKeys.COLOR_MODE_CUSTOM
                            this[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] = false
                            this[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] = 0
                            // Clear stale chart bitmap so widget shows "Loading Chart..." until
                            // DataSyncWorker generates a fresh one for the newly selected channel
                            this.remove(WidgetPrefsKeys.KEY_CHART_BITMAP)
                            this.remove(WidgetPrefsKeys.KEY_CHART_FILE)
                        }
                    }
                }

                // Trigger refresh with 60s timeout protection via performWidgetRefreshAction
                // Replaces updateAll + implicit refresh to prevent isRefreshing stuck at true
                if (gId != null) {
                    performWidgetRefreshAction(
                        context = appContext,
                        glanceId = gId,
                        updateWidget = suspend { ThingSpeakGlanceWidget().update(appContext, gId) },
                        uniqueWorkPrefix = "config_refresh"
                    )
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    setResult(Activity.RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                    finish()
                }
            } catch (e: Exception) { finish() }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

package com.thingspeak.monitor.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val widgetClasses = listOf(ThingSpeakGlanceWidget::class.java)

        scope.launch {
            val savedPrefs = loadSavedWidgetPrefs(this@WidgetConfigActivity, appWidgetId, widgetClasses)
            val savedChannelId = savedPrefs.channelId
            val savedBgColor = savedPrefs.bgColor
            val savedTextColor = savedPrefs.textColor
            val savedTransparency = savedPrefs.transparency
            val savedFontSize = savedPrefs.fontSize
            val savedIsGlass = savedPrefs.isGlass
            val savedBgColorMode = savedPrefs.bgColorMode
            val savedVisibleFields = savedPrefs.visibleFields

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
                                try {
                                    repository.refreshFeed(chanId, key, chartTimespan = ch?.chartTimespan)
                                } catch (e: Exception) {
                                    android.util.Log.e(WIDGET_LOG_TAG, "WidgetConfig: refreshFeed failed for $chanId", e)
                                }
                            }
                        },
                        onSave = { chanId, apiKey, chanName, bgColor, txtColor, transparency, fontSize, visibleFields, _, isGlass, colorMode, _, _, chResults, alertRules ->
                            if (!saveGuard.compareAndSet(false, true)) return@WidgetConfigScreen
                            isSaving = true
                            scope.launch {
                                val gId = findWidgetGlanceId(applicationContext, appWidgetId, widgetClasses = widgetClasses)
                                saveWidgetConfigAndRefresh(
                                    context = applicationContext,
                                    appWidgetId = appWidgetId,
                                    glanceId = gId,
                                    channelId = chanId,
                                    channelName = chanName,
                                    apiKey = apiKey,
                                    bgColor = bgColor,
                                    textColor = txtColor,
                                    transparency = transparency,
                                    fontSize = fontSize,
                                    visibleFields = visibleFields,
                                    isGlass = isGlass,
                                    bgColorMode = colorMode,
                                    chartResultsCount = chResults,
                                    alertRules = alertRules,
                                    skipChartClear = false,
                                    channelPreferences = channelPreferences,
                                    widgetBindingRepository = widgetBindingRepository,
                                    updateWidget = { ThingSpeakGlanceWidget().update(applicationContext, gId!!) },
                                    onResult = {
                                        setResult(Activity.RESULT_OK, Intent().apply {
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                        })
                                        finish()
                                    }
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
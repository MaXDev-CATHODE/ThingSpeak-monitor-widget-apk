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
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ValueGridWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var channelPreferences: ChannelPreferences

    @Inject
    lateinit var widgetBindingRepository: WidgetBindingRepository

    @Inject
    lateinit var repository: ChannelRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val saveGuard = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastRefreshTimestamp = java.util.concurrent.atomic.AtomicLong(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val widgetClasses = listOf(ValueGridWidget::class.java)

        setContent {
            ThingSpeakMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val savedChannels by channelPreferences.observe().collectAsState(initial = emptyList())
                    var isSaving by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    var savedPrefs by remember { mutableStateOf<SavedWidgetPrefs?>(null) }
                    LaunchedEffect(appWidgetId) {
                        savedPrefs = loadSavedWidgetPrefs(
                            this@ValueGridWidgetConfigActivity, appWidgetId,
                            widgetClasses = widgetClasses
                        )
                    }

                    val savedChannelId = savedPrefs?.channelId
                    val savedBgColor = savedPrefs?.bgColor
                    val savedTextColor = savedPrefs?.textColor
                    val savedTransparency = savedPrefs?.transparency
                    val savedFontSize = savedPrefs?.fontSize
                    val savedIsGlass = savedPrefs?.isGlass
                    val savedBgColorMode = savedPrefs?.bgColorMode
                    val savedVisibleFields = savedPrefs?.visibleFields

                    val existing = savedChannelId?.let { idVal -> savedChannels.find { it.id == idVal } }

                    val initialAlertRules by if (savedChannelId != null) {
                        repository.observeAlertRules(savedChannelId, null).collectAsState(initial = emptyList())
                    } else {
                        remember { mutableStateOf(emptyList<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()) }
                    }

                    WidgetConfigScreen(
                        isSaving = isSaving,
                        isGridMode = true,
                        availableChannels = savedChannels,
                        onRefreshRequest = { channelId, key ->
                            val now = System.currentTimeMillis()
                            if (now - lastRefreshTimestamp.getAndSet(now) < 3000L) {
                                android.util.Log.d(WIDGET_LOG_TAG, "ValueGridConfig: refresh debounced for $channelId")
                                return@WidgetConfigScreen
                            }
                            coroutineScope.launch {
                                try {
                                    val ch = savedChannels.find { it.id == channelId }
                                    repository.refreshFeed(channelId, key, chartTimespan = ch?.chartTimespan)
                                } catch (e: Exception) {
                                    android.util.Log.e(WIDGET_LOG_TAG, "ValueGridWidgetConfig: refreshFeed failed for $channelId", e)
                                }
                            }
                        },
                        initialChannelId = existing?.id,
                        initialApiKey = existing?.apiKey,
                        initialChannelName = existing?.name,
                        initialBgColorHex = savedBgColor ?: existing?.widgetBgColorHex,
                        initialTransparency = savedTransparency ?: existing?.widgetTransparency ?: 1.0f,
                        initialIsGlass = savedIsGlass ?: existing?.isGlassmorphismEnabled,
                        initialBgColorMode = savedBgColorMode,
                        initialChartResults = existing?.chartResults ?: 60,
                        initialFontSize = savedFontSize ?: existing?.widgetFontSize ?: 12,
                        initialTextColorHex = savedTextColor ?: existing?.widgetTextColorHex,
                        initialVisibleFields = savedVisibleFields ?: existing?.widgetVisibleFields ?: emptySet(),
                        initialAlertRules = initialAlertRules,
                        onSave = { channelId, apiKey, name, bgColor, txtColor, transparency, fontSize, visibleFields, _, isGlass, colorMode, chResultsCount, alertRules ->
                            if (!saveGuard.compareAndSet(false, true)) return@WidgetConfigScreen
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    val gId = findWidgetGlanceId(applicationContext, appWidgetId, widgetClasses = widgetClasses)
                                    saveWidgetConfigAndRefresh(
                                        context = applicationContext,
                                        appWidgetId = appWidgetId,
                                        glanceId = gId,
                                        channelId = channelId,
                                        channelName = name,
                                        apiKey = apiKey,
                                        bgColor = bgColor,
                                        textColor = txtColor,
                                        transparency = transparency,
                                        fontSize = fontSize,
                                        visibleFields = visibleFields,
                                        isGlass = isGlass,
                                        bgColorMode = colorMode,
                                        chartResultsCount = chResultsCount,
                                        alertRules = alertRules,
                                        skipChartClear = true,
                                        channelPreferences = channelPreferences,
                                        widgetBindingRepository = widgetBindingRepository,
                                        updateWidget = { ValueGridWidget().update(applicationContext, gId!!) },
                                        onResult = {
                                            val resultIntent = Intent().apply {
                                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            }
                                            setResult(RESULT_OK, resultIntent)
                                            finish()
                                        }
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e(WIDGET_LOG_TAG, "FATAL onSave error", e)
                                    finish()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
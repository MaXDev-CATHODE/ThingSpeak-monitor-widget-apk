package com.thingspeak.monitor.feature.widget

import android.app.Activity
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
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import com.thingspeak.monitor.core.worker.DataSyncWorker
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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
            android.util.Log.d("TS_DEBUG", "Activity started with appWidgetId=$appWidgetId")
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
                    var isSaving by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()

                    var initialPrefs by remember { mutableStateOf<androidx.datastore.preferences.core.Preferences?>(null) }
                    LaunchedEffect(appWidgetId) {
                        val gId = findWidgetGlanceId(this@ValueGridWidgetConfigActivity, appWidgetId, widgetClasses = listOf(ValueGridWidget::class.java))
                        if (gId != null) {
                            initialPrefs = androidx.glance.appwidget.state.getAppWidgetState(
                                this@ValueGridWidgetConfigActivity, WidgetPreferencesStateDefinition, gId
                            )
                        }
                    }

                    val savedChannelId = initialPrefs?.get(WidgetPrefsKeys.KEY_CHANNEL_ID)
                    val savedBgColor = initialPrefs?.get(WidgetPrefsKeys.KEY_BG_COLOR)
                    val savedTextColor = initialPrefs?.get(WidgetPrefsKeys.KEY_TEXT_COLOR)
                    val savedTransparency = initialPrefs?.get(WidgetPrefsKeys.KEY_TRANSPARENCY)
                    val savedFontSize = initialPrefs?.get(WidgetPrefsKeys.KEY_FONT_SIZE)
                    val savedIsGlass = initialPrefs?.get(WidgetPrefsKeys.KEY_IS_GLASS)
                    val savedVisibleFields = initialPrefs?.get(WidgetPrefsKeys.KEY_VISIBLE_FIELDS)?.mapNotNull { it.toIntOrNull() }?.toSet()

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
                            coroutineScope.launch {
                                try {
                                    val ch = savedChannels.find { it.id == channelId }
                                    repository.refreshFeed(channelId, key, chartTimespan = ch?.chartTimespan)
                                } catch (e: Exception) {
                                    android.util.Log.e("ValueGridWidgetConfig", "Failed to refresh metadata for $channelId", e)
                                }
                            }
                        },
                        initialChannelId = existing?.id,
                        initialApiKey = existing?.apiKey,
                        initialChannelName = existing?.name,
                        initialBgColorHex = savedBgColor ?: existing?.widgetBgColorHex,
                        initialTransparency = savedTransparency ?: existing?.widgetTransparency ?: 1.0f,
                        initialIsGlass = savedIsGlass ?: existing?.isGlassmorphismEnabled,
                        initialChartTimespan = existing?.chartProcessingPeriod,
                        initialChartTimespanStr = existing?.chartTimespan,
                        initialChartResults = existing?.chartResults ?: 60,
                        initialFontSize = savedFontSize ?: existing?.widgetFontSize ?: 12,
                        initialTextColorHex = savedTextColor ?: existing?.widgetTextColorHex,
                        initialVisibleFields = savedVisibleFields ?: existing?.widgetVisibleFields ?: emptySet(),
                        initialAlertRules = initialAlertRules,
                        onSave = { channelId, apiKey, name, bgColor, txtColor, transparency, fontSize, visibleFields, chartField, isGlass, chartTimespan, chartTimespanStr, chResultsCount, alertRules ->
                            isSaving = true
                            coroutineScope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    try {
                                        android.util.Log.d("TS_DEBUG", "onSave triggered for channel $channelId, widget $appWidgetId")

                                        // 1. Update Core Channel Info (API Key, Name Only)
                                        val existingChannel = channelPreferences.observe().first().find { it.id == channelId }
                                        val updatedChannel = (existingChannel ?: SavedChannel(id = channelId, name = name)).copy(
                                            id = channelId,
                                            name = name,
                                            apiKey = apiKey,
                                            lastSyncStatus = WidgetPrefsKeys.STATUS_NONE,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                            channelPreferences.save(updatedChannel)
                                        android.util.Log.d("TS_DEBUG", "1. Core Channel info saved for $channelId")

                                        // 2. Alert Rules saving (Unified - Global per channel)
                                        repository.deleteGlobalAlertRules(channelId)
                                        alertRules.forEach { rule ->
                                            repository.saveAlertRule(rule.copy(appWidgetId = null))
                                        }
                                        android.util.Log.d("TS_DEBUG", "Unified Alarms: Drawn ${alertRules.size} rules for channel $channelId")

                                        // 3. Save binding synchronicly
                                        widgetBindingRepository.saveBinding(appWidgetId, channelId)
                                        android.util.Log.d("TS_DEBUG", "3. Binding DB saved synchronicly: $appWidgetId -> $channelId")

                                        // Sync with Room DB for app UI consistency
                                        repository.observeChannel(channelId).first()?.let { roomChannel ->
                                            repository.updateChannel(roomChannel.copy(
                                                name = name,
                                                apiKey = apiKey
                                            ))
                                        }

                                        // 4. Launch fire-and-forget background sync (standalone scope)
                                        val appContext = applicationContext
                                        kotlinx.coroutines.CoroutineScope(
                                            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
                                        ).launch {
                                            try {
                                                android.util.Log.d("TS_DEBUG", ">>> STARTING ASYNC SYNC V8 for $appWidgetId")

                                                // Update Glance DataStore
                                                val gId = findWidgetGlanceId(appContext, appWidgetId, widgetClasses = listOf(ValueGridWidget::class.java))
                                                if (gId != null) {
                                                    updateAppWidgetState(appContext, WidgetPreferencesStateDefinition, gId) { p ->
                                                        p.toMutablePreferences().apply {
                                                            this[WidgetPrefsKeys.KEY_CHANNEL_ID] = channelId
                                                            this[WidgetPrefsKeys.KEY_CHANNEL_NAME] = name
                                                            this[WidgetPrefsKeys.KEY_BG_COLOR] = bgColor ?: "#FFFFFF"
                                                            this[WidgetPrefsKeys.KEY_TEXT_COLOR] = txtColor ?: ""
                                                            this[WidgetPrefsKeys.KEY_TRANSPARENCY] = transparency
                                                            this[WidgetPrefsKeys.KEY_FONT_SIZE] = fontSize
                                                            this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS] = visibleFields.map { it.toString() }.toSet()
                                                            this[WidgetPrefsKeys.KEY_IS_GLASS] = isGlass
                                                            this[WidgetPrefsKeys.KEY_CHART_RESULTS] = chResultsCount
                                                            this[WidgetPrefsKeys.KEY_IS_REFRESHING] = true
                                                            this[WidgetPrefsKeys.KEY_WIDGET_VISUALS_CUSTOMIZED] = true
                                                        }
                                                    }
                                                    android.util.Log.d("TS_DEBUG", "Async: DataStore updated for $appWidgetId")
                                                }

                                                ValueGridWidget().updateAll(appContext)

                                                // SYSTEM SIGNAL
                                                val updateIntent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                                                    setPackage(packageName)
                                                }
                                                appContext.sendBroadcast(updateIntent)
                                                android.util.Log.d("TS_DEBUG", "Async: System signals sent.")

                                                // Refresh feed and enqueue worker via proper WorkManager API
                                                repository.refreshFeed(channelId, apiKey, chartTimespan = chartTimespanStr)

                                                val workRequest = OneTimeWorkRequestBuilder<DataSyncWorker>().build()
                                                WorkManager.getInstance(appContext)
                                                    .enqueueUniqueWork(
                                                        "value_grid_config_refresh_$appWidgetId",
                                                        ExistingWorkPolicy.REPLACE,
                                                        workRequest
                                                    )
                                                android.util.Log.d("TS_DEBUG", "Async: Worker enqueued via WorkManager.")
                                            } catch (e: Exception) {
                                                android.util.Log.e("TS_DEBUG", "Async: FATAL ERROR", e)
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
                                            android.util.Log.d("TS_DEBUG", ">>> RESULT_OK sent. Finishing.")
                                            finish()
                                        }

                                    } catch (e: Exception) {
                                        android.util.Log.e("TS_DEBUG", "FATAL onSave error", e)
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

    override fun onDestroy() {
        super.onDestroy()
        if (!isConfigurationDone && !isChangingConfigurations) {
            setResult(RESULT_CANCELED)
        }
    }
}
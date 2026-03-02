package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.getAppWidgetState
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.worker.DataSyncWorker
import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.feature.channel.data.mapper.toDomain
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.combine
import com.thingspeak.monitor.feature.channel.domain.model.Channel

/**
 * Glance widget displaying live ThingSpeak channel data.
 */
class ThingSpeakGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = WidgetPreferencesStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("Widget", "provideGlance triggered for $id")

        provideContent {
            val prefs = androidx.glance.currentState<Preferences>()
            val savedChannelId = prefs[longPreferencesKey("channel_id")] ?: -1L
            
            // Reactive data loading: observing database changes for a specific channel
            val state by androidx.compose.runtime.produceState<WidgetData?>(
                initialValue = if (savedChannelId != -1L) WidgetData(channelName = "Loading...", channelId = savedChannelId, entry = null, isLoading = true) else null, 
                savedChannelId, 
                prefs
            ) {
                if (savedChannelId == -1L) {
                    value = null
                    return@produceState
                }

                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, 
                    WidgetEntryPoint::class.java
                )
                val dao = entryPoint.channelFeedDao()
                val repository = entryPoint.channelRepository()
                
                // Observing both entries and channel metadata (100% reactivity)
                combine(
                    dao.observeFeedEntries(savedChannelId),
                    repository.observeChannel(savedChannelId)
                ) { entries, channel ->
                    entries to channel
                }.collect { (entries, channelObj) ->
                    if (channelObj != null) {
                        value = withContext(Dispatchers.IO) {
                            loadWidgetData(context, savedChannelId, prefs, entries, channelObj)
                        }
                    }
                }
            }

            GlanceTheme {
                when {
                    state == null -> WidgetConfigReqUI(context)
                    state?.isLoading == true && state?.entry == null -> WidgetLoadingUI(context)
                    state?.entry == null -> NoDataContent(context, state?.channelName ?: "")
                    else -> WidgetContent(context, state!!)
                }
            }
        }
    }

    private suspend fun loadWidgetData(
        context: Context, 
        targetId: Long, 
        prefs: Preferences,
        entries: List<com.thingspeak.monitor.feature.channel.data.local.FeedEntryEntity>,
        channelData: com.thingspeak.monitor.feature.channel.domain.model.Channel
    ): WidgetData? {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, 
            WidgetEntryPoint::class.java
        )
        val channelPreferences = entryPoint.channelPreferences()
        val dao = entryPoint.channelFeedDao()
        val repository = entryPoint.channelRepository()
        val checkAlerts = entryPoint.checkAlertThresholdsUseCase()
        val appPreferences = entryPoint.appPreferences()

        val entry = entries.firstOrNull()
        
        val visibleFields = prefs[stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: channelData.widgetVisibleFields

        val chartBitmap = if (entries.size >= 2) {
            // Include all visible fields in the chart
            val fieldsToPlot = visibleFields ?: (1..8).toSet()
            
            val dataMap = fieldsToPlot.associateWith { fieldNum ->
                entries.take(30).mapNotNull { e ->
                    val value = when(fieldNum) {
                        1 -> e.field1?.toDoubleOrNull()
                        2 -> e.field2?.toDoubleOrNull()
                        3 -> e.field3?.toDoubleOrNull()
                        4 -> e.field4?.toDoubleOrNull()
                        5 -> e.field5?.toDoubleOrNull()
                        6 -> e.field6?.toDoubleOrNull()
                        7 -> e.field7?.toDoubleOrNull()
                        8 -> e.field8?.toDoubleOrNull()
                        else -> null
                    }
                    if (value != null) {
                        val time = WidgetUtils.parseIsoTime(e.createdAt) ?: 0L
                        if (time > 0L) Pair(time, value) else null
                    } else null
                }.reversed()
            }.filterValues { it.isNotEmpty() }

            if (dataMap.isNotEmpty()) {
                WidgetChartGenerator.generateLineChart(dataMap)
            } else null
        } else null

        // Load alerts and check if any threshold is violated
        val activeAlertFields = if (entry != null) {
            val thresholds = repository.observeAlerts(channelData.id).first()
            val entryDomain = entry.toDomain()
            checkAlerts(entryDomain, thresholds).map { it.fieldNumber }.toSet()
        } else {
            emptySet()
        }

        val isGlass = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_glass")] 
            ?: channelData.isGlassmorphismEnabled

        val isRefreshing = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] ?: false

        android.util.Log.d("Widget", "Load: ch=${channelData.id}, bg=${prefs[stringPreferencesKey("bg_color")]}, glass=$isGlass, refreshing=$isRefreshing")

        return WidgetData(
            channelName = channelData.name.ifBlank { "Channel ${channelData.id}" },
            channelId = channelData.id,
            entry = entry,
            fieldNames = channelData.fieldNames,
            bgColorHex = prefs[stringPreferencesKey("bg_color")] ?: channelData.widgetBgColorHex,
            transparency = prefs[floatPreferencesKey("transparency")] ?: channelData.widgetTransparency,
            fontSize = prefs[intPreferencesKey("font_size")] ?: channelData.widgetFontSize,
            isGlass = isGlass,
            activeAlertFields = activeAlertFields,
            syncIntervalMinutes = appPreferences.observeSyncInterval().first(),
            lastSyncTime = channelData.lastSyncTime,
            lastSyncStatus = channelData.lastSyncStatus.name,
            visibleFields = visibleFields,
            chartBitmap = chartBitmap,
            isRefreshing = isRefreshing
        )
    }
}

/**
 * Callback triggered by the manual refresh button on the widget.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
        )
        val channelPreferences = entryPoint.channelPreferences()
        val getChannelFeedUseCase = entryPoint.getChannelFeedUseCase()
        
        // Get the channel explicitly or refresh all
        val prefs = getAppWidgetState<Preferences>(context, WidgetPreferencesStateDefinition, glanceId)
        val savedChannelId = prefs[longPreferencesKey("channel_id")] ?: -1L
        
        if (savedChannelId > 0) {
            val channel = channelPreferences.observe().first().find { it.id == savedChannelId }
            if (channel != null) {
                // Set loading status
                androidx.glance.appwidget.state.updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply { this[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] = true }
                }
                ThingSpeakGlanceWidget().update(context, glanceId)

                val result = getChannelFeedUseCase.refresh(channel.id, channel.apiKey)
                
                // Clear loading status
                androidx.glance.appwidget.state.updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply { this[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] = false }
                }
                
                if (result is ApiResult.Error || 
                    result is ApiResult.Exception) {
                    android.util.Log.e("RefreshAction", "Refresh failed for channel ${channel.id}")
                }
            }
        } else {
            // Fallback: refresh everything if no specific ID bound
            DataSyncWorker.runOnce(context)
            // Note: runOnce is still async, but for a generic refresh it's better than nothing
        }
        
        ThingSpeakGlanceWidget().update(context, glanceId)
    }
}

/**
 * Action to launch the configuration activity for an existing widget.
 */
class EditAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            .getAppWidgetId(glanceId)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val intent = Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}


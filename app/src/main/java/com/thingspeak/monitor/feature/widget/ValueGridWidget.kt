package com.thingspeak.monitor.feature.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import androidx.glance.GlanceTheme
import com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository
import com.thingspeak.monitor.feature.channel.data.mapper.toDomain
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.thingspeak.monitor.feature.channel.domain.model.Channel

class ValueGridWidget : GlanceAppWidget() {
    
    // Exact sizing allows to build precise UI depending on the grid size
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition



    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
                    com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
                )
                val dao = entryPoint.channelFeedDao()
                val repository = entryPoint.channelRepository()
                
                // Observing both entries and channel metadata (100% reactivity)
                combine(
                    dao.observeFeedEntries(savedChannelId),
                    repository.observeChannel(savedChannelId)
                ) { entriesList, channelObj ->
                    entriesList to channelObj
                }.collect { (entries, channelData) ->
                    if (channelData != null) {
                        value = withContext(Dispatchers.IO) {
                            loadWidgetData(context, savedChannelId, prefs, entries, channelData)
                        }
                    }
                }
            }

            GlanceTheme {
                when {
                    state == null -> WidgetErrorUI(context, "Configuring...")
                    state?.isLoading == true && state?.entry == null -> WidgetLoadingUI(context)
                    state?.entry == null -> NoDataContent(context, state?.channelName ?: "")
                    else -> ValueGridContent(context, state!!)
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
            com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
        )
        val channelPreferences = entryPoint.channelPreferences()
        val dao = entryPoint.channelFeedDao()
        val repository = entryPoint.channelRepository()
        val checkAlerts = entryPoint.checkAlertThresholdsUseCase()
        val appPreferences = entryPoint.appPreferences()

        val entry = entries.firstOrNull()
        
        val visibleFields = prefs[stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: channelData.widgetVisibleFields

        val activeAlertFields = if (entry != null) {
            val thresholds = repository.observeAlerts(channelData.id).firstOrNull() ?: emptyList()
            val entryDomain = entry.toDomain()
            checkAlerts(entryDomain, thresholds).map { it.fieldNumber }.toSet()
        } else {
            emptySet()
        }

        val isGlass = prefs[booleanPreferencesKey("is_glass")] 
            ?: channelData.isGlassmorphismEnabled

        val isRefreshing = prefs[booleanPreferencesKey("is_refreshing")] ?: false

        val result = WidgetData(
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
            chartBitmap = null, // Not used in this widget
            isRefreshing = isRefreshing
        )
        
        android.util.Log.d("ValueGridWidget", "loadWidgetData: returning data for ${result.channelName}, fields: ${result.visibleFields}")
        return result
    }
}

class GridRefreshAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: android.content.Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.thingspeak.monitor.core.di.WidgetEntryPoint::class.java
        )
        val channelPreferences = entryPoint.channelPreferences()
        val getChannelFeedUseCase = entryPoint.getChannelFeedUseCase()
        
        val prefs = androidx.glance.appwidget.state.getAppWidgetState<androidx.datastore.preferences.core.Preferences>(context, WidgetPreferencesStateDefinition, glanceId)
        val savedChannelId = prefs[androidx.datastore.preferences.core.longPreferencesKey("channel_id")] ?: -1L
        
        if (savedChannelId > 0) {
            val savedChannel = (channelPreferences.observe().firstOrNull() ?: emptyList()).find { it.id == savedChannelId }
            if (savedChannel != null) {
                androidx.glance.appwidget.state.updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply { this[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] = true }
                }
                ValueGridWidget().update(context, glanceId)

                val apiKeySafeguard = savedChannel.apiKey ?: ""
                getChannelFeedUseCase.refresh(savedChannel.id, apiKeySafeguard)
                
                androidx.glance.appwidget.state.updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply { this[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] = false }
                }
            }
        } else {
            com.thingspeak.monitor.core.worker.DataSyncWorker.runOnce(context)
        }
        ValueGridWidget().update(context, glanceId)
    }
}

class GridEditAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: android.content.Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            .getAppWidgetId(glanceId)

        if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            val intent = android.content.Intent(context, ValueGridWidgetConfigActivity::class.java).apply {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }
    }
}

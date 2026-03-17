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
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.state.getAppWidgetState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.thingspeak.monitor.feature.channel.domain.model.Channel
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.worker.DataSyncWorker

class ValueGridWidget : GlanceAppWidget() {
    
    // Exact sizing allows to build precise UI depending on the grid size
    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = WidgetPreferencesStateDefinition



    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val appWidgetId = manager.getAppWidgetId(id)
            
            android.util.Log.d("SNIPER_WIDGET", "In provideContent: glanceId=$id, resolved appWidgetId=$appWidgetId")
            
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext, 
                WidgetEntryPoint::class.java
            )
            val bindingRepo = entryPoint.widgetBindingRepository()
            
            // produceState key: appWidgetId + prefs (to react to configuration changes)
            val state by androidx.compose.runtime.produceState<WidgetData?>(
                initialValue = WidgetData(channelName = "", channelId = -1L, entry = null, isLoading = true, debugInfo = "Initializing..."), 
                appWidgetId,
                prefs
            ) {
                val dao = entryPoint.channelFeedDao()
                val repository = entryPoint.channelRepository()
                
                // AUDIT_V11: Tracking produceState lifecycle
                android.util.Log.d("AUDIT_V11", "ValueGrid [START] for appWidgetId=$appWidgetId")
                
                // Fail-safe: if after 7 seconds we still have nothing, stop the loading state
                val job = launch {
                    kotlinx.coroutines.delay(7000)
                    if (value?.isLoading == true) {
                        android.util.Log.e("AUDIT_V11", "ValueGrid [TIMEOUT] 7s reached for $appWidgetId")
                        value = value?.copy(isLoading = false, channelName = "Timeout / No Data")
                    }
                }

                WidgetIdResolver.observe(appWidgetId, bindingRepo, prefs).collectLatest { effectiveId ->
                    android.util.Log.d("AUDIT_V11", "ValueGrid [ID_RESOLVED] appWidgetId=$appWidgetId -> effectiveId=$effectiveId")
                    if (effectiveId <= 0L) {
                        android.util.Log.e("AUDIT_V11", "ValueGrid [NO_ID] Aborting data stream for $appWidgetId")
                        value = value?.copy(debugInfo = "No ID resolved (Check configuration)")
                        return@collectLatest
                    }
                    
                    // V11: Immediate channel ID update to switch UI from loading to the widget skeleton
                    value = value?.copy(
                        channelId = effectiveId,
                        debugInfo = "ID Resolved: $effectiveId. Waiting for data..."
                    )
                    
                    // V11: Reactive data observation from all sources
                    kotlinx.coroutines.flow.combine(
                        dao.observeLastEntry(effectiveId),
                        repository.observeChannel(effectiveId),
                        repository.observeAlerts(effectiveId),
                        entryPoint.appPreferences().observeSyncInterval()
                    ) { entries, channelData, alerts, syncInterval ->
                        loadWidgetData(context, effectiveId, prefs, entries, channelData, alerts, syncInterval)
                    }.collect { loadedData ->
                        // V11.2 [100% Certainty]: Proactive synchronization via centralized orchestrator
                        WidgetSyncOrchestrator.triggerSyncIfNeeded(
                            context = context,
                            channelId = effectiveId,
                            entryPresent = loadedData.entry != null,
                            lastSyncTime = loadedData.lastSyncTime,
                            isRefreshing = loadedData.isRefreshing
                        )
                        
                        android.util.Log.d("AUDIT_V11", "ValueGrid [REACTIVE_UPDATE] for $effectiveId, entryTime=${loadedData.entry?.createdAt}")
                        value = loadedData
                    }
                }
            }

            GlanceTheme {
                val currentLoading = state?.isLoading == true
                val currentId = state?.channelId ?: -1L
                val debugInfo = state?.debugInfo ?: ""
                android.util.Log.d("AUDIT_V11", "ValueGrid [RENDER] appWidgetId=$appWidgetId, targetId=$currentId, isLoading=$currentLoading, entryPresent=${state?.entry != null}")
                
                when {
                    state == null -> WidgetConfigReqUI(context)
                    // V9: Only show full-screen loading if we don't even have a channelId yet
                    currentId == -1L && currentLoading -> WidgetLoadingUI(context, debugInfo)
                    // If we have ID but no entry yet, it's either "No Data" or background loading (handled inside ValueGridContent)
                    state?.entry == null && !currentLoading -> NoDataContent(context, state?.channelName ?: "")
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
        channelData: com.thingspeak.monitor.feature.channel.domain.model.Channel?,
        alerts: List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold>,
        syncInterval: Long
    ): WidgetData {
        android.util.Log.d("ValueGridWidget", "loadWidgetData: starting for $targetId (metadata present=${channelData != null})")
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, 
            WidgetEntryPoint::class.java
        )
        val checkAlerts = entryPoint.checkAlertThresholdsUseCase()

        val entry = entries.firstOrNull()
        
        // V11: Observe style preferences from DataStore for immediate UI updates
        val bgColorHex = prefs[stringPreferencesKey("bg_color")] ?: channelData?.widgetBgColorHex
        val transparency = prefs[floatPreferencesKey("transparency")] ?: channelData?.widgetTransparency ?: 0.5f
        val fontSize = prefs[intPreferencesKey("font_size")] ?: channelData?.widgetFontSize ?: 14
        
        val visibleFields = prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            // Default fields 1-8 for Grid widget as requested by the client
            ?: channelData?.widgetVisibleFields ?: (1..8).toSet() 

        val activeAlertFields = if (entry != null) {
            val entryDomain = entry.toDomain()
            checkAlerts(entryDomain, alerts).map { it.fieldNumber }.toSet()
        } else {
            emptySet()
        }

        val isGlass = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_glass")] 
            ?: channelData?.isGlassmorphismEnabled ?: false

        val isRefreshing = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] ?: false

        val result = WidgetData(
            channelName = channelData?.name?.ifBlank { null } 
                ?: context.getString(com.thingspeak.monitor.R.string.widget_channel_default_name, targetId),
            channelId = targetId,
            entry = entry,
            fieldNames = channelData?.fieldNames ?: emptyMap(),
            bgColorHex = bgColorHex,
            transparency = transparency,
            fontSize = fontSize,
            isGlass = isGlass,
            activeAlertFields = activeAlertFields,
            syncIntervalMinutes = syncInterval,
            lastSyncTime = channelData?.lastSyncTime ?: 0L,
            lastSyncStatus = channelData?.lastSyncStatus?.name ?: "NONE",
            visibleFields = visibleFields,
            chartBitmap = null,
            isRefreshing = isRefreshing
        )
        
        android.util.Log.v("AUDIT_V11", "ValueGrid [LOAD_WIDGET_DATA_DONE] targetId=$targetId, channelName=${result.channelName}")
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
        val bindingRepo = entryPoint.widgetBindingRepository()
        
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val effectiveId = WidgetIdResolver.resolve(appWidgetId, bindingRepo, prefs)
        
        if (effectiveId > 0) {
            val savedChannel = (channelPreferences.observe().firstOrNull() ?: emptyList()).find { it.id == effectiveId }
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

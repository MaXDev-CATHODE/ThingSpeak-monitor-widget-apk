package com.thingspeak.monitor.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
            
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext, 
                WidgetEntryPoint::class.java
            )
            val bindingRepo = entryPoint.widgetBindingRepository()
            val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(id)

            val state by androidx.compose.runtime.produceState<WidgetData?>(
                initialValue = WidgetData(channelName = "", channelId = -1L, entry = null, isLoading = true, debugInfo = "Initializing..."), 
                appWidgetId,
                prefs
            ) {
                val dao = entryPoint.channelFeedDao()
                val repository = entryPoint.channelRepository()

                // AUDIT_V11: Tracking produceState lifecycle
                android.util.Log.d("AUDIT_V11", "SmallWidget [START] for appWidgetId=$appWidgetId")
                
                // Fail-safe: jesli po 7 sekundach nadal nic nie mamy, przerywamy stan ladowania
                val job = launch {
                    kotlinx.coroutines.delay(7000)
                    if (value?.isLoading == true) {
                        android.util.Log.e("AUDIT_V11", "SmallWidget [TIMEOUT] 7s reached for $appWidgetId")
                        value = value?.copy(isLoading = false, channelName = "Timeout / No Data")
                    }
                }

                WidgetIdResolver.observe(appWidgetId, bindingRepo, prefs).collectLatest { effectiveId ->
                    android.util.Log.d("AUDIT_V11", "SmallWidget [ID_RESOLVED] appWidgetId=$appWidgetId -> effectiveId=$effectiveId")
                    if (effectiveId <= 0L) {
                        android.util.Log.e("AUDIT_V11", "SmallWidget [NO_ID] Aborting data stream for $appWidgetId")
                        value = value?.copy(debugInfo = "No ID resolved (Check Config)")
                        return@collectLatest
                    }
                    
                    value = value?.copy(debugInfo = "ID Resolved: $effectiveId. Waiting for Data...")

                    val entries = dao.observeLatestFeedEntries(effectiveId, 500).firstOrNull() ?: emptyList()
                    val channelObj = repository.observeChannel(effectiveId).firstOrNull()
                    val alerts = repository.observeAlerts(effectiveId).firstOrNull() ?: emptyList()
                    val syncInterval = entryPoint.appPreferences().observeSyncInterval().firstOrNull() ?: 30L
                    
                    android.util.Log.v("AUDIT_V11", "SmallWidget [COMBINE] Data arrived for $effectiveId: entries=${entries.size}, channel=${channelObj != null}")
                    
                    val loadedData = loadWidgetData(context, effectiveId, prefs, entries, channelObj, alerts, syncInterval)
                    
                    android.util.Log.d("AUDIT_V11", "SmallWidget [UPDATE_VALUE] for $effectiveId, entryTime=${loadedData.entry?.createdAt}")
                    value = loadedData
                }
            }

            GlanceTheme {
                val currentLoading = state?.isLoading == true
                val currentId = state?.channelId ?: -1L
                val debugInfo = state?.debugInfo ?: ""
                android.util.Log.d("AUDIT_V11", "SmallWidget [RENDER] appWidgetId=$appWidgetId, targetId=$currentId, isLoading=$currentLoading, entryPresent=${state?.entry != null}")
                
                when {
                    state == null -> WidgetConfigReqUI(context)
                    // V9: Only show full-screen loading if we don't have a channelId yet
                    currentId == -1L && currentLoading -> WidgetLoadingUI(context, debugInfo)
                    // If we have ID but no entry yet, it's either "No Data" or background loading
                    state?.entry == null && !currentLoading -> NoDataContent(context, state?.channelName ?: "")
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
        channelData: com.thingspeak.monitor.feature.channel.domain.model.Channel?,
        alerts: List<com.thingspeak.monitor.feature.channel.domain.model.AlertThreshold>,
        syncInterval: Long
    ): WidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, 
            WidgetEntryPoint::class.java
        )
        val checkAlerts = entryPoint.checkAlertThresholdsUseCase()

        val entry = entries.firstOrNull()
        
        val visibleFields = prefs[stringSetPreferencesKey("visible_fields")]?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: channelData?.widgetVisibleFields ?: (1..8).toSet()

        val chartTimespanHours = prefs[intPreferencesKey("chart_timespan")] ?: channelData?.chartProcessingPeriod?.takeIf { it > 0 } ?: 24
        val referenceNow = System.currentTimeMillis()
        val cutoffTime = referenceNow - (chartTimespanHours * 60 * 60 * 1000L)

        val chartBitmap = if (entries.size >= 2) {
            val fieldsToPlot = visibleFields ?: (1..8).toSet()
            val tempMap = fieldsToPlot.associateWith { mutableListOf<Pair<Long, Double>>() }
            
            // Process entries once for all fields
            entries.forEach { e ->
                val time = WidgetUtils.parseIsoTime(e.createdAt) ?: return@forEach
                if (time < cutoffTime) return@forEach
                
                fieldsToPlot.forEach { fieldNum ->
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
                        tempMap[fieldNum]?.add(Pair(time, value))
                    }
                }
            }

            val dataMap = tempMap.mapValues { (_, rawPoints) ->
                if (rawPoints.isEmpty()) return@mapValues emptyList<Pair<Long, Double>>()
                
                val allPointsInRange = rawPoints.sortedBy { it.first }
                
                // DISCRETE SAMPLING: 15 fixed time buckets for maximum clarity
                val numBuckets = 15
                val startTime = allPointsInRange.first().first
                val endTime = allPointsInRange.last().first
                val totalDataSpan = (endTime - startTime).let { if (it <= 0) 1L else it }
                val bucketDuration = totalDataSpan / numBuckets
                
                val buckets = mutableListOf<Pair<Long, Double>>()
                for (i in 0 until numBuckets) {
                    val bStart = startTime + (i * bucketDuration)
                    val bEnd = bStart + bucketDuration
                    
                    val pointsInBucket = allPointsInRange.filter { it.first in bStart..bEnd }
                    if (pointsInBucket.isNotEmpty()) {
                        val avg = pointsInBucket.map { it.second }.average()
                        buckets.add(Pair(bStart + (bucketDuration / 2), avg))
                    }
                }
                
                // If buckets are too sparse, just use the raw points (capped at 50)
                if (buckets.size < 3) {
                    allPointsInRange.takeLast(50).sortedBy { it.first }
                } else {
                    buckets
                }
            }.filterValues { it.isNotEmpty() }

            android.util.Log.d("Widget", "Chart data processed for channel $targetId. ${dataMap.size} fields. Points: ${dataMap.mapValues { it.value.size }}")

            if (dataMap.isNotEmpty()) {
                android.util.Log.v("AUDIT_V11", "SmallWidget [CHART_START] generating for $targetId")
                val start = System.currentTimeMillis()
                
                // Calculate actual X range across ALL fields for a unified X axis
                val allTimes = dataMap.values.flatten().map { it.first }
                val minTime = allTimes.minOrNull() ?: 0L
                val maxTime = allTimes.maxOrNull() ?: System.currentTimeMillis()
                val effectiveXRange = (maxTime - minTime).let { if (it <= 0) 3600000L else it }

                val bitmap = WidgetChartGenerator.generateLineChart(
                    dataMap, 
                    safeWidth = 300, // Reduced resolution to prevent TransactionTooLargeException on Pixel
                    safeHeight = 150,
                    timeRangeMs = effectiveXRange,
                    referenceNow = maxTime // Anchored to latest data point
                )
                android.util.Log.v("AUDIT_V11", "SmallWidget [CHART_DONE] for $targetId, time=${System.currentTimeMillis() - start}ms")
                bitmap
            } else {
                android.util.Log.i("AUDIT_V11", "SmallWidget [VERSION] 1.0.3 - Sorted sampling + Auto-zoom for $targetId")
                null
            }
        } else {
            android.util.Log.w("AUDIT_V11", "SmallWidget [CHART_SKIP] Too few entries for chart: ${entries.size}")
            null
        }

        // Load alerts and check if any threshold is violated
        val activeAlertFields = if (entry != null) {
            val entryDomain = entry.toDomain()
            checkAlerts(entryDomain, alerts).map { it.fieldNumber }.toSet()
        } else {
            emptySet()
        }

        val isGlass = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_glass")] 
            ?: channelData?.isGlassmorphismEnabled ?: false

        val isRefreshing = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("is_refreshing")] ?: false

        return WidgetData(
            channelName = channelData?.name?.ifBlank { null } 
                ?: context.getString(com.thingspeak.monitor.R.string.widget_channel_default_name, targetId),
            channelId = targetId,
            entry = entry,
            fieldNames = channelData?.fieldNames ?: emptyMap(),
            bgColorHex = prefs[stringPreferencesKey("bg_color")] ?: channelData?.widgetBgColorHex,
            transparency = prefs[floatPreferencesKey("transparency")] ?: channelData?.widgetTransparency ?: 0.5f,
            fontSize = prefs[intPreferencesKey("font_size")] ?: channelData?.widgetFontSize ?: 14,
            isGlass = isGlass,
            activeAlertFields = activeAlertFields,
            syncIntervalMinutes = syncInterval,
            lastSyncTime = channelData?.lastSyncTime ?: 0L,
            lastSyncStatus = channelData?.lastSyncStatus?.name ?: "NONE",
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
        val bindingRepo = entryPoint.widgetBindingRepository()
        
        val appWidgetId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val effectiveId = WidgetIdResolver.resolve(appWidgetId, bindingRepo, prefs)
        
        if (effectiveId > 0) {
            val channel = channelPreferences.observe().first().find { it.id == effectiveId }
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


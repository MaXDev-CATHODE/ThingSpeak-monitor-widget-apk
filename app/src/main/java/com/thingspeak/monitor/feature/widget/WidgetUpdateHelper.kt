package com.thingspeak.monitor.feature.widget

import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.appwidget.AppWidgetManager
import androidx.work.WorkManager
import androidx.datastore.preferences.core.Preferences
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.core.di.WidgetEntryPoint
import com.thingspeak.monitor.core.worker.DataSyncWorker
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import org.json.JSONObject

data class WidgetGlanceContext(
    val appWidgetId: Int,
    val boundChannelId: Long,
    val syncIntervalMinutes: Long
)

/**
 * Centralized utility to push data from Room/API to Glance Preferences.
 * Ensures consistent key usage across different widget types and sync sources.
 */
object WidgetUpdateHelper {

    const val MAX_GLANCE_RETRIES = 2

    /**
     * Checks whether self-healing should be triggered.
     * Capped at [WidgetPrefsKeys.MAX_HEAL_RETRIES] to prevent infinite soft-loop
     * when the upstream data source is consistently unavailable.
     */
    fun shouldTriggerSelfHeal(
        prefs: Preferences,
        data: WidgetData,
        boundChannelId: Long
    ): Boolean {
        val healAttempted = prefs[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] ?: false
        val retryCount = prefs[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] ?: 0
        return data.channelName == WidgetPrefsKeys.LOADING_PLACEHOLDER &&
            boundChannelId != -1L &&
            !data.isRefreshing &&
            !healAttempted &&
            retryCount < WidgetPrefsKeys.MAX_HEAL_RETRIES
    }

    /**
     * Increments the heal retry counter so the widget does not attempt healing
     * indefinitely. Must be called just before starting the heal update cycle.
     */
    @JvmStatic
    fun bumpHealRetry(context: Context, glanceId: GlanceId) {
        kotlinx.coroutines.runBlocking {
            updateAppWidgetState(
                context, WidgetPreferencesStateDefinition, glanceId
            ) { p ->
                p.toMutablePreferences().apply {
                    val current = this[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] ?: 0
                    this[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] = (current + 1).coerceAtMost(9)
                }
            }
        }
    }

    /**
     * Common provideGlance pre-provideContent setup shared across all widget types.
     */
    suspend fun resolveGlanceContext(context: Context, id: GlanceId): WidgetGlanceContext {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val bindingRepo = entryPoint.widgetBindingRepository()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val boundChannelId = bindingRepo.getBindingSync(appWidgetId)

        val syncIntervalMinutes = try {
            entryPoint.appPreferences().observeSyncInterval().first()
        } catch (e: Exception) {
            android.util.Log.w(WIDGET_LOG_TAG, "Failed to read sync interval, using default", e)
            DEFAULT_SYNC_INTERVAL_MINUTES
        }

        return WidgetGlanceContext(appWidgetId, boundChannelId, syncIntervalMinutes)
    }

    /**
     * Shared core save pipeline used by both WidgetConfigActivity and ValueGridWidgetConfigActivity.
     */
    suspend fun saveWidgetCoreConfig(
        entryPoint: WidgetEntryPoint,
        channelPreferences: com.thingspeak.monitor.core.datastore.ChannelPreferences,
        widgetBindingRepository: WidgetBindingRepository,
        appWidgetId: Int,
        channelId: Long,
        apiKey: String,
        channelName: String,
        alertRules: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>
    ) {
        val repository = entryPoint.channelRepository()

        repository.deleteGlobalAlertRules(channelId)
        alertRules.forEach { rule ->
            repository.saveAlertRule(rule.copy(appWidgetId = null))
        }
        android.util.Log.d(WIDGET_LOG_TAG, "Unified Alert rules saved for channel $channelId")

        val existingChannel = channelPreferences.observe().first().find { it.id == channelId }
        val updatedChannel = (existingChannel ?: SavedChannel(id = channelId, name = channelName)).copy(
            id = channelId,
            name = channelName,
            apiKey = apiKey,
            lastSyncStatus = WidgetPrefsKeys.STATUS_NONE
        )
        channelPreferences.save(updatedChannel)
        android.util.Log.d(WIDGET_LOG_TAG, "Core Channel info saved")

        widgetBindingRepository.saveBinding(appWidgetId, channelId)

        repository.observeChannel(channelId).first()?.let { roomChannel ->
            repository.updateChannel(roomChannel.copy(
                name = channelName,
                apiKey = apiKey
            ))
        }
    }

    /**
     * Shared onDelete cleanup — cancels DataSyncWorker when no widgets of any type remain.
     */
    fun cancelRefreshIfNoWidgetsLeft(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val remainingGlance = manager.getAppWidgetIds(
            ComponentName(context, WidgetReceiver::class.java)
        )
        val remainingValueGrid = manager.getAppWidgetIds(
            ComponentName(context, ValueGridWidgetReceiver::class.java)
        )
        if (remainingGlance.isEmpty() && remainingValueGrid.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(DataSyncWorker.WORK_NAME)
            android.util.Log.w(WIDGET_LOG_TAG, "Last widget of any type removed — periodic refresh cancelled")
        }
    }

    /**
     * Shared updateAppWidget logic for both ThingSpeakGlanceWidget and ValueGridWidget.
     * Eliminates ~80% code duplication between the two implementations.
     *
     * @param onGenerateChart Optional callback to generate chart bitmap (only for chart widget).
     */
    suspend fun performUpdate(
        context: Context,
        appWidgetId: Int,
        widgetInstanceFactory: () -> androidx.glance.appwidget.GlanceAppWidget,
        onGenerateChart: suspend (SavedChannel, Long, List<FeedEntry>) -> String? = { _, _, _ -> null }
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val bindingRepo = entryPoint.widgetBindingRepository()
        val chanPrefs = entryPoint.channelPreferences()
        val getChannelFeed = entryPoint.getChannelFeedUseCase()

        val channelId = bindingRepo.getBindingSync(appWidgetId)
        if (channelId == -1L) return

        val savedChannels = chanPrefs.observe().first()
        val channel = savedChannels.find { it.id == channelId } ?: return

        val feeds = getChannelFeed.observe(channelId).first()
        val latestFeed = feeds.lastOrNull()

        val glanceId = findWidgetGlanceId(context, appWidgetId, maxRetries = MAX_GLANCE_RETRIES)
        if (glanceId == null) {
            android.util.Log.e("TS_DEBUG", "updateWidget: Could not find GlanceId for $appWidgetId")
            return
        }

        // Alert rules
        val repo = entryPoint.channelRepository()
        val checkRules = entryPoint.checkAlertRulesUseCase()
        val widgetAlerts = repo.getAlertRules(channelId, appWidgetId)

        val violations = latestFeed?.let { checkRules(it, widgetAlerts) }
            ?: emptyList<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()

        // Chart generation (only for chart widget; grid widgets get null)
        val chartFile = onGenerateChart(channel, channelId, feeds)

        // Resolve chartResults from existing prefs (preserve user setting)
        val prefs = getAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId)
        val cachedChartResults = prefs?.get(WidgetPrefsKeys.KEY_CHART_RESULTS) ?: 60

        // Push to DataStore Preferences
        updateWidgetPreferences(
            context = context,
            glanceId = glanceId,
            channel = channel.copy(chartResults = cachedChartResults),
            latestFeed = latestFeed,
            chartFile = chartFile,
            violatedMinFields = violations
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN }
                .map { it.fieldNumber }.toSet(),
            violatedMaxFields = violations
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN }
                .map { it.fieldNumber }.toSet(),
            minSetFields = widgetAlerts
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_LESS_THAN && it.isEnabled }
                .map { it.fieldNumber }.toSet(),
            maxSetFields = widgetAlerts
                .filter { it.condition == WidgetPrefsKeys.ALERT_CONDITION_GREATER_THAN && it.isEnabled }
                .map { it.fieldNumber }.toSet()
        )

        // Re-render widget
        widgetInstanceFactory().update(context, glanceId)
    }

    suspend fun updateWidgetPreferences(
        context: Context,
        glanceId: GlanceId,
        channel: SavedChannel,
        latestFeed: FeedEntry?,
        chartFile: String? = null,
        violatedMinFields: Set<Int> = emptySet(),
        violatedMaxFields: Set<Int> = emptySet(),
        minSetFields: Set<Int> = emptySet(),
        maxSetFields: Set<Int> = emptySet()
    ) {
        android.util.Log.d("TS_DEBUG", "updateWidgetPreferences: START for channel ${channel.id}, glanceId=$glanceId")
        val cachedEntryStr = latestFeed?.let { f ->
            try {
                JSONObject().apply {
                    put("channelId", channel.id)
                    put("createdAt", f.createdAt)
                    put("entryId", f.entryId)
                    (1..8).forEach { i ->
                        val value = f.fields[i]
                        if (value != null) put("field$i", value)
                    }
                }.toString()
            } catch (e: Exception) { null }
        }

        val fieldNamesJson = JSONObject().apply {
            channel.fieldNames.forEach { (k, v) -> put(k.toString(), v) }
        }.toString()

        val fieldUnitsJson = JSONObject().apply {
            channel.fieldUnits.forEach { (k, v) -> put(k.toString(), v) }
        }.toString()

        updateAppWidgetState(context, WidgetPreferencesStateDefinition, glanceId) { prefs ->
            android.util.Log.d("TS_DEBUG", "PUSHING preferences to widget for channel ${channel.id}")
            prefs.toMutablePreferences().apply {
                this[WidgetPrefsKeys.KEY_CHANNEL_ID] = channel.id
                this[WidgetPrefsKeys.KEY_CHANNEL_NAME] = channel.name
                this[WidgetPrefsKeys.KEY_ROUNDING] = channel.chartRounding
                this[WidgetPrefsKeys.KEY_FIELD_NAMES] = fieldNamesJson
                this[WidgetPrefsKeys.KEY_FIELD_UNITS] = fieldUnitsJson
                this[WidgetPrefsKeys.KEY_IS_REFRESHING] = false
                this[WidgetPrefsKeys.KEY_LAST_SYNC_STATUS] = channel.lastSyncStatus
                channel.timezone?.let { this[WidgetPrefsKeys.KEY_CHANNEL_TIMEZONE] = it }

                if (cachedEntryStr != null) {
                    this[WidgetPrefsKeys.KEY_CACHED_ENTRY] = cachedEntryStr
                }

                if (chartFile != null) {
                    this[WidgetPrefsKeys.KEY_CHART_FILE] = chartFile
                    this.remove(WidgetPrefsKeys.KEY_CHART_BITMAP)
                }

                // PROTECT visual settings — only overwrite if user hasn't customized them via config screen
                val isCustomized = this[WidgetPrefsKeys.KEY_WIDGET_VISUALS_CUSTOMIZED] ?: false
                if (!isCustomized) {
                    this[WidgetPrefsKeys.KEY_BG_COLOR] = channel.widgetBgColorHex ?: "#FFFFFF"
                    this[WidgetPrefsKeys.KEY_TEXT_COLOR] = channel.widgetTextColorHex ?: ""
                    this[WidgetPrefsKeys.KEY_TRANSPARENCY] = channel.widgetTransparency
                    this[WidgetPrefsKeys.KEY_FONT_SIZE] = channel.widgetFontSize
                    this[WidgetPrefsKeys.KEY_IS_GLASS] = channel.isGlassmorphismEnabled ?: false
                }

                val currentVisible = this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS]
                if (currentVisible == null || currentVisible.isEmpty()) {
                    channel.widgetVisibleFields?.let { fields ->
                        this[WidgetPrefsKeys.KEY_VISIBLE_FIELDS] = fields.map { it.toString() }.toSet()
                    }
                }

                this[WidgetPrefsKeys.KEY_CHART_RESULTS] = channel.chartResults ?: 60

                this[WidgetPrefsKeys.KEY_VIOLATED_MIN_FIELDS] = violatedMinFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_VIOLATED_MAX_FIELDS] = violatedMaxFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_MIN_SET_FIELDS] = minSetFields.map { it.toString() }.toSet()
                this[WidgetPrefsKeys.KEY_MAX_SET_FIELDS] = maxSetFields.map { it.toString() }.toSet()

                this[WidgetPrefsKeys.KEY_HEAL_ATTEMPTED] = cachedEntryStr != null
                this[WidgetPrefsKeys.KEY_HEAL_RETRY_COUNT] = 0
            }
        }
        android.util.Log.i("TS_DEBUG", "updateWidgetPreferences: SUCCESS for channel ${channel.id}")
    }

    /**
     * Pushes widget state to all bound widgets for a given channel.
     * Replaces duplicated loop logic in [DataSyncWorker] and [DataSyncService].
     *
     * @param feedEntries When non-empty, generates per-widget chart using the actual
     *                    appWidgetId as the cache key (avoids channel-vs-widget key collision).
     */
    suspend fun pushToBoundWidgets(
        context: Context,
        channel: SavedChannel,
        latestFeed: FeedEntry?,
        violatedMinFields: Set<Int>,
        violatedMaxFields: Set<Int>,
        minSetFields: Set<Int>,
        maxSetFields: Set<Int>,
        feedEntries: List<FeedEntry>? = null
    ) {
        val manager = GlanceAppWidgetManager(context)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val bindingRepo = entryPoint.widgetBindingRepository()

        for (widgetClass in listOf(
            ThingSpeakGlanceWidget::class.java,
            ValueGridWidget::class.java
        )) {
            manager.getGlanceIds(widgetClass).forEach { glanceId ->
                val appWidgetId = manager.getAppWidgetId(glanceId)
                if (bindingRepo.getBindingSync(appWidgetId) == channel.id) {
                    val chartFile = if (widgetClass == ThingSpeakGlanceWidget::class.java && feedEntries != null && feedEntries.isNotEmpty()) {
                        WidgetChartGenerator.generateAndSaveChart(
                            context = context,
                            channel = channel,
                            entries = feedEntries.reversed(),
                            appWidgetId = appWidgetId
                        )
                    } else null

                    updateWidgetPreferences(
                        context = context,
                        glanceId = glanceId,
                        channel = channel,
                        latestFeed = latestFeed,
                        chartFile = chartFile,
                        violatedMinFields = violatedMinFields,
                        violatedMaxFields = violatedMaxFields,
                        minSetFields = minSetFields,
                        maxSetFields = maxSetFields
                    )
                    when (widgetClass) {
                        ThingSpeakGlanceWidget::class.java -> ThingSpeakGlanceWidget().update(context, glanceId)
                        ValueGridWidget::class.java -> ValueGridWidget().update(context, glanceId)
                    }
                }
            }
        }
    }
}

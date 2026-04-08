package com.thingspeak.monitor.feature.chart.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thingspeak.monitor.core.error.ApiResult
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.thingspeak.monitor.feature.channel.domain.model.Channel
import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry
import com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase
import com.thingspeak.monitor.feature.channel.domain.usecase.GetHistoricalDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

sealed class ChartDataBundle {
    abstract val title: String
    abstract val timezone: String?
    
    data class Line(
        override val title: String,
        val lineData: LineData,
        val baselineX: Long,
        val timeScale: Float,
        val xAxisMin: Float,
        val xAxisMax: Float,
        val drawingStyle: com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle = com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC,
        val sampleTimestamps: List<Long> = emptyList(),
        override val timezone: String? = null
    ) : ChartDataBundle()

    data class Bar(
        override val title: String,
        val barData: BarData,
        val baselineX: Long,
        val timeScale: Float,
        val xAxisMin: Float,
        val xAxisMax: Float,
        val sampleTimestamps: List<Long> = emptyList(),
        override val timezone: String? = null
    ) : ChartDataBundle()
}

sealed class ChartState {
    object Loading : ChartState()
    data class Success(val charts: List<ChartDataBundle>) : ChartState()
    data class Error(val message: String) : ChartState()
    object Empty : ChartState()
}

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val getChannelFeedUseCase: GetChannelFeedUseCase,
    private val getHistoricalDataUseCase: GetHistoricalDataUseCase,
    private val repository: com.thingspeak.monitor.feature.channel.domain.repository.ChannelRepository,
    private val appPreferences: com.thingspeak.monitor.core.datastore.AppPreferences
) : ViewModel() {

    private val _channelId = MutableStateFlow<Long?>(null)
    private val _apiKey = MutableStateFlow<String?>(null)
    
    private val _currentRangeDays = MutableStateFlow(1)
    val currentRangeDays: StateFlow<Int> = _currentRangeDays.asStateFlow()

    private val _uiState = MutableStateFlow<ChartState>(ChartState.Loading)
    val uiState: StateFlow<ChartState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _channelData = MutableStateFlow<Channel?>(null)
    val channelName = _channelData.map { it?.name ?: "Channel" }.stateIn(viewModelScope, SharingStarted.Lazily, "Channel")
    val fieldNames = _channelData.map { it?.fieldNames ?: emptyMap() }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _isDailyRange = MutableStateFlow(true)
    val isDailyRange: StateFlow<Boolean> = _isDailyRange.asStateFlow()

    private val _isMergingEnabled = _channelData.map { it?.isMergingEnabled ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)
    val isMergingEnabled: StateFlow<Boolean> = _isMergingEnabled
    
    private val _drawingStyle = _channelData.map { 
        try { com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.valueOf(it?.drawingStyle ?: "CUBIC") }
        catch (e: Exception) { com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC }
    }.stateIn(viewModelScope, SharingStarted.Lazily, com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC)
    val drawingStyle: StateFlow<com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle> = _drawingStyle
    
    private var lastLoadedFeeds: List<FeedEntry> = emptyList()

    val isLiveMode = MutableStateFlow(false)
    private var autoRefreshJob: Job? = null

    init {
        android.util.Log.d("TS_DEBUG", "ChartViewModel INIT")
        observeChannelData()
    }

    fun setChannel(id: Long, apiKey: String?) {
        if (_channelId.value != id) {
            stableBaselineX = null
            lastLoadedFeeds = emptyList()
            _uiState.value = ChartState.Loading
        }
        _channelId.value = id
        _apiKey.value = apiKey
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeChannelData() {
        _channelId.flatMapLatest { id ->
            if (id != null) getChannelFeedUseCase.observeChannel(id) else flowOf(null)
        }
        .distinctUntilChanged()
        .onEach { channel ->
            _channelData.value = channel
            if (channel != null && lastLoadedFeeds.isNotEmpty()) {
                processCurrentData()
            }
        }
        .launchIn(viewModelScope)
    }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var stableBaselineX: Long? = null

    fun loadChartData(days: Int = _currentRangeDays.value, isSilent: Boolean = false) {
        android.util.Log.d("TS_DEBUG", "loadChartData START: days=$days, silent=$isSilent")
        if (_currentRangeDays.value != days) {
            stableBaselineX = null
        }
        val channelId = _channelId.value ?: return
        val apiKey = _apiKey.value
        _currentRangeDays.value = days

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!isSilent) _isRefreshing.value = true
            try {
                // Ensure we have channel settings before loading if possible
                if (_channelData.value == null) {
                    kotlinx.coroutines.delay(200)
                }

                val resultsLimit = _channelData.value?.chartResults ?: 500
                
                val averageParam = when (days) {
                    7 -> 60
                    30 -> 720
                    else -> null
                }
                
                // CRITICAL FIX: To prevent date regression for "1D" (24h), we must always use 'days' parameter.
                // resultsLimit will act as a cap within those days.
                // Agent 3.7.8: Increased results to 8000 for 1D as well to ensure full coverage
            val finalResults = if (days <= 1) 8000 else 8000
                val finalDays = if (days <= 0) 1 else days

                val startTime = System.currentTimeMillis()
                android.util.Log.d("TS_DEBUG", "loadChartData fetching: days=$finalDays, results=$finalResults, avg=$averageParam, limit=$resultsLimit")
                
                var result = getHistoricalDataUseCase(
                    channelId = channelId,
                    apiKey = apiKey,
                    days = finalDays,
                    results = finalResults,
                    average = averageParam
                )

                // FALLBACK LOGIC: If 7D/30D with average failed, try raw data as fallback with a SAFE limit.
                // We use a hard limit of 2000 points for fallback to prevent OOM/Timeouts on large ranges.
                if (result is ApiResult.Error && averageParam != null) {
                    android.util.Log.w("TS_DEBUG", "Averaged fetch failed (timeout?), retrying with RAW fallback (limit=2000) for $channelId")
                    result = getHistoricalDataUseCase(
                        channelId = channelId,
                        apiKey = apiKey,
                        days = finalDays,
                        results = 2000, 
                        average = null
                    )
                }

                when (result) {
                    is ApiResult.Success<*> -> {
                        val data = result.data
                        if (data is List<*>) {
                            lastLoadedFeeds = data.filterIsInstance<FeedEntry>()
                            
                            val startTs = lastLoadedFeeds.firstOrNull()?.createdAt ?: "EMPTY"
                            val endTs = lastLoadedFeeds.lastOrNull()?.createdAt ?: "EMPTY"
                            
                            // Initialize stable baseline from the very first feed of the session if not set
                            if (stableBaselineX == null && lastLoadedFeeds.isNotEmpty()) {
                                stableBaselineX = lastLoadedFeeds.first().createdAt.let {
                                    try { java.time.Instant.parse(it).epochSecond } catch (e: Exception) { null }
                                }
                                android.util.Log.i("TS_DEBUG", "Stable BaselineX initialized: $stableBaselineX")
                            }

                            android.util.Log.i("TS_DEBUG", "loadChartData SUCCESS: id=$channelId, received ${lastLoadedFeeds.size} entries. Range: [$startTs] - [$endTs]. Took ${System.currentTimeMillis() - startTime}ms")
                            
                            processCurrentData()
                        }
                    }
                    is ApiResult.Error -> {
                        val detailedMsg = "API ERROR: ${result.message} (id=$channelId, days=$finalDays)"
                        android.util.Log.e("TS_DEBUG", detailedMsg)
                        _uiState.value = ChartState.Error(detailedMsg)
                    }
                    else -> {
                        android.util.Log.e("TS_DEBUG", "Unknown result type for $channelId")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    android.util.Log.v("TS_DEBUG", "loadChartData cancelled (lifecycle) for $channelId")
                } else {
                    android.util.Log.e("TS_DEBUG", "CRASH in loadChartData (id=$channelId)", e)
                    _uiState.value = ChartState.Error("Unexpected crash: ${e.message}")
                }
            } finally {
                if (!isSilent) _isRefreshing.value = false
                android.util.Log.v("TS_DEBUG", "loadChartData FINISHED (finally) for $channelId")
            }
        }
    }

    fun refresh() = loadChartData()

    fun setDrawingStyle(style: com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle) {
        val currentChannel = _channelData.value ?: return
        viewModelScope.launch {
            repository.updateChannel(currentChannel.copy(drawingStyle = style.name))
        }
    }

    fun toggleLiveMode() {
        isLiveMode.value = !isLiveMode.value
        if (isLiveMode.value) {
            startAutoRefresh()
        } else {
            autoRefreshJob?.cancel()
            autoRefreshJob = null
        }
    }

    fun toggleMerging() {
        val currentChannel = _channelData.value ?: return
        viewModelScope.launch {
            repository.updateChannel(currentChannel.copy(isMergingEnabled = !currentChannel.isMergingEnabled))
        }
    }

    private fun processCurrentData() {
        if (lastLoadedFeeds.isEmpty()) {
            android.util.Log.w("TS_DEBUG", "processCurrentData: EMPTY feeds for id=${_channelId.value}")
            _uiState.value = ChartState.Empty
            return
        }
        
        viewModelScope.launch {
            val channel = _channelData.value
            val selectedFields = channel?.widgetVisibleFields ?: (1..8).toSet()
            val isNormalized = channel?.isNormalized ?: false
            _isDailyRange.value = (_currentRangeDays.value <= 1)

            // --- DEBUG LOGGING (Agent 3.7.7) ---
            val firstTs = try { java.time.Instant.parse(lastLoadedFeeds.first().createdAt).epochSecond } catch (e: Exception) { -1L }
            val lastTs = try { java.time.Instant.parse(lastLoadedFeeds.last().createdAt).epochSecond } catch (e: Exception) { -1L }
            android.util.Log.i("TS_DEBUG", "API Returned ${lastLoadedFeeds.size} feeds. Range: $firstTs to $lastTs. Requested Days: ${_currentRangeDays.value}")
            // -----------------------------------

            val resultsLimit = channel?.chartResults ?: 60
            val startTime = System.currentTimeMillis()
            android.util.Log.v("TS_DEBUG", "processCurrentData: processing ${lastLoadedFeeds.size} points...")

            val bundles = withContext(Dispatchers.Default) {
                ChartDataProcessor.processFeedsToBundles(
                    feeds = lastLoadedFeeds,
                    currentRangeDays = _currentRangeDays.value,
                    selectedFields = selectedFields,
                    isMergingEnabled = _isMergingEnabled.value,
                    isNormalized = isNormalized,
                    fieldNames = fieldNames.value,
                    chartColor = channel?.chartColor,
                    fieldColorsMap = channel?.fieldColors ?: emptyMap(),
                    resultsLimit = resultsLimit,
                    baselineXOverride = stableBaselineX,
                    processingType = channel?.chartProcessingType ?: "NONE",
                    drawingStyle = when (channel?.chartType?.lowercase()) {
                        "bar", "column" -> com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.BAR
                        "area" -> com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.AREA
                        "scatter" -> com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.SCATTER
                        "spline", "cubic" -> com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.CUBIC
                        "step" -> com.thingspeak.monitor.feature.chart.presentation.model.LineDrawingStyle.STEPPED
                        else -> _drawingStyle.value
                    },
                    timezone = channel?.timezone
                )
            }
            android.util.Log.d("TS_DEBUG", "processCurrentData COMPLETED: id=${channel?.id}, bundles=${bundles.size}, stableBaselineX=$stableBaselineX. Took ${System.currentTimeMillis() - startTime}ms")
            if (bundles.isEmpty()) {
                android.util.Log.w("TS_DEBUG", "processCurrentData: BUNDLES EMPTY after processing!")
            } else {
                bundles.forEachIndexed { index, bundle ->
                    android.util.Log.v("TS_DEBUG", "Bundle[$index]: title=${bundle.title}")
                }
            }
            _uiState.value = if (bundles.isEmpty()) ChartState.Empty else ChartState.Success(bundles)
        }
    }

    fun setDateRange(start: Long, end: Long) {
        val diff = (end - start) / (1000 * 60 * 60 * 24)
        loadChartData(diff.toInt().coerceAtLeast(1))
    }
    
    fun exportCsv(): String {
        if (lastLoadedFeeds.isEmpty()) return ""
        
        val channel = _channelData.value
        val visibleIndices = channel?.widgetVisibleFields ?: (1..8).toSet()
        val exportFieldNames = visibleIndices.sorted().associateWith { 
            channel?.fieldNames?.get(it) ?: "Field $it"
        }
        val timezone = channel?.timezone
        
        val csvData = com.thingspeak.monitor.core.utils.ExportUtils.generateCsv(
            entries = lastLoadedFeeds,
            fieldNames = exportFieldNames,
            timezone = timezone
        )
        
        // Prepended UTF-8 BOM for Excel compatibility
        return "\ufeff$csvData"
    }

    fun exportPdf(stream: java.io.OutputStream, name: String) {
        if (lastLoadedFeeds.isEmpty()) return
        val channel = _channelData.value
        val channelName = channel?.name ?: name
        val visibleIndices = channel?.widgetVisibleFields ?: (1..8).toSet()
        val exportFieldNames = visibleIndices.sorted().associateWith { 
            channel?.fieldNames?.get(it) ?: "Field $it"
        }
        val timezone = channel?.timezone
        
        try {
            com.thingspeak.monitor.core.utils.ExportUtils.writePdfReport(
                outputStream = stream,
                channelName = channelName,
                entries = lastLoadedFeeds,
                fieldNames = exportFieldNames,
                timezone = timezone
            )
        } catch (e: Exception) {
            android.util.Log.e("TS_DEBUG", "Error exporting PDF", e)
        }
    }

    /**
     * Auto-refresh loop for charts.
     * Pulls fresh data from ThingSpeak API at the interval configured in AppPreferences.
     * Job is automatically cancelled when ViewModel is destroyed (viewModelScope).
     */
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            appPreferences.observeRefreshInterval().collectLatest { interval ->
                // Minimum 1s interval for charts to avoid API throttling
                val safeInterval = interval.coerceAtLeast(1000L)
                android.util.Log.i("TS_DEBUG", "ChartViewModel autoRefresh STARTED: interval=${safeInterval}ms")
                while (isActive) {
                    delay(safeInterval)
                    val channelId = _channelId.value ?: continue
                    android.util.Log.v("TS_DEBUG", "ChartViewModel autoRefresh TICK for $channelId")
                    loadChartData(isSilent = true)
                }
            }
        }
    }
}

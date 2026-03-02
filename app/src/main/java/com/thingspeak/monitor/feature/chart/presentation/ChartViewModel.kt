package com.thingspeak.monitor.feature.chart.presentation

import com.thingspeak.monitor.feature.channel.domain.model.FeedEntry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.thingspeak.monitor.core.error.ApiResult
import com.thingspeak.monitor.feature.channel.domain.usecase.GetHistoricalDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.thingspeak.monitor.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thingspeak.monitor.core.utils.MathUtils
import java.time.Instant
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import java.lang.Exception

private fun safeToFloat(value: Any?): Float? {
    return when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }
}

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getHistoricalDataUseCase: GetHistoricalDataUseCase,
    private val getChannelFeed: com.thingspeak.monitor.feature.channel.domain.usecase.GetChannelFeedUseCase,
    private val appPreferences: com.thingspeak.monitor.core.datastore.AppPreferences,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val channelId: Long = savedStateHandle.get<Long>("channelId") ?: -1L
    private val apiKey: String? = savedStateHandle["apiKey"]

    private val _uiState = MutableStateFlow<ChartState>(ChartState.Loading)
    val uiState: StateFlow<ChartState> = _uiState.asStateFlow()

    private val _isDailyRange = MutableStateFlow(true)
    val isDailyRange: StateFlow<Boolean> = _isDailyRange.asStateFlow()

    private val _selectedFields = MutableStateFlow<Set<Int>>(emptySet())
    val selectedFields: StateFlow<Set<Int>> = _selectedFields.asStateFlow()

    private val _dataFilter = MutableStateFlow(DataFilter.NONE)
    val dataFilter: StateFlow<DataFilter> = _dataFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Instant, Instant>?>(null)
    val customDateRange: StateFlow<Pair<Instant, Instant>?> = _customDateRange.asStateFlow()

    private val _isSmoothingEnabled = MutableStateFlow(false)
    val isSmoothingEnabled: StateFlow<Boolean> = _isSmoothingEnabled.asStateFlow()

    private val _isMergingEnabled = MutableStateFlow(false)
    val isMergingEnabled: StateFlow<Boolean> = _isMergingEnabled.asStateFlow()

    private val _rawEntries = MutableStateFlow<List<FeedEntry>>(emptyList())
    
    private val _isLiveMode = MutableStateFlow(false)
    val isLiveMode: StateFlow<Boolean> = _isLiveMode.asStateFlow()

    private var loadJob: Job? = null
    private var pollingJob: Job? = null
    private val _currentRangeDays = MutableStateFlow(1)
    val currentRangeDays: StateFlow<Int> = _currentRangeDays.asStateFlow()
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Observe field names and name once and keep them updated
    private val channelData = getChannelFeed.observeChannel(channelId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val fieldNames: StateFlow<Map<Int, String>> = channelData
        .map { it?.fieldNames ?: emptyMap() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val channelName: StateFlow<String> = channelData
        .map { it?.name ?: "Channel $channelId" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "Loading..."
        )

    init {
        if (channelId == -1L) {
            _uiState.value = ChartState.Error("Invalid Channel ID")
        } else {
            // Restore persistent settings
            viewModelScope.launch {
                val range = appPreferences.observeChartRange().first()
                _currentRangeDays.value = range
                _isDailyRange.value = range == 1

                _isSmoothingEnabled.value = appPreferences.observeChartSmoothing().first()

                val filterName = appPreferences.observeChartFilter().first()
                _dataFilter.value = try {
                    DataFilter.valueOf(filterName)
                } catch (e: Exception) {
                    DataFilter.NONE
                }

                // Restore field selection per channel
                val channel = getChannelFeed.observeChannel(channelId).first()
                _selectedFields.value = channel?.preferredChartFields ?: emptySet()

                _isMergingEnabled.value = appPreferences.observeChartMerging().first()

                // Launch a separate job to handle initial field auto-selection and data loading
                // to avoid blocking this coroutine
                launch {
                    fieldNames.collect { names ->
                        if (names.isNotEmpty()) {
                            if (_selectedFields.value.isEmpty()) {
                                _selectedFields.value = names.keys
                            }
                            // Initial load or refresh on field changes
                            loadChartData(_currentRangeDays.value, silent = _uiState.value is ChartState.Success)
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        loadChartData(_currentRangeDays.value)
    }

    /**
     * Loads chart data for the current channel.
     * @param days number of days (predefined). If > 0, clears customDateRange.
     */
    fun loadChartData(days: Int = 1, silent: Boolean = false, customRange: Pair<Instant, Instant>? = null) {
        _currentRangeDays.value = days
        
        if (customRange != null) {
            _customDateRange.value = customRange
            _isDailyRange.value = Duration.between(customRange.first, customRange.second).toHours() <= 24
        } else {
            if (days >= 1) {
                _customDateRange.value = null
            }
            _isDailyRange.value = days <= 1
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            appPreferences.setChartRange(days)
            if (!silent) {
                _isRefreshing.value = true
                _uiState.value = ChartState.Loading
            }
            _isDailyRange.value = days <= 1

            val currentRange = _customDateRange.value
            val endInstant = currentRange?.second ?: Instant.now()
            val startInstant = currentRange?.first ?: endInstant.minus(days.toLong(), ChronoUnit.DAYS)
            
            val startStr = startInstant.toString()
            val endStr = endInstant.toString()
            
            val average = when (days) {
                7 -> 60
                30 -> 1440
                else -> null
            }

            when (val result = getHistoricalDataUseCase(channelId, apiKey, startStr, endStr, average)) {
                is ApiResult.Success -> {
                    processChartData(result.data)
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ChartViewModel", "Error loading chart data: ${result.message}")
                    _uiState.value = ChartState.Error(result.message ?: "Unknown Error")
                }
                is ApiResult.Exception -> {
                    android.util.Log.e("ChartViewModel", "Exception loading chart data", result.e)
                    _uiState.value = ChartState.Error(result.e.localizedMessage ?: "Exception")
                }
                is ApiResult.Loading -> {
                     _uiState.value = ChartState.Loading
                }
            }
            if (!silent) {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Processes raw feed entries into discrete ChartItems asynchronously on Dispatchers.Default.
     */
    private suspend fun processChartData(entries: List<com.thingspeak.monitor.feature.channel.domain.model.FeedEntry>) {
        _rawEntries.value = entries
        if (entries.isEmpty()) {
            _uiState.value = ChartState.Empty
            return
        }

        val result = withContext(defaultDispatcher) {
             val dataSetsMap = mutableMapOf<Int, MutableList<Entry>>()
             
             // Step 1: Find baseline (minimum timestamp) for all points to preserve Float precision
             var minTimestamp = Long.MAX_VALUE
             var maxTimestamp = Long.MIN_VALUE
             
             entries.forEach { feed ->
                 try {
                     val ts = Instant.parse(feed.createdAt).epochSecond
                     if (ts < minTimestamp) minTimestamp = ts
                     if (ts > maxTimestamp) maxTimestamp = ts
                 } catch (e: Exception) { /* skip */ }
             }
             
             if (minTimestamp == Long.MAX_VALUE) return@withContext null
             val baselineX = minTimestamp
             val rangeSeconds = maxTimestamp - minTimestamp
             
             // If range exceeds Float precision limit (16.7M seconds), use minutes (factor 60)
             // This preserves precision for up to ~30 years (16.7M * 60 seconds)
             val timeScale = if (rangeSeconds > 10_000_000) 60f else 1f

             entries.forEach { feed ->
                 val timestampStr = feed.createdAt
                 val timestampLong = try {
                     Instant.parse(timestampStr).epochSecond
                 } catch (e: Exception) {
                     return@forEach
                 }
                 
                  // Use offset from baseline and apply scale to maintain high precision
                  val xValue = (timestampLong - baselineX).toFloat() / timeScale

                 feed.fields.forEach { (fieldIndex, valueStr) ->
                     if (_selectedFields.value.contains(fieldIndex)) {
                         val valueFloat = safeToFloat(valueStr)
                         if (valueFloat != null && !valueFloat.isNaN()) {
                             val list = dataSetsMap.getOrPut(fieldIndex) { mutableListOf() }
                             list.add(Entry(xValue, valueFloat))
                         }
                     }
                 }
             }

             if (dataSetsMap.isEmpty()) {
                 return@withContext null
             }

             val colors = listOf(
                 android.graphics.Color.parseColor("#E53935"), // Red
                 android.graphics.Color.parseColor("#1E88E5"), // Blue
                 android.graphics.Color.parseColor("#43A047"), // Green
                 android.graphics.Color.parseColor("#FDD835"), // Yellow
                 android.graphics.Color.parseColor("#8E24AA"), // Purple
                 android.graphics.Color.parseColor("#F4511E"), // Deep Orange
                 android.graphics.Color.parseColor("#00ACC1"), // Cyan
                 android.graphics.Color.parseColor("#7CB342")  // Light Green
             )

             val merging = _isMergingEnabled.value
             
             if (merging) {
                 // MERGED MODE: One LineData with multiple DataSets
                 val dataSets = dataSetsMap.entries.mapIndexed { index, mapEntry ->
                     val fieldIndex = mapEntry.key
                     val sortedEntries = mapEntry.value.sortedBy { it.x }
                     val filteredEntries = applyFilter(sortedEntries, _dataFilter.value)
                     val smoothedEntries = if (_isSmoothingEnabled.value) MathUtils.applyMovingAverage(filteredEntries, 5) else filteredEntries
                     val points = if (smoothedEntries.size > 200) com.thingspeak.monitor.core.utils.ChartUtils.downsampleLTTB(smoothedEntries, 200) else smoothedEntries

                     val label = fieldNames.value[fieldIndex] ?: "Field $fieldIndex"
                     LineDataSet(points, label).apply {
                         mode = LineDataSet.Mode.LINEAR // CLEANER LOOK
                         setDrawCircles(false)
                         setDrawValues(false)
                         lineWidth = 2.5f
                         color = colors[index % colors.size]
                         highLightColor = colors[index % colors.size]
                     }
                 }
                 listOf(ChartDataBundle(LineData(dataSets), baselineX, timeScale, "Merged Fields"))
             } else {
                 // SEPARATE MODE: List of LineData bundles, each with one DataSet
                 dataSetsMap.entries.mapIndexed { index, mapEntry ->
                     val fieldIndex = mapEntry.key
                     val sortedEntries = mapEntry.value.sortedBy { it.x }
                     val filteredEntries = applyFilter(sortedEntries, _dataFilter.value)
                     val smoothedEntries = if (_isSmoothingEnabled.value) MathUtils.applyMovingAverage(filteredEntries, 5) else filteredEntries
                     val points = if (smoothedEntries.size > 200) com.thingspeak.monitor.core.utils.ChartUtils.downsampleLTTB(smoothedEntries, 200) else smoothedEntries

                     val label = fieldNames.value[fieldIndex] ?: "Field $fieldIndex"
                     val dataSet = LineDataSet(points, label).apply {
                         mode = LineDataSet.Mode.LINEAR // CLEANER LOOK
                         setDrawCircles(false)
                         setDrawValues(false)
                         lineWidth = 2.5f
                         color = colors[index % colors.size]
                         highLightColor = colors[index % colors.size]
                     }
                     ChartDataBundle(LineData(dataSet), baselineX, timeScale, label)
                 }
             }
        }
 
        if (result != null) {
            _uiState.value = ChartState.Success(result, _isMergingEnabled.value)
        } else {
            _uiState.value = ChartState.Empty
        }
    }

    private fun applyFilter(entries: List<Entry>, filter: DataFilter): List<Entry> {
        return when (filter) {
            DataFilter.NONE -> entries
            DataFilter.ROUND -> entries.map { Entry(it.x, Math.round(it.y).toFloat()) }
            DataFilter.MEAN -> applyWindowFilter(entries) { window -> window.map { it.y }.average().toFloat() }
            DataFilter.MEDIAN -> applyWindowFilter(entries) { window -> 
                val sorted = window.map { it.y }.sorted()
                if (sorted.size % 2 == 0) {
                    (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2
                } else {
                    sorted[sorted.size / 2]
                }
            }
            DataFilter.SUM -> applyWindowFilter(entries) { window -> window.sumOf { it.y.toDouble() }.toFloat() }
        }
    }

    private fun applyWindowFilter(entries: List<Entry>, windowSize: Int = 5, operation: (List<Entry>) -> Float): List<Entry> {
        if (entries.size < windowSize) return entries
        return entries.mapIndexed { index, entry ->
            val start = (index - windowSize / 2).coerceAtLeast(0)
            val end = (index + windowSize / 2).coerceAtMost(entries.size - 1)
            val window = entries.subList(start, end + 1)
            Entry(entry.x, operation(window))
        }
    }

    fun toggleField(fieldIndex: Int) {
        val current = _selectedFields.value.toMutableSet()
        if (current.contains(fieldIndex)) {
            if (current.size > 1) current.remove(fieldIndex)
        } else {
            current.add(fieldIndex)
        }
        _selectedFields.value = current
        
        // Persist preferred fields for this channel
        viewModelScope.launch {
            getChannelFeed.observeChannel(channelId).first()?.let { channel ->
                getChannelFeed.updateChannel(channel.copy(preferredChartFields = current))
            }
        }
        
        refreshData()
    }

    fun setFilter(filter: DataFilter) {
        _dataFilter.value = filter
        viewModelScope.launch {
            appPreferences.setChartFilter(filter.name)
        }
        refreshData()
    }

    fun toggleSmoothing() {
        _isSmoothingEnabled.value = !_isSmoothingEnabled.value
        viewModelScope.launch {
            appPreferences.setChartSmoothing(_isSmoothingEnabled.value)
        }
        refreshData()
    }

    fun toggleLiveMode() {
        _isLiveMode.value = !_isLiveMode.value
        if (_isLiveMode.value) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val intervalMs = appPreferences.observeRefreshInterval().first()
                    delay(maxOf(1000L, intervalMs))
                    
                    if (_isLiveMode.value) {
                        refreshLatestData()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChartViewModel", "Polling loop error", e)
                    delay(5000) // Backoff on error
                }
            }
        }
    }

    private suspend fun refreshLatestData() {
        val results = 5 // Fetch last 5 to avoid gaps
        when (val result = getChannelFeed.refresh(channelId, apiKey, results = results)) {
            is ApiResult.Success -> {
                // Since GetChannelFeedUseCase.refresh updates local DB, 
                // but we manage _rawEntries manually for historical charts,
                // we should fetch the actual entries too or just use what refresh returned.
                // Wait, GetChannelFeedUseCase.refresh returns ApiResult<Unit>.
                // Better approach: Call a specific fetch method for latest entries.
                
                val latestResult = getHistoricalDataUseCase(
                    channelId = channelId,
                    apiKey = apiKey,
                    start = Instant.now().minus(5, ChronoUnit.MINUTES).toString(),
                    end = Instant.now().toString()
                )
                
                if (latestResult is ApiResult.Success) {
                    appendEntries(latestResult.data)
                }
            }
            else -> { /* Ignore errors in background polling */ }
        }
    }

    private suspend fun appendEntries(newEntries: List<FeedEntry>) {
        if (newEntries.isEmpty()) return
        
        val currentEntries = _rawEntries.value
        val existingIds = currentEntries.map { it.entryId }.toSet()
        val uniqueNew = newEntries.filter { !existingIds.contains(it.entryId) }
        
        if (uniqueNew.isNotEmpty()) {
            val combined = (currentEntries + uniqueNew).sortedBy { it.createdAt }
            // Keep memory in check for long sessions
            val trimmed = if (combined.size > 2000) combined.takeLast(2000) else combined
            processChartData(trimmed)
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun setDateRange(start: Instant, end: Instant) {
        loadChartData(days = 0, customRange = start to end)
    }

    private fun refreshData() {
        // Simple refresh using last known data if possible or reload
        loadChartData()
    }

    fun toggleMerging() {
        _isMergingEnabled.value = !_isMergingEnabled.value
        viewModelScope.launch {
            appPreferences.setChartMerging(_isMergingEnabled.value)
        }
        refreshData()
    }

    fun exportCsv(): String {
        return com.thingspeak.monitor.core.utils.ExportUtils.generateCsv(
            _rawEntries.value,
            fieldNames.value
        )
    }

    fun exportPdf(outputStream: java.io.OutputStream, channelName: String) {
        com.thingspeak.monitor.core.utils.ExportUtils.writePdfReport(
            outputStream,
            channelName,
            _rawEntries.value,
            fieldNames.value
        )
    }
}

package com.thingspeak.monitor.feature.chart.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingspeak.monitor.R
import com.thingspeak.monitor.feature.chart.presentation.components.DateRangePickerDialog
import com.thingspeak.monitor.feature.chart.presentation.components.ThingSpeakLineChart
import com.thingspeak.monitor.feature.chart.presentation.components.ThingSpeakBarChart
import com.thingspeak.monitor.core.ui.shimmer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    onNavigateBack: () -> Unit,
    channelId: Long,
    apiKey: String?,
    modifier: Modifier = Modifier,
    viewModel: ChartViewModel = hiltViewModel()
) {
    android.util.Log.d(com.thingspeak.monitor.core.utils.APP_LOG_TAG, "ChartScreen COMPOSE: channelId=$channelId")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDailyRange by viewModel.isDailyRange.collectAsStateWithLifecycle()
    
    val fieldNames by viewModel.fieldNames.collectAsStateWithLifecycle()
    val channelName by viewModel.channelName.collectAsStateWithLifecycle()
    val isLiveMode by viewModel.isLiveMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var fullscreenChart by remember { mutableStateOf<ChartDataBundle?>(null) }
    
    val listState = rememberLazyListState()

    LaunchedEffect(channelId) {
        viewModel.setChannel(channelId, apiKey)
        viewModel.refresh()
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val outputStream = try { 
                context.contentResolver.openOutputStream(it) 
            } catch (e: Exception) { 
                null 
            }
            
            if (outputStream != null) {
                scope.launch {
                    try {
                        val csvContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            viewModel.exportCsv()
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            outputStream.use { stream ->
                                stream.write(csvContent.toByteArray(Charsets.UTF_8))
                            }
                        }
                        snackbarHostState.showSnackbar(context.getString(R.string.chart_export_success))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(context.getString(R.string.chart_export_error) + ": ${e.message}")
                    }
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            val outputStream = try {
                context.contentResolver.openOutputStream(it)
            } catch (e: Exception) {
                null
            }

            if (outputStream != null) {
                scope.launch {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            outputStream.use { stream ->
                                viewModel.exportPdf(stream, channelName)
                            }
                        }
                        snackbarHostState.showSnackbar(context.getString(R.string.chart_export_success))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(context.getString(R.string.chart_export_error) + ": ${e.message}")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(channelName, style = MaterialTheme.typography.titleMedium)
                        Text(if (isDailyRange) "Today" else "Historical", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.chart_select_range))
                    }
                    val isMerged by viewModel.isMergingEnabled.collectAsStateWithLifecycle()
                    IconButton(onClick = { viewModel.toggleMerging() }) {
                        Icon(
                            imageVector = if (isMerged) Icons.Default.CallMerge else Icons.Default.CallSplit,
                            contentDescription = stringResource(R.string.chart_toggle_merging),
                            tint = if (isMerged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.chart_export))
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chart_export_csv)) },
                                onClick = {
                                    showExportMenu = false
                                    csvLauncher.launch("channel_${channelName.replace(" ", "_")}.csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chart_export_pdf)) },
                                onClick = {
                                    showExportMenu = false
                                    pdfLauncher.launch("report_${channelName.replace(" ", "_")}.pdf")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val currentRangeDays by viewModel.currentRangeDays.collectAsStateWithLifecycle()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentRangeDays == 1,
                    onClick = { viewModel.loadChartData(1) },
                    label = { Text("1D") }
                )
                FilterChip(
                    selected = currentRangeDays == 7,
                    onClick = { viewModel.loadChartData(7) },
                    label = { Text("7D") }
                )
                FilterChip(
                    selected = currentRangeDays == 30,
                    onClick = { viewModel.loadChartData(30) },
                    label = { Text("30D") }
                )
                
                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("LIVE", style = MaterialTheme.typography.labelMedium, color = if (isLiveMode) Color.Red else MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isLiveMode,
                        onCheckedChange = { viewModel.toggleLiveMode() },
                        modifier = Modifier.scale(0.7f),
                        thumbContent = if (isLiveMode) {
                            { Box(modifier = Modifier.size(6.dp).background(Color.Red, CircleShape)) }
                        } else null
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val successState = uiState as? ChartState.Success
                    if (successState != null) {
                        ChartSuccessContent(
                            state = successState,
                            listState = listState,
                            viewModel = viewModel,
                            isDailyRange = isDailyRange,
                            onFullscreen = { fullscreenChart = it }
                        )
                    }

                    AnimatedContent(
                        targetState = uiState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ChartStatusAnimation"
                    ) { state ->
                        when (state) {
                            is ChartState.Loading -> ShimmerChart()
                            is ChartState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                            is ChartState.Empty -> EmptyState()
                            is ChartState.Success -> { /* Persistent Box handles this */ }
                        }
                    }
                }
            }
        }

        if (fullscreenChart != null) {
            Dialog(
                onDismissRequest = { fullscreenChart = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fullscreenChart?.title ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            IconButton(onClick = { fullscreenChart = null }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                            when (val bundle = fullscreenChart!!) {
                                is ChartDataBundle.Line -> {
                                    ThingSpeakLineChart(
                                        lineData = bundle.lineData,
                                        isDailyRange = isDailyRange,
                                        baselineX = bundle.baselineX,
                                        timeScale = bundle.timeScale,
                                        xAxisMin = bundle.xAxisMin,
                                        xAxisMax = bundle.xAxisMax,
                                        drawingStyle = bundle.drawingStyle,
                                        sampleTimestamps = bundle.sampleTimestamps,
                                        timezone = bundle.timezone,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                is ChartDataBundle.Bar -> {
                                    ThingSpeakBarChart(
                                        barData = bundle.barData,
                                        isDailyRange = isDailyRange,
                                        baselineX = bundle.baselineX,
                                        timeScale = bundle.timeScale,
                                        xAxisMin = bundle.xAxisMin,
                                        xAxisMax = bundle.xAxisMax,
                                        isHorizontal = bundle.isHorizontal,
                                        sampleTimestamps = bundle.sampleTimestamps,
                                        timezone = bundle.timezone,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDateRangePicker) {
            DateRangePickerDialog(
                onDismiss = { showDateRangePicker = false },
                onDateRangeSelected = { start, end ->
                    viewModel.setDateRange(start.toEpochMilli(), end.toEpochMilli())
                    showDateRangePicker = false
                }
            )
        }
    }
}

@Composable
private fun ChartSuccessContent(
    state: ChartState.Success,
    listState: LazyListState,
    viewModel: ChartViewModel,
    isDailyRange: Boolean,
    onFullscreen: (ChartDataBundle) -> Unit
) {
    var activeChartTitle by remember { mutableStateOf<String?>(null) }
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = activeChartTitle == null
    ) {
        items(
            items = state.charts,
            key = { bundle -> bundle.title }
        ) { bundle ->
            ChartCard(
                bundle = bundle,
                isDailyRange = isDailyRange,
                isActive = activeChartTitle == bundle.title,
                onInteraction = { interacting ->
                    activeChartTitle = if (interacting) bundle.title else null
                },
                onFullscreen = { onFullscreen(bundle) }
            )
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.chart_retry)) }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.chart_empty_data), textAlign = TextAlign.Center)
    }
}

@Composable
fun ShimmerChart() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) {
            Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(24.dp).shimmer())
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxSize().shimmer())
                }
            }
        }
    }
}

@Composable
fun ChartCard(
    bundle: ChartDataBundle,
    isDailyRange: Boolean,
    isActive: Boolean = false,
    onInteraction: (Boolean) -> Unit = {},
    onFullscreen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .zIndex(if (isActive) 10f else 0f),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = bundle.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (bundle) {
                    is ChartDataBundle.Line -> {
                        ThingSpeakLineChart(
                            lineData = bundle.lineData,
                            isDailyRange = isDailyRange,
                            baselineX = bundle.baselineX,
                            timeScale = bundle.timeScale,
                            xAxisMin = bundle.xAxisMin,
                            xAxisMax = bundle.xAxisMax,
                            drawingStyle = bundle.drawingStyle,
                            sampleTimestamps = bundle.sampleTimestamps,
                            timezone = bundle.timezone,
                            onInteraction = onInteraction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is ChartDataBundle.Bar -> {
                        ThingSpeakBarChart(
                            barData = bundle.barData,
                            isDailyRange = isDailyRange,
                            baselineX = bundle.baselineX,
                            timeScale = bundle.timeScale,
                            xAxisMin = bundle.xAxisMin,
                            xAxisMax = bundle.xAxisMax,
                            isHorizontal = bundle.isHorizontal,
                            sampleTimestamps = bundle.sampleTimestamps,
                            timezone = bundle.timezone,
                            onInteraction = onInteraction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

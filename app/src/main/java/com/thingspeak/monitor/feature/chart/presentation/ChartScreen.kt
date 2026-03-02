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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingspeak.monitor.R
import com.thingspeak.monitor.feature.chart.presentation.components.DateRangePickerDialog
import com.thingspeak.monitor.feature.chart.presentation.components.ThingSpeakLineChart
import com.thingspeak.monitor.core.ui.shimmer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChartViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDailyRange by viewModel.isDailyRange.collectAsStateWithLifecycle()
    val selectedFields by viewModel.selectedFields.collectAsStateWithLifecycle()
    val dataFilter by viewModel.dataFilter.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    
    val fieldNames by viewModel.fieldNames.collectAsStateWithLifecycle()
    val channelName by viewModel.channelName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var fullscreenChart by remember { mutableStateOf<ChartDataBundle?>(null) }

    // Logic for launchers
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    // Generowanie CSV może chwilę zająć, więc przenosimy to z Main do Dispatchers.IO
                    val csvContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        viewModel.exportCsv()
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(csvContent.toByteArray(Charsets.UTF_8))
                        }
                    }
                    snackbarHostState.showSnackbar(context.getString(R.string.chart_export_success))
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(context.getString(R.string.chart_export_error))
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    viewModel.exportPdf(stream, channelName)
                }
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.chart_export_success)) }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.chart_export_error)) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chart_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Select Range")
                    }
                    // Live Mode Toggle (Replaced standard refresh)
                    val isLiveMode by viewModel.isLiveMode.collectAsStateWithLifecycle()
                    IconButton(onClick = { viewModel.toggleLiveMode() }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Live Mode",
                                tint = if (isLiveMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isLiveMode) {
                                LiveIndicator(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (2).dp, y = (-2).dp)
                                )
                            }
                        }
                    }
                    val isMerged by viewModel.isMergingEnabled.collectAsStateWithLifecycle()
                    IconButton(onClick = { viewModel.toggleMerging() }) {
                        Icon(
                            imageVector = if (isMerged) Icons.Default.CallMerge else Icons.Default.CallSplit,
                            contentDescription = "Toggle Merging",
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
            // Field Selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fieldNames.forEach { (index, name) ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandHorizontally()
                    ) {
                        FilterChip(
                            selected = selectedFields.contains(index),
                            onClick = { viewModel.toggleField(index) },
                            label = { Text(name) }
                        )
                    }
                }
            }

            // Range Selection & Smoothing
            val currentRangeDays by viewModel.currentRangeDays.collectAsStateWithLifecycle()
            val isSmoothingEnabled by viewModel.isSmoothingEnabled.collectAsStateWithLifecycle()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Range Chips
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
                
                // Smoothing Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Smooth", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = isSmoothingEnabled,
                        onCheckedChange = { viewModel.toggleSmoothing() },
                        modifier = Modifier.scale(0.7f)
                    )
                }

                var showFilterMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        onClick = { showFilterMenu = true },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = dataFilter.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change Filter",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        DataFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(filter.name) }, onClick = {
                                viewModel.setFilter(filter)
                                showFilterMenu = false
                            })
                        }
                    }
                }
            }

            // Chart Content
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(
                    targetState = uiState,
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(androidx.compose.animation.core.tween(500)) togetherWith
                                fadeOut(androidx.compose.animation.core.tween(300))
                    },
                    label = "ChartContentAnimation"
                ) { state ->
                    when (state) {
                        is ChartState.Loading -> ShimmerChart()
                        is ChartState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh() })
                        is ChartState.Empty -> EmptyState()
                        is ChartState.Success -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(state.charts) { bundle ->
                                    ChartCard(
                                        bundle = bundle,
                                        isDailyRange = isDailyRange,
                                        onFullscreen = { fullscreenChart = bundle }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Overlay
    if (fullscreenChart != null) {
        Dialog(
            onDismissRequest = { fullscreenChart = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fullscreenChart?.title ?: "Fullscreen Chart",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        IconButton(onClick = { fullscreenChart = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                        ThingSpeakLineChart(
                            lineData = fullscreenChart!!.lineData,
                            isDailyRange = isDailyRange,
                            baselineX = fullscreenChart!!.baselineX,
                            timeScale = fullscreenChart!!.timeScale,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRange(start, end)
                showDateRangePicker = false
            }
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning Indicator",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No Data Indicator",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "No data available for the selected period", textAlign = TextAlign.Center)
    }
}
@Composable
fun LiveIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(10.dp)
            .background(
                color = androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = alpha),
                shape = CircleShape
            )
    )
}

@Composable
fun ShimmerChart() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(24.dp)
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shimmer()
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ChartScreenPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        ChartScreen(
            onNavigateBack = {}
        )
    }
}
@Composable
fun ChartCard(
    bundle: ChartDataBundle,
    isDailyRange: Boolean,
    onFullscreen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bundle.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = onFullscreen) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                ThingSpeakLineChart(
                    lineData = bundle.lineData,
                    isDailyRange = isDailyRange,
                    baselineX = bundle.baselineX,
                    timeScale = bundle.timeScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

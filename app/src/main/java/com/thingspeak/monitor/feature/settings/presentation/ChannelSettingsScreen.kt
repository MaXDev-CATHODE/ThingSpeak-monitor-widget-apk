package com.thingspeak.monitor.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingspeak.monitor.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsScreen(
    channelId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToAlertRules: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelSettingsViewModel = hiltViewModel()
) {
    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.channel?.name ?: "Channel Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Chart Settings
                Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Chart Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chart Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Rounding decimal places: ${uiState.channel?.chartRounding ?: 2}")
                    Slider(
                        value = (uiState.channel?.chartRounding ?: 2).toFloat(),
                        onValueChange = { viewModel.updateChartSettings(rounding = it.toInt()) },
                        valueRange = 0f..4f,
                        steps = 3
                    )
                }

                HorizontalDivider()

                // Alerts Section
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alert Rules",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alert Rules",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TextButton(onClick = { onNavigateToAlertRules(channelId) }) {
                            Text("Configure")
                        }
                    }
                    Text(
                        text = "Configure thresholds to receive notifications when values go out of range.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ChannelSettingsScreenPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        ChannelSettingsScreen(
            channelId = 12345,
            onNavigateBack = {},
            onNavigateToAlertRules = {}
        )
    }
}

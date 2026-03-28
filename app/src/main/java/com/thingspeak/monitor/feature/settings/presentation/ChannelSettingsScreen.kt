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
                title = { Text(uiState.channel?.name ?: stringResource(R.string.channel_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chart_back)
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
                            contentDescription = stringResource(R.string.widget_content_chart),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.widget_content_chart),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.settings_rounding, uiState.channel?.chartRounding ?: 2))
                    Slider(
                        value = (uiState.channel?.chartRounding ?: 2).toFloat(),
                        onValueChange = { viewModel.updateChartSettings(rounding = it.toInt()) },
                        valueRange = 0f..4f,
                        steps = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Merge Charts", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Combine all fields into a single chart view",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.channel?.isMergingEnabled ?: false,
                            onCheckedChange = { viewModel.updateChartSettings(isMergingEnabled = it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Normalization Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Normalized Mode (0-100%)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Scale all fields to 0-100% for comparison",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.channel?.isNormalized ?: false,
                            onCheckedChange = { viewModel.updateChartSettings(isNormalized = it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Removed duplicated EightFieldSelector group
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
                                contentDescription = stringResource(R.string.settings_alert_rules),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_alert_rules),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TextButton(onClick = { onNavigateToAlertRules(channelId) }) {
                            Text(stringResource(R.string.settings_configure))
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_alert_rules_desc),
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

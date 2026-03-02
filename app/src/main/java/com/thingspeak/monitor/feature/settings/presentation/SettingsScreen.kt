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
import com.thingspeak.monitor.core.datastore.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Preference
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme Setting",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ThemePreference.entries.forEach { preference ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.targetTheme == preference,
                            onClick = { viewModel.setThemePreference(preference) }
                        )
                        Text(
                            text = preference.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Merge Chart Fields",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Display all fields on a single chart",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isChartMergingEnabled,
                        onCheckedChange = { viewModel.setChartMerging(it) }
                    )
                }
            }

            HorizontalDivider()

            // Refresh Interval
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Setting",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh Interval",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "High frequency (1s) may trigger rate limits. App will automatically retry for up to 15s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                val intervals = listOf(
                    1_000L to "1s",
                    15_000L to "15s",
                    30_000L to "30s",
                    60_000L to "1m",
                    300_000L to "5m"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { (ms, label) ->
                        FilterChip(
                            selected = uiState.refreshIntervalMs == ms,
                            onClick = { viewModel.setRefreshInterval(ms) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Notifications Info
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications Setting",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Alert Notifications",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Alerts are sent when channel fields cross defined thresholds. You can configure rules in Channel Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        SettingsScreen(
            onNavigateBack = {}
        )
    }
}

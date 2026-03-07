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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val context = androidx.compose.ui.platform.LocalContext.current
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
                            contentDescription = stringResource(R.string.chart_back)
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
                        contentDescription = stringResource(R.string.settings_appearance),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_appearance),
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
                            text = when (preference) {
                                ThemePreference.LIGHT -> stringResource(R.string.theme_light)
                                ThemePreference.DARK -> stringResource(R.string.theme_dark)
                                ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
                            },
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
                            text = stringResource(R.string.settings_merge_charts),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_merge_charts_desc),
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
                        contentDescription = stringResource(R.string.settings_refresh_interval),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_refresh_interval),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_refresh_warning),
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

            // Background Sync Interval
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = stringResource(R.string.settings_sync_interval),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_sync_interval),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_sync_interval_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                val syncIntervals = listOf(
                    15L to stringResource(R.string.settings_sync_interval_15m),
                    30L to stringResource(R.string.settings_sync_interval_30m),
                    60L to stringResource(R.string.settings_sync_interval_1h)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    syncIntervals.forEach { (minutes, label) ->
                        FilterChip(
                            selected = uiState.syncIntervalMinutes == minutes,
                            onClick = { viewModel.setSyncInterval(context, minutes) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // High Frequency Monitoring (Foreground Service)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.settings_high_freq),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_high_freq),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Switch(
                        checked = uiState.isHighFrequencyEnabled,
                        onCheckedChange = { viewModel.setHighFrequencyEnabled(context, it) }
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_high_freq_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error // Highlight potential battery drain
                )
                
                if (uiState.isHighFrequencyEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val highFreqIntervals = listOf(
                        1L to stringResource(R.string.settings_high_freq_1m),
                        5L to stringResource(R.string.settings_high_freq_5m),
                        10L to stringResource(R.string.settings_high_freq_10m)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        highFreqIntervals.forEach { (minutes, label) ->
                            FilterChip(
                                selected = uiState.highFrequencyIntervalMinutes == minutes,
                                onClick = { viewModel.setHighFrequencyInterval(context, minutes) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Notifications Info
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.settings_alert_notifications),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_alert_notifications),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_alert_notifications_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
                var isIgnoringBatterOptimizations by remember {
                    mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
                }

                // Check again when the screen is resumed
                LifecycleResumeEffect(Unit) {
                    isIgnoringBatterOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    onPauseOrDispose {}
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_battery_optimization),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.settings_battery_optimization_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            },
                            enabled = !isIgnoringBatterOptimizations,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isIgnoringBatterOptimizations) "Already Opt-out" 
                                else stringResource(R.string.settings_battery_optimization_button)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Manual Sync / Check Alerts Now
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sync_now),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_now_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                com.thingspeak.monitor.core.worker.DataSyncWorker.runOnce(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_sync_now))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Reset Alerts
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Reset Alerts",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Clears all alert states. Active violations will re-trigger on the next sync cycle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.resetAlerts() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Alerts")
                        }
                    }
                }
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

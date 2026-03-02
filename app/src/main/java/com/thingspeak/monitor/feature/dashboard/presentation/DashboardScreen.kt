package com.thingspeak.monitor.feature.dashboard.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingspeak.monitor.R
import com.thingspeak.monitor.feature.dashboard.presentation.components.AddChannelDialog
import com.thingspeak.monitor.feature.dashboard.presentation.components.DashboardContent
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.foundation.layout.Column
import com.thingspeak.monitor.feature.dashboard.presentation.components.AdMobBanner
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

/**
 * Main dashboard screen with ViewModel integration.
 *
 * @param onNavigateToSettings Callback to navigate to settings screen.
 * @param viewModel Hilt ViewModel for the dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToChart: (Long, String, String?) -> Unit,
    onNavigateToChannelSettings: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DashboardEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    if (showAddDialog) {
        AddChannelDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { id, name, apiKey ->
                viewModel.addChannel(id, name, apiKey)
                showAddDialog = false
            },
            onSearch = { viewModel.searchPublicChannels(it) },
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching
        )
    }

    channelToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { channelToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_channel_title)) },
            text = { Text(stringResource(R.string.dialog_delete_channel_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeChannel(id)
                        channelToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.dashboard_add_channel)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            androidx.compose.animation.AnimatedVisibility(visible = uiState.isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.widget_working_offline),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshAll,
                modifier = Modifier.weight(1f)
            ) {
                DashboardContent(
                    uiState = uiState,
                    onNavigateToChart = onNavigateToChart,
                    onNavigateToChannelSettings = onNavigateToChannelSettings,
                    onRemoveChannel = { channelToDelete = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AdMobBanner()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        DashboardScreen(
            onNavigateToSettings = {},
            onNavigateToChart = { _, _, _ -> },
            onNavigateToChannelSettings = {}
        )
    }
}

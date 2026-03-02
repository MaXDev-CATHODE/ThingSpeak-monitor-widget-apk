package com.thingspeak.monitor.feature.dashboard.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thingspeak.monitor.feature.dashboard.presentation.DashboardUiState
import com.thingspeak.monitor.presentation.components.EmptyStateComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import com.thingspeak.monitor.core.ui.shimmer

/**
 * Main dashboard content list.
 *
 * @param uiState Current dashboard UI state.
 * @param onRemoveChannel Callback to remove a channel.
 */
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onNavigateToChart: (Long, String, String?) -> Unit,
    onNavigateToChannelSettings: (Long) -> Unit,
    onRemoveChannel: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.channels.isEmpty()) {
        if (uiState.isLoading) {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(5) {
                    ShimmerChannelCard()
                }
            }
        } else {
            EmptyStateComponent(modifier = modifier)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(
                items = uiState.channels,
                key = { it.id }
            ) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onNavigateToChart(it.id, it.name, it.apiKey) },
                    onRemoveClick = onRemoveChannel,
                    onSettingsClick = onNavigateToChannelSettings,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
fun ShimmerChannelCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
            }
        }
    }
}

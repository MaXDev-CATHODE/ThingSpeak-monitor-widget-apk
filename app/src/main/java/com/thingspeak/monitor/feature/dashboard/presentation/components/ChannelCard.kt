package com.thingspeak.monitor.feature.dashboard.presentation.components

import androidx.compose.animation.animateContentSize
import com.thingspeak.monitor.core.ui.shimmer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thingspeak.monitor.R
import com.thingspeak.monitor.core.datastore.ChannelPreferences

/**
 * Premium channel card item.
 *
 * @param channel The channel data to display.
 * @param onRemoveClick Callback when remove button is clicked.
 */
@Composable
fun ChannelCard(
    channel: ChannelPreferences.SavedChannel,
    onClick: (ChannelPreferences.SavedChannel) -> Unit,
    onRemoveClick: (Long) -> Unit,
    onSettingsClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val statusColor = when (channel.lastSyncStatus) {
        "SUCCESS" -> com.thingspeak.monitor.core.designsystem.theme.TsStatusSuccess
        "ERROR_API" -> com.thingspeak.monitor.core.designsystem.theme.TsStatusError
        "ERROR_AUTH" -> MaterialTheme.colorScheme.tertiary // Distinguish auth error
        "ERROR_NETWORK" -> com.thingspeak.monitor.core.designsystem.theme.TsStatusPending
        "NONE" -> MaterialTheme.colorScheme.outline
        else -> com.thingspeak.monitor.core.designsystem.theme.TsStatusPending
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { 
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick(channel) 
            }
            .padding(vertical = 4.dp)
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status bar indicator
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${channel.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = { onSettingsClick(channel.id) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { onRemoveClick(channel.id) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.dialog_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ChannelCardPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        ChannelCard(
            channel = ChannelPreferences.SavedChannel(
                id = 12345,
                name = "Test Channel",
                apiKey = "API_KEY"
            ),
            onClick = {},
            onRemoveClick = {},
            onSettingsClick = {}
        )
    }
}

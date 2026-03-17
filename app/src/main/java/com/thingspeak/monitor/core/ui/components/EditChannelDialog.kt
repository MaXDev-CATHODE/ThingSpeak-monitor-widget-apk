package com.thingspeak.monitor.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thingspeak.monitor.R
import com.thingspeak.monitor.core.datastore.ChannelPreferences

/**
 * Dialog for editing an existing ThingSpeak channel.
 * Location: core.ui.components to avoid feature isolation issues.
 */
@Composable
fun EditChannelDialog(
    channel: ChannelPreferences.SavedChannel,
    onDismiss: () -> Unit,
    onConfirm: (id: Long, name: String, apiKey: String?) -> Unit
) {
    val channelId = channel.id.toString()
    var channelName by remember { mutableStateOf(channel.name) }
    var apiKey by remember { mutableStateOf(channel.apiKey ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_edit_channel_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = channelId,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.dialog_channel_id)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text(stringResource(R.string.dialog_channel_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.dialog_api_key)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(channel.id, channelName, apiKey.ifBlank { null })
                }
            ) {
                Text(stringResource(R.string.alert_save_channel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

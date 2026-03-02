package com.thingspeak.monitor.feature.dashboard.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import com.thingspeak.monitor.R

/**
 * Dialog for adding a new ThingSpeak channel.
 *
 * @param onDismiss Callback to dismiss the dialog.
 * @param onConfirm Callback when "Add" is clicked with channel details.
 */
@Composable
fun AddChannelDialog(
    onDismiss: () -> Unit,
    onConfirm: (id: Long, name: String, apiKey: String?) -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<com.thingspeak.monitor.feature.channel.domain.model.Channel>,
    isSearching: Boolean
) {
    var selectedTab by remember { mutableStateOf(0) }
    var channelId by remember { mutableStateOf("") }
    var channelName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.TabRow(selectedTabIndex = selectedTab) {
                androidx.compose.material3.Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.dialog_add_manual)) }
                )
                androidx.compose.material3.Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.dialog_search_public)) }
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { 
                            channelId = it
                            isError = false 
                        },
                        label = { Text(stringResource(R.string.dialog_channel_id)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isError,
                        supportingText = if (isError) {
                            { Text(stringResource(R.string.error_invalid_channel)) }
                        } else null
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
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            onSearch(it)
                        },
                        label = { Text(stringResource(R.string.dialog_search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (isSearching) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(searchResults) { channel ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(channel.name) },
                                supportingContent = { Text("ID: ${channel.id}") },
                                modifier = Modifier.clickable {
                                    channelId = channel.id.toString()
                                    channelName = channel.name
                                    selectedTab = 0
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = {
                        val id = channelId.toLongOrNull()
                        if (id != null) {
                            onConfirm(id, channelName, apiKey.ifBlank { null })
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.dialog_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

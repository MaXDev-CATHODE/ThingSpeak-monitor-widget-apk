package com.thingspeak.monitor.presentation.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * A headless Composable that asks for POST_NOTIFICATIONS permission
 * on Android 13+ (Tiramisu) devices if not already granted.
 */
@Composable
fun NotificationPermissionTarget() {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Optionally handle the result, e.g., show a snackbar if denied
        }

        LaunchedEffect(Unit) {
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

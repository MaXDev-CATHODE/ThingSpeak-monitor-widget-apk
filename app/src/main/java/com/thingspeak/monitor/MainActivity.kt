package com.thingspeak.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Added import for Color
import androidx.compose.ui.res.stringResource
import com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme
import com.thingspeak.monitor.presentation.navigation.NavGraph
import com.thingspeak.monitor.presentation.components.NotificationPermissionTarget
import com.thingspeak.monitor.core.datastore.AppPreferences
import com.thingspeak.monitor.core.datastore.ThemePreference
import javax.inject.Inject
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main application activity — UI entry point.
 *
 * [@AndroidEntryPoint] enables Hilt dependency injection for this activity
 * and its Composable children.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // This line was already present and is kept.
        setContent {
            val themePreference by appPreferences.observeThemePreference()
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            
            val isDarkTheme = when (themePreference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            ThingSpeakMonitorTheme(darkTheme = isDarkTheme) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    NotificationPermissionTarget()
                    NavGraph()
                }
            }
        }
    }
}

package com.thingspeak.monitor.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thingspeak.monitor.feature.dashboard.presentation.DashboardScreen
import com.thingspeak.monitor.feature.settings.presentation.SettingsScreen
import com.thingspeak.monitor.feature.chart.presentation.ChartScreen
import com.thingspeak.monitor.feature.settings.presentation.ChannelSettingsScreen
import androidx.navigation.toRoute

/**
 * Main application navigation graph.
 * Configures [NavHost] with Type-Safe routes.
 */
@Composable
fun NavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(400)) },
        exitTransition = { fadeOut(animationSpec = tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(400)) },
        popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(400)) },
        popExitTransition = { fadeOut(animationSpec = tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(400)) }
    ) {
        composable<Screen.Dashboard> {
            DashboardScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                onNavigateToChart = { id, name, apiKey -> 
                    navController.navigate(Screen.Chart(id, name, apiKey)) 
                },
                onNavigateToChannelSettings = { id ->
                    navController.navigate(Screen.ChannelSettings(id))
                }
            )
        }
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<Screen.Chart> {
            ChartScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<Screen.ChannelSettings> { backStackEntry ->
            val route: Screen.ChannelSettings = backStackEntry.toRoute()
            ChannelSettingsScreen(
                channelId = route.channelId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAlertRules = { id -> navController.navigate(Screen.AlertRules(id)) }
            )
        }
        composable<Screen.AlertRules> { backStackEntry ->
            val route: Screen.AlertRules = backStackEntry.toRoute()
            com.thingspeak.monitor.feature.settings.presentation.AlertRulesScreen(
                channelId = route.channelId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

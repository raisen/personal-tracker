package com.personaltracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personaltracker.data.AuthManager
import com.personaltracker.ui.screens.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Entries : Screen("entries", "Entries", Icons.Default.List)
    data object Insights : Screen("insights", "Insights", Icons.Default.Lightbulb)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(Screen.Entries, Screen.Insights, Screen.Settings)

@Composable
fun PersonalTrackerApp() {
    var isAuthenticated by remember { mutableStateOf(AuthManager.isAuthenticated()) }

    if (!isAuthenticated) {
        SetupScreen(onComplete = { isAuthenticated = true })
        return
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Entries.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Entries.route) {
                EntriesScreen()
            }
            composable(Screen.Insights.route) {
                InsightsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onDisconnect = {
                    isAuthenticated = false
                })
            }
        }
    }
}

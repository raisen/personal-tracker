package com.personaltracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
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
    data object Entry : Screen("entry", "Entry", Icons.Default.Add)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Insights : Screen("insights", "Insights", Icons.Default.Lightbulb)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavScreens = listOf(Screen.Entry, Screen.History, Screen.Insights, Screen.Settings)

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
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
            startDestination = Screen.Entry.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Entry.route) {
                EntryScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(onEditEntry = { entry ->
                    // Navigate to entry screen - in a full implementation,
                    // we'd pass the entry data. For now, navigate to entry tab.
                    navController.navigate(Screen.Entry.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                })
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

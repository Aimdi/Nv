package com.nai.promptcompanion.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nai.promptcompanion.ui.builder.BuilderScreen
import com.nai.promptcompanion.ui.library.LibraryScreen
import com.nai.promptcompanion.ui.tags.TagsScreen

private data class TopLevelRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_LEVEL = listOf(
    TopLevelRoute("builder", "Builder", Icons.Default.Tune),
    TopLevelRoute("tags", "Tags", Icons.Default.PhotoLibrary),
    TopLevelRoute("library", "Library", Icons.Default.Bookmarks),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                TOP_LEVEL.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "builder",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("builder") {
                BuilderScreen(snackbarHostState = snackbarHostState)
            }
            composable("tags") {
                TagsScreen(snackbarHostState = snackbarHostState)
            }
            composable("library") {
                LibraryScreen(
                    snackbarHostState = snackbarHostState,
                    onGoToBuilder = {
                        navController.navigate("builder") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

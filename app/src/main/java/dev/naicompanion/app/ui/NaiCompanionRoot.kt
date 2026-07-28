package dev.naicompanion.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.naicompanion.app.di.AppContainer
import dev.naicompanion.app.ui.browser.BrowserScreen
import dev.naicompanion.app.ui.browser.BrowserViewModel
import dev.naicompanion.app.ui.builder.BuilderScreen
import dev.naicompanion.app.ui.builder.BuilderViewModel
import dev.naicompanion.app.ui.library.LibraryScreen
import dev.naicompanion.app.ui.library.LibraryViewModel
import dev.naicompanion.app.ui.packs.PacksScreen
import dev.naicompanion.app.ui.packs.PacksViewModel
import dev.naicompanion.app.ui.settings.SettingsScreen
import dev.naicompanion.app.ui.settings.SettingsViewModel

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BUILDER("builder", "Builder", Icons.Default.Tune),
    LIBRARY("library", "Library", Icons.Default.Bookmarks),
    BROWSER("browser", "Browse", Icons.Default.GridView),
}

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PACKS = "packs"

@Composable
fun NaiCompanionRoot(
    container: AppContainer,
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val builderViewModel: BuilderViewModel =
        viewModel(factory = BuilderViewModel.factory(container))

    // Text arriving from the share sheet always lands in the builder.
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            builderViewModel.appendFromText(sharedText)
            onSharedTextHandled()
            navController.navigate(TopLevelDestination.BUILDER.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        // Hosted once at the root: each screen has its own Scaffold, and a snackbar raised from a
        // screen whose Scaffold has no host would simply never be shown.
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.BUILDER.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(TopLevelDestination.BUILDER.route) {
                BuilderScreen(
                    viewModel = builderViewModel,
                    snackbarHostState = snackbarHostState,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
            }

            composable(TopLevelDestination.LIBRARY.route) {
                val libraryViewModel: LibraryViewModel =
                    viewModel(factory = LibraryViewModel.factory(container))
                LibraryScreen(
                    viewModel = libraryViewModel,
                    snackbarHostState = snackbarHostState,
                    onLoadCombo = { comboId ->
                        builderViewModel.loadCombo(comboId)
                        navController.navigate(TopLevelDestination.BUILDER.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onLoadPromptText = { text ->
                        builderViewModel.loadFromText(text)
                        navController.navigate(TopLevelDestination.BUILDER.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(TopLevelDestination.BROWSER.route) {
                val browserViewModel: BrowserViewModel =
                    viewModel(factory = BrowserViewModel.factory(container))
                BrowserScreen(
                    viewModel = browserViewModel,
                    snackbarHostState = snackbarHostState,
                    onAddTag = { name, kind -> builderViewModel.addTag(name, kind) },
                    onOpenPacks = { navController.navigate(ROUTE_PACKS) },
                )
            }

            composable(ROUTE_SETTINGS) {
                val settingsViewModel: SettingsViewModel =
                    viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(
                    viewModel = settingsViewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                    onOpenPacks = { navController.navigate(ROUTE_PACKS) },
                )
            }

            composable(ROUTE_PACKS) {
                val packsViewModel: PacksViewModel =
                    viewModel(factory = PacksViewModel.factory(container))
                PacksScreen(
                    viewModel = packsViewModel,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

package com.naicompanion.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naicompanion.NaiCompanionApp
import com.naicompanion.ui.builder.BuilderScreen
import com.naicompanion.ui.builder.BuilderViewModel
import com.naicompanion.ui.library.LibraryScreen
import com.naicompanion.ui.tags.TagsScreen

object Destinations {
    const val BUILDER = "builder"
    const val LIBRARY = "library"
    const val TAGS = "tags"
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as NaiCompanionApp
    val repository = app.container.repository

    // The builder state is shared across tabs: the tag browser and library
    // both feed tags into the combo under construction.
    val builderViewModel: BuilderViewModel = viewModel(
        factory = BuilderViewModel.factory(repository),
    )

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        repository.ensureCatalogSeeded()
    }

    val tabs = listOf(
        Triple(Destinations.BUILDER, "Builder", Icons.Filled.AutoAwesome),
        Triple(Destinations.LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
        Triple(Destinations.TAGS, "Tags", Icons.Filled.Style),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.BUILDER,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destinations.BUILDER) {
                BuilderScreen(
                    viewModel = builderViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Destinations.LIBRARY) {
                LibraryScreen(
                    repository = repository,
                    builderViewModel = builderViewModel,
                    snackbarHostState = snackbarHostState,
                    onNavigateToBuilder = {
                        navController.navigate(Destinations.BUILDER) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Destinations.TAGS) {
                TagsScreen(
                    repository = repository,
                    builderViewModel = builderViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

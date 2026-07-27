package com.aimdi.nv.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aimdi.nv.ui.builder.BuilderScreen
import com.aimdi.nv.ui.builder.BuilderViewModel
import com.aimdi.nv.ui.library.LibraryScreen
import com.aimdi.nv.ui.library.LibraryViewModel
import com.aimdi.nv.ui.settings.SettingsScreen
import com.aimdi.nv.ui.settings.SettingsViewModel
import com.aimdi.nv.ui.tags.TagsScreen
import com.aimdi.nv.ui.tags.TagsViewModel

object Routes {
    const val BUILDER = "builder"
    const val LIBRARY = "library"
    const val TAGS = "tags"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val builderVm: BuilderViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.BUILDER,
        modifier = modifier,
    ) {
        composable(Routes.BUILDER) {
            BuilderScreen(
                viewModel = builderVm,
                snackbarHostState = snackbarHostState,
            )
        }
        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = vm,
                snackbarHostState = snackbarHostState,
                onLoadIntoBuilder = { text ->
                    builderVm.loadPromptText(text)
                    navController.navigate(Routes.BUILDER) {
                        launchSingleTop = true
                    }
                },
                onLoadCombo = { entries ->
                    builderVm.replaceEntries(entries)
                    navController.navigate(Routes.BUILDER) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.TAGS) {
            val vm: TagsViewModel = viewModel()
            TagsScreen(
                viewModel = vm,
                snackbarHostState = snackbarHostState,
                onAddToBuilder = { tag, asArtist ->
                    builderVm.addTag(tag, forceArtistPrefix = asArtist)
                    navController.navigate(Routes.BUILDER) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = vm,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

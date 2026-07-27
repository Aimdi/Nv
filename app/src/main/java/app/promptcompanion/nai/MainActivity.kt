package app.promptcompanion.nai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.promptcompanion.nai.ui.browser.BrowserScreen
import app.promptcompanion.nai.ui.builder.BuilderScreen
import app.promptcompanion.nai.ui.library.LibraryScreen
import app.promptcompanion.nai.ui.navigation.AppDestination
import app.promptcompanion.nai.ui.settings.SettingsScreen
import app.promptcompanion.nai.ui.theme.PromptCompanionTheme
import app.promptcompanion.nai.viewmodel.AppViewModel
import app.promptcompanion.nai.viewmodel.UiEvent
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)

        setContent {
            PromptCompanionTheme {
                val vm: AppViewModel = viewModel()
                val builder by vm.builder.collectAsStateWithLifecycle()
                val library by vm.library.collectAsStateWithLifecycle()
                val browser by vm.browser.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: AppDestination.Builder.route

                LaunchedEffect(sharedText) {
                    if (!sharedText.isNullOrBlank()) {
                        vm.loadPromptBody(sharedText)
                    }
                }

                LaunchedEffect(Unit) {
                    vm.events.collectLatest { event ->
                        when (event) {
                            is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.destination.route,
                                    onClick = {
                                        navController.navigate(item.destination.route) {
                                            popUpTo(AppDestination.Builder.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.destination.label) },
                                    label = { Text(item.destination.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Builder.route,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(AppDestination.Builder.route) {
                            BuilderScreen(
                                state = builder,
                                onDraftChange = vm::setDraftTag,
                                onAddTag = vm::addDraftTag,
                                onSelect = vm::selectEntry,
                                onUpdate = vm::updateEntry,
                                onRemove = vm::removeEntry,
                                onMove = vm::moveEntry,
                                onClear = vm::clearBuilder,
                                onCopy = { vm.copyRendered(false) },
                                onCopyAndOpen = { vm.copyRendered(true) },
                                onShare = vm::shareRendered,
                                onSavePrompt = vm::saveCurrentAsPrompt,
                                onSaveCombo = vm::saveCurrentAsCombo,
                            )
                        }
                        composable(AppDestination.Library.route) {
                            LibraryScreen(
                                state = library,
                                onQueryChange = vm::setLibraryQuery,
                                onLoadPrompt = {
                                    vm.loadPromptBody(it.body)
                                    navController.navigate(AppDestination.Builder.route)
                                },
                                onLoadCombo = {
                                    vm.loadCombo(it)
                                    navController.navigate(AppDestination.Builder.route)
                                },
                                onDeletePrompt = vm::deletePrompt,
                                onDeleteCombo = vm::deleteCombo,
                                onTogglePromptFavorite = vm::togglePromptFavorite,
                                onToggleComboFavorite = vm::toggleComboFavorite,
                                onExport = vm::exportToUri,
                                onImport = vm::importFromUri,
                                onCopyText = { text ->
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
                                },
                            )
                        }
                        composable(AppDestination.Browser.route) {
                            BrowserScreen(
                                state = browser,
                                thumbnailResolver = vm::thumbnailFile,
                                onQueryChange = vm::setBrowserQuery,
                                onFavoritesOnly = vm::setFavoritesOnly,
                                onSort = vm::setSort,
                                onSelect = vm::selectArtist,
                                onToggleFavorite = vm::toggleArtistFavorite,
                                onAddToBuilder = { artist, weighted ->
                                    vm.addArtistToBuilder(artist, weighted)
                                },
                            )
                        }
                        composable(AppDestination.Settings.route) {
                            SettingsScreen(
                                state = settings,
                                onPackUrlChange = vm::setPackUrl,
                                onDownload = vm::downloadPack,
                                onRemove = vm::removePack,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class NavItem(val destination: AppDestination, val icon: ImageVector)

private val navItems = listOf(
    NavItem(AppDestination.Builder, Icons.Default.Edit),
    NavItem(AppDestination.Library, Icons.Default.LibraryBooks),
    NavItem(AppDestination.Browser, Icons.Default.Brush),
    NavItem(AppDestination.Settings, Icons.Default.Settings),
)

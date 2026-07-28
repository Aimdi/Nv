package dev.naicompanion.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.naicompanion.app.core.prompt.Combo
import dev.naicompanion.app.data.user.PromptEntity
import dev.naicompanion.app.ui.common.ConfirmDialog
import dev.naicompanion.app.ui.common.EmptyState
import dev.naicompanion.app.ui.theme.PromptPreviewTextStyle
import dev.naicompanion.app.ui.util.ClipboardBridge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    onLoadCombo: (Long) -> Unit,
    onLoadPromptText: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingPromptDelete by remember { mutableStateOf<PromptEntity?>(null) }
    var pendingComboDelete by remember { mutableStateOf<Combo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    fun copy(text: String) {
        if (ClipboardBridge.copy(context, text)) {
            scope.launch { snackbarHostState.showSnackbar("Copied") }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                LibraryTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { viewModel.setTab(entry) },
                        text = { Text(entry.label) },
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search saved items") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = viewModel::toggleFavoritesOnly,
                    label = { Text("Favorites") },
                )
                if (tab == LibraryTab.PROMPTS) {
                    state.folders.forEach { folder ->
                        FilterChip(
                            selected = state.folderFilter == folder,
                            onClick = {
                                viewModel.setFolderFilter(
                                    if (state.folderFilter == folder) null else folder,
                                )
                            },
                            label = { Text(folder) },
                        )
                    }
                }
            }

            when (tab) {
                LibraryTab.PROMPTS -> {
                    val prompts = state.visiblePrompts
                    if (prompts.isEmpty()) {
                        EmptyState(
                            title = "No saved prompts",
                            message = "Build a prompt, then use \"Save rendered text to library\" " +
                                "to keep it here.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(prompts, key = { it.id }) { prompt ->
                                PromptCard(
                                    prompt = prompt,
                                    onCopy = { copy(prompt.body) },
                                    onLoad = { onLoadPromptText(prompt.body) },
                                    onToggleFavorite = { viewModel.togglePromptFavorite(prompt) },
                                    onDelete = { pendingPromptDelete = prompt },
                                )
                            }
                        }
                    }
                }

                LibraryTab.COMBOS -> {
                    if (state.combos.isEmpty()) {
                        EmptyState(
                            title = "No saved combos",
                            message = "Combos keep your tags editable, with their weights and " +
                                "order intact. Save one from the builder.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.combos, key = { it.id }) { combo ->
                                val rendered = viewModel.renderCombo(combo, settings)
                                ComboCard(
                                    combo = combo,
                                    rendered = rendered,
                                    onCopy = { copy(rendered) },
                                    onLoad = { onLoadCombo(combo.id) },
                                    onToggleFavorite = { viewModel.toggleComboFavorite(combo) },
                                    onDelete = { pendingComboDelete = combo },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingPromptDelete?.let { prompt ->
        ConfirmDialog(
            title = "Delete prompt",
            message = "\"${prompt.title}\" will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { pendingPromptDelete = null },
            onConfirm = {
                viewModel.deletePrompt(prompt)
                pendingPromptDelete = null
            },
        )
    }

    pendingComboDelete?.let { combo ->
        ConfirmDialog(
            title = "Delete combo",
            message = "\"${combo.name}\" will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { pendingComboDelete = null },
            onConfirm = {
                viewModel.deleteCombo(combo)
                pendingComboDelete = null
            },
        )
    }
}

@Composable
private fun PromptCard(
    prompt: PromptEntity,
    onCopy: () -> Unit,
    onLoad: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (prompt.isFavorite) {
                            Icons.Default.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = "Toggle favorite",
                        tint = if (prompt.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                text = prompt.body,
                style = PromptPreviewTextStyle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CardActions(onCopy = onCopy, onLoad = onLoad, onDelete = onDelete)
        }
    }
}

@Composable
private fun ComboCard(
    combo: Combo,
    rendered: String,
    onCopy: () -> Unit,
    onLoad: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLoad),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = combo.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (combo.isFavorite) {
                            Icons.Default.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = "Toggle favorite",
                        tint = if (combo.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                text = "${combo.entries.size} tags",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rendered,
                style = PromptPreviewTextStyle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            CardActions(onCopy = onCopy, onLoad = onLoad, onDelete = onDelete)
        }
    }
}

@Composable
private fun CardActions(onCopy: () -> Unit, onLoad: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
        }
        IconButton(onClick = onLoad) {
            Icon(Icons.Default.Edit, contentDescription = "Load into builder")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

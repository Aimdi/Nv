package com.naicompanion.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naicompanion.data.AppRepository
import com.naicompanion.data.db.ComboEntity
import com.naicompanion.data.db.PromptEntity
import com.naicompanion.render.PromptRenderer
import com.naicompanion.ui.builder.BuilderViewModel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repository: AppRepository,
    builderViewModel: BuilderViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToBuilder: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(repository))

    val tab by viewModel.tab.collectAsState()
    val query by viewModel.query.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val prompts by viewModel.prompts.collectAsState()
    val combos by viewModel.combos.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var promptEditorTarget by remember { mutableStateOf<PromptEntity?>(null) }
    var promptEditorOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = viewModel.exportBackup()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()
                    ?.use { it.write(text) } ?: error("Could not open file")
            }.onSuccess {
                snackbarHostState.showSnackbar("Backup exported")
            }.onFailure {
                snackbarHostState.showSnackbar("Export failed: ${it.message}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    ?.use { it.readText() } ?: error("Could not read file")
                viewModel.importBackup(text)
            }.onSuccess { result ->
                snackbarHostState.showSnackbar(result.summary())
            }.onFailure {
                snackbarHostState.showSnackbar("Import failed: ${it.message}")
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (tab == LibraryViewModel.Tab.PROMPTS) {
                FloatingActionButton(onClick = {
                    promptEditorTarget = null
                    promptEditorOpen = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New prompt")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    label = { Text("Search library") },
                    singleLine = true,
                )
                IconButton(onClick = { viewModel.setFavoritesOnly(!favoritesOnly) }) {
                    Icon(
                        if (favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Favorites only",
                        tint = if (favoritesOnly)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Import / export")
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export as JSON") },
                            leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = {
                                overflowOpen = false
                                exportLauncher.launch("nai-companion-backup.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import from JSON") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                overflowOpen = false
                                // Broad mime so file pickers on all devices show .json files.
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == LibraryViewModel.Tab.PROMPTS,
                    onClick = { viewModel.setTab(LibraryViewModel.Tab.PROMPTS) },
                    text = { Text("Prompts (${prompts.size})") },
                )
                Tab(
                    selected = tab == LibraryViewModel.Tab.COMBOS,
                    onClick = { viewModel.setTab(LibraryViewModel.Tab.COMBOS) },
                    text = { Text("Combos (${combos.size})") },
                )
            }

            Spacer(Modifier.height(8.dp))

            when (tab) {
                LibraryViewModel.Tab.PROMPTS -> PromptList(
                    prompts = prompts,
                    onCopy = { prompt ->
                        clipboard.setText(AnnotatedString(prompt.body))
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                    },
                    onEdit = {
                        promptEditorTarget = it
                        promptEditorOpen = true
                    },
                    onToggleFavorite = viewModel::togglePromptFavorite,
                    onDelete = { viewModel.deletePrompt(it.id) },
                )
                LibraryViewModel.Tab.COMBOS -> ComboList(
                    combos = combos,
                    renderCombo = { combo ->
                        PromptRenderer.renderCombo(viewModel.decodeCombo(combo))
                    },
                    onLoadIntoBuilder = { combo ->
                        builderViewModel.loadCombo(combo)
                        onNavigateToBuilder()
                        scope.launch {
                            snackbarHostState.showSnackbar("Loaded \"${combo.title}\" into builder")
                        }
                    },
                    onCopy = { combo ->
                        val text = PromptRenderer.renderCombo(viewModel.decodeCombo(combo))
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                    },
                    onToggleFavorite = viewModel::toggleComboFavorite,
                    onDelete = { viewModel.deleteCombo(it.id) },
                )
            }
        }
    }

    if (promptEditorOpen) {
        PromptEditorDialog(
            existing = promptEditorTarget,
            onDismiss = { promptEditorOpen = false },
            onSave = { title, body ->
                viewModel.savePrompt(promptEditorTarget?.id, title, body)
                promptEditorOpen = false
            },
        )
    }
}

@Composable
private fun PromptList(
    prompts: List<PromptEntity>,
    onCopy: (PromptEntity) -> Unit,
    onEdit: (PromptEntity) -> Unit,
    onToggleFavorite: (PromptEntity) -> Unit,
    onDelete: (PromptEntity) -> Unit,
) {
    if (prompts.isEmpty()) {
        EmptyHint("No prompts yet. Save one from the Builder tab or create one with +.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(prompts, key = { it.id }) { prompt ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            prompt.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onToggleFavorite(prompt) }) {
                            Icon(
                                if (prompt.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (prompt.isFavorite)
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        prompt.body,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { onCopy(prompt) }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        TextButton(onClick = { onEdit(prompt) }) {
                            Icon(Icons.Filled.Edit, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onDelete(prompt) }) {
                            Icon(Icons.Filled.Delete, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboList(
    combos: List<ComboEntity>,
    renderCombo: (ComboEntity) -> String,
    onLoadIntoBuilder: (ComboEntity) -> Unit,
    onCopy: (ComboEntity) -> Unit,
    onToggleFavorite: (ComboEntity) -> Unit,
    onDelete: (ComboEntity) -> Unit,
) {
    if (combos.isEmpty()) {
        EmptyHint("No combos yet. Build one in the Builder tab and choose \"Save as combo\".")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(combos, key = { it.id }) { combo ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            combo.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onToggleFavorite(combo) }) {
                            Icon(
                                if (combo.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (combo.isFavorite)
                                    MaterialTheme.colorScheme.secondary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        renderCombo(combo),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { onLoadIntoBuilder(combo) }) {
                            Icon(Icons.AutoMirrored.Filled.Input, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }
                        TextButton(onClick = { onCopy(combo) }) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onDelete(combo) }) {
                            Icon(Icons.Filled.Delete, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptEditorDialog(
    existing: PromptEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New prompt" else "Edit prompt") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Prompt text") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
                enabled = title.isNotBlank() && body.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

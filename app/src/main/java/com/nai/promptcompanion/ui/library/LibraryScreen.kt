package com.nai.promptcompanion.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nai.promptcompanion.data.user.ComboEntity
import com.nai.promptcompanion.data.user.PromptEntity
import com.nai.promptcompanion.util.copyToClipboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    snackbarHostState: SnackbarHostState,
    onGoToBuilder: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prompts by viewModel.promptList.collectAsState()
    val combos by viewModel.comboList.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    var tab by rememberSaveable { mutableStateOf(0) } // 0 = prompts, 1 = combos
    var showAddDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importFrom) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.goToBuilder.collect { onGoToBuilder() }
    }

    Scaffold(
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New prompt") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search prompts") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export backup (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = {
                                menuExpanded = false
                                exportLauncher.launch("nai-prompt-backup.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Prompts (${prompts.size})") }
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Combos (${combos.size})") }
            }

            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                PromptList(
                    prompts = prompts,
                    onCopy = {
                        copyToClipboard(context, it.body, it.title)
                    },
                    onLoad = viewModel::loadPromptIntoBuilder,
                    onToggleFavorite = viewModel::togglePromptFavorite,
                    onDelete = viewModel::deletePrompt,
                )
            } else {
                ComboList(
                    combos = combos,
                    preview = viewModel::comboPreview,
                    onLoad = viewModel::loadComboIntoBuilder,
                    onToggleFavorite = viewModel::toggleComboFavorite,
                    onDelete = viewModel::deleteCombo,
                )
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New prompt") },
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
                        label = { Text("Prompt text (NovelAI syntax)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addPrompt(title, body)
                    showAddDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PromptList(
    prompts: List<PromptEntity>,
    onCopy: (PromptEntity) -> Unit,
    onLoad: (PromptEntity) -> Unit,
    onToggleFavorite: (PromptEntity) -> Unit,
    onDelete: (PromptEntity) -> Unit,
) {
    if (prompts.isEmpty()) {
        EmptyHint("No prompts yet. Save prompts here, or load anything into the builder.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(prompts, key = { it.id }) { prompt ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(prompt.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatTimestamp(prompt.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(prompt) }) {
                            Icon(
                                if (prompt.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (prompt.favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        prompt.body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onCopy(prompt) }) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Copy")
                        }
                        TextButton(onClick = { onLoad(prompt) }) {
                            Icon(Icons.Default.Input, null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Load into builder")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onDelete(prompt) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
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
    preview: (ComboEntity) -> String,
    onLoad: (ComboEntity) -> Unit,
    onToggleFavorite: (ComboEntity) -> Unit,
    onDelete: (ComboEntity) -> Unit,
) {
    if (combos.isEmpty()) {
        EmptyHint("No saved combos. Build one in the Builder tab and tap \"Save combo\".")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(combos, key = { it.id }) { combo ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            combo.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onToggleFavorite(combo) }) {
                            Icon(
                                if (combo.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (combo.favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        preview(combo),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onLoad(combo) }) {
                            Icon(Icons.Default.Input, null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Load into builder")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onDelete(combo) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
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

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

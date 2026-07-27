package com.naicompanion.ui.builder

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.naicompanion.data.model.TagEntry
import com.naicompanion.render.PromptRenderer
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val NOVELAI_IMAGE_URL = "https://novelai.net/image"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val entries by viewModel.entries.collectAsState()
    val rendered by viewModel.rendered.collectAsState()
    val query by viewModel.query.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val editing by viewModel.editing.collectAsState()

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var saveComboDialog by remember { mutableStateOf(false) }
    var savePromptDialog by remember { mutableStateOf(false) }

    fun copyToClipboard(message: String) {
        if (rendered.isBlank()) return
        clipboard.setText(AnnotatedString(rendered))
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        AddTagField(
            query = query,
            suggestions = suggestions.map { it.artist.displayName to it.artist.name },
            onQueryChange = viewModel::onQueryChange,
            onSubmit = { viewModel.addTag(query) },
            onSuggestionClick = { name ->
                viewModel.addArtist(
                    suggestions.first { it.artist.name == name }.artist
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        PreviewCard(
            rendered = rendered,
            onCopy = { copyToClipboard("Copied to clipboard") },
            onCopyAndOpen = {
                if (rendered.isBlank()) return@PreviewCard
                clipboard.setText(AnnotatedString(rendered))
                scope.launch { snackbarHostState.showSnackbar("Copied — opening NovelAI") }
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NOVELAI_IMAGE_URL)))
                }
            },
            onMenuClick = { menuExpanded = true },
            menuContent = {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Save as combo") },
                        onClick = { menuExpanded = false; saveComboDialog = true },
                        enabled = entries.isNotEmpty(),
                    )
                    DropdownMenuItem(
                        text = { Text("Save as prompt") },
                        onClick = { menuExpanded = false; savePromptDialog = true },
                        enabled = rendered.isNotBlank(),
                    )
                    DropdownMenuItem(
                        text = { Text("Clear all") },
                        onClick = { menuExpanded = false; viewModel.clear() },
                        enabled = entries.isNotEmpty(),
                    )
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Add tags above, from the Tags tab, or load a combo from the Library.\n" +
                        "Earlier tags have higher priority in NovelAI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            EntryList(
                entries = entries,
                onMove = viewModel::move,
                onEntryClick = viewModel::startEditing,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    editing?.let { entry ->
        EntryEditSheet(
            entry = entry,
            isFirst = entries.firstOrNull()?.id == entry.id,
            isLast = entries.lastOrNull()?.id == entry.id,
            onBracketChange = { viewModel.setBracketCount(entry.id, it) },
            onNumericWeightChange = { viewModel.setNumericWeight(entry.id, it) },
            onToggleEnabled = { viewModel.toggleEnabled(entry.id) },
            onMove = { delta -> viewModel.moveBy(entry.id, delta) },
            onDelete = { viewModel.remove(entry.id) },
            onDismiss = viewModel::stopEditing,
        )
    }

    if (saveComboDialog) {
        SaveDialog(
            title = "Save combo",
            onDismiss = { saveComboDialog = false },
            onConfirm = { name ->
                viewModel.saveCombo(name) {
                    scope.launch { snackbarHostState.showSnackbar("Combo saved to library") }
                }
                saveComboDialog = false
            },
        )
    }

    if (savePromptDialog) {
        SaveDialog(
            title = "Save as prompt",
            onDismiss = { savePromptDialog = false },
            onConfirm = { name ->
                viewModel.saveAsPrompt(name) {
                    scope.launch { snackbarHostState.showSnackbar("Prompt saved to library") }
                }
                savePromptDialog = false
            },
        )
    }
}

@Composable
private fun AddTagField(
    query: String,
    suggestions: List<Pair<String, String>>, // displayName to raw name
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionClick: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            label = { Text("Add tag") },
            placeholder = { Text("e.g. 1girl, mizuki_hitoshi…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onSubmit) {
                        Icon(Icons.Filled.Add, contentDescription = "Add tag")
                    }
                }
            },
        )
        DropdownMenu(
            expanded = focused && suggestions.isNotEmpty(),
            onDismissRequest = { },
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { (display, raw) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSuggestionClick(raw) },
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    rendered: String,
    onCopy: () -> Unit,
    onCopyAndOpen: () -> Unit,
    onMenuClick: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Prompt preview",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                    }
                    menuContent()
                }
            }
            SelectionContainer {
                Text(
                    text = rendered.ifBlank { "Your prompt appears here as you build it." },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (rendered.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onCopy,
                    enabled = rendered.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                FilledTonalButton(
                    onClick = onCopyAndOpen,
                    enabled = rendered.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy & open NAI")
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    entries: List<TagEntry>,
    onMove: (Int, Int) -> Unit,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = entries.indexOfFirst { it.id == from.key }
        val toIndex = entries.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) onMove(fromIndex, toIndex)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            ReorderableItem(reorderableState, key = entry.id) { _ ->
                EntryRow(
                    entry = entry,
                    dragHandle = {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.draggableHandle(),
                        )
                    },
                    onClick = { onEntryClick(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: TagEntry,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            dragHandle()
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = PromptRenderer.renderTag(entry.tag),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PromptRenderer.renderEntry(entry)?.let { fragment ->
                    if (fragment != PromptRenderer.renderTag(entry.tag)) {
                        Text(
                            text = fragment,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            entry.numericWeight?.let { w ->
                Badge { Text("×${PromptRenderer.formatWeight(w)}") }
            } ?: run {
                if (entry.bracketCount != 0) {
                    val label = if (entry.bracketCount > 0)
                        "{".repeat(entry.bracketCount)
                    else
                        "[".repeat(-entry.bracketCount)
                    Badge { Text(label) }
                }
            }
            if (!entry.enabled) {
                Spacer(Modifier.width(6.dp))
                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("off")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditSheet(
    entry: TagEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onBracketChange: (Int) -> Unit,
    onNumericWeightChange: (Float?) -> Unit,
    onToggleEnabled: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                PromptRenderer.renderTag(entry.tag),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            Text("Braces / brackets (×1.05 per pair)", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { onBracketChange(entry.bracketCount - 1) }) {
                    Text("[ −")
                }
                Text(
                    text = when {
                        entry.bracketCount > 0 -> "{".repeat(entry.bracketCount) + " … " + "}".repeat(entry.bracketCount)
                        entry.bracketCount < 0 -> "[".repeat(-entry.bracketCount) + " … " + "]".repeat(-entry.bracketCount)
                        else -> "none"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
                FilledTonalButton(onClick = { onBracketChange(entry.bracketCount + 1) }) {
                    Text("+ }")
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Numeric weight (x.x::tag::)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = entry.numericWeight != null,
                    onCheckedChange = { checked ->
                        onNumericWeightChange(if (checked) 1.2f else null)
                    },
                )
            }
            entry.numericWeight?.let { weight ->
                Slider(
                    value = weight,
                    onValueChange = { v -> onNumericWeightChange((v * 10).toInt() / 10f) },
                    valueRange = -2f..2f,
                    steps = 39,
                )
                Text(
                    "×${PromptRenderer.formatWeight(weight)} — negative weights need NovelAI V4.5+",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Include in prompt",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = entry.enabled, onCheckedChange = { onToggleEnabled() })
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SaveDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

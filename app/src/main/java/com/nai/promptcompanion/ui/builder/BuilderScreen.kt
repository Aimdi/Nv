package com.nai.promptcompanion.ui.builder

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nai.promptcompanion.novelai.NovelaiSyntax
import com.nai.promptcompanion.novelai.TagEntry
import com.nai.promptcompanion.util.copyToClipboard
import com.nai.promptcompanion.util.openUrl
import com.nai.promptcompanion.util.shareText
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: BuilderViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val entries by viewModel.entries.collectAsState()
    val rendered by viewModel.rendered.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val editingIndex by viewModel.editingIndex.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        AddTagField(
            query = query,
            onQueryChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            suggestions = suggestions,
            onDismissSuggestions = { viewModel.onQueryChange("") },
            onAdd = { tag ->
                viewModel.addTag(tag)
                query = ""
                viewModel.onQueryChange("")
            },
        )

        Spacer(Modifier.height(12.dp))

        ReorderableChipRow(
            entries = entries,
            onMove = viewModel::moveEntry,
            onChipClick = viewModel::openEditor,
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) {
                        viewModel.notifyClipboardEmpty()
                    } else {
                        viewModel.addParsedPrompt(text)
                        viewModel.notifyParsed()
                    }
                },
                label = { Text("Parse clipboard") },
                leadingIcon = { Icon(Icons.Default.ContentPasteGo, contentDescription = null) },
            )
            AssistChip(
                onClick = { showSaveDialog = true },
                label = { Text("Save combo") },
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
            )
            AssistChip(
                onClick = { showClearDialog = true },
                label = { Text("Clear") },
                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Live preview — NovelAI syntax",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = rendered.ifEmpty { "Tags you add render here, e.g. 1.3::masterpiece::, {{artist:wlop}}, [worst quality]" },
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = if (rendered.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Button(
                onClick = {
                    copyToClipboard(context, rendered)
                    viewModel.notifyCopied()
                },
                enabled = rendered.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            FilledTonalButton(
                onClick = {
                    copyToClipboard(context, rendered)
                    viewModel.notifyCopied()
                    openUrl(context, NovelaiSyntax.NOVELAI_URL)
                },
                enabled = rendered.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy + open NAI")
            }
            OutlinedButton(
                onClick = { shareText(context, rendered) },
                enabled = rendered.isNotEmpty(),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
    }

    if (editingIndex in entries.indices) {
        EntryEditorSheet(
            index = editingIndex,
            entry = entries[editingIndex],
            canMoveLeft = editingIndex > 0,
            canMoveRight = editingIndex < entries.lastIndex,
            onDismiss = viewModel::closeEditor,
            onChange = { viewModel.updateEntry(editingIndex, it) },
            onMove = { delta ->
                viewModel.moveEntry(editingIndex, editingIndex + delta)
                viewModel.openEditor(editingIndex + delta)
            },
            onDelete = {
                viewModel.removeEntry(editingIndex)
                viewModel.closeEditor()
            },
        )
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save combo") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Combo name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveCombo(name)
                    showSaveDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all tags?") },
            text = { Text("This removes every tag from the current combo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddTagField(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<com.nai.promptcompanion.data.catalog.ArtistTagEntity>,
    onDismissSuggestions: () -> Unit,
    onAdd: (String) -> Unit,
) {
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Add a tag or artist") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onAdd(query) }, enabled = query.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = suggestions.isNotEmpty() && query.isNotBlank(),
            onDismissRequest = onDismissSuggestions,
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            suggestions.forEach { artist ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(NovelaiSyntax.normalizeTagText(artist.tag))
                            Text(
                                "${artist.postCount} posts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onAdd(artist.tag) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorSheet(
    index: Int,
    entry: TagEntry,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onDismiss: () -> Unit,
    onChange: (TagEntry) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                NovelaiSyntax.normalizeTagText(entry.tag),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                NovelaiSyntax.renderEntry(entry),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Brace/bracket nesting: each { } multiplies attention by x1.05, [ ] divides.
            Text("Braces / brackets (×1.05 per level)", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onChange(entry.copy(bracketCount = entry.bracketCount - 1, numericWeight = null)) },
                ) { Text("[ −") }
                Text(
                    text = when {
                        entry.bracketCount > 0 -> "{".repeat(entry.bracketCount) + " … " + "}".repeat(entry.bracketCount)
                        entry.bracketCount < 0 -> "[".repeat(-entry.bracketCount) + " … " + "]".repeat(-entry.bracketCount)
                        else -> "none"
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = { onChange(entry.copy(bracketCount = entry.bracketCount + 1, numericWeight = null)) },
                ) { Text("+ }") }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Numeric emphasis (w::tag::)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "0–1 weakens · >1 strengthens · negative needs NAI V4.5+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = entry.numericWeight != null,
                    onCheckedChange = { on ->
                        onChange(
                            if (on) entry.copy(numericWeight = 1.3, bracketCount = 0)
                            else entry.copy(numericWeight = null)
                        )
                    },
                )
            }
            val weight = entry.numericWeight
            if (weight != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = weight.toFloat(),
                        onValueChange = { v ->
                            val snapped = (v * 20).roundToInt() / 20.0
                            onChange(entry.copy(numericWeight = snapped))
                        },
                        valueRange = -2f..2f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        NovelaiSyntax.formatWeight(weight),
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled in output", modifier = Modifier.weight(1f))
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = { onChange(entry.copy(enabled = it)) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMove(-1) }, enabled = canMoveLeft) { Text("← Earlier") }
                OutlinedButton(onClick = { onMove(1) }, enabled = canMoveRight) { Text("Later →") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

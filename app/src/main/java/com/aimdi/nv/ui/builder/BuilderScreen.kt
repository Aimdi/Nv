package com.aimdi.nv.ui.builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.nv.domain.NovelAiPromptRenderer
import com.aimdi.nv.domain.TagEntry
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val NOVELAI_URL = "https://novelai.net/image"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    var chipBounds by remember { mutableStateOf(mapOf<String, Pair<Float, Float>>()) }
    var draggingId by remember { mutableStateOf<String?>(null) }

    val editing = viewModel.entries.firstOrNull { it.id == viewModel.editingId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "NaiComposer",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Compose · weight · copy into NovelAI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.draftInput,
            onValueChange = viewModel::onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add tag(s)") },
            placeholder = { Text("artist_name, 1girl, …") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { viewModel.addTag() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            },
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = viewModel.useArtistPrefix,
                onClick = viewModel::toggleArtistPrefix,
                label = { Text("artist:") },
            )
            FilterChip(
                selected = viewModel.useNumericWeights,
                onClick = viewModel::toggleNumericMode,
                label = { Text("numeric ::") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::clear, enabled = viewModel.entries.isNotEmpty()) {
                Text("Clear")
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tags · long-press drag to reorder · tap to weight",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = viewModel.entries.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add tags above, or pick artists from the Artists tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            viewModel.entries.forEachIndexed { index, entry ->
                val label = NovelAiPromptRenderer.renderEntry(entry)
                InputChip(
                    selected = entry.id == viewModel.editingId,
                    onClick = { viewModel.setEditing(entry.id) },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(if (entry.enabled) 1f else 0.45f),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { viewModel.remove(entry.id) },
                        )
                    },
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInParent()
                            chipBounds = chipBounds + (entry.id to (pos.x to pos.x + coords.size.width))
                        }
                        .pointerInput(entry.id, viewModel.entries.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = entry.id },
                                onDragEnd = { draggingId = null },
                                onDragCancel = { draggingId = null },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val absX = change.position.x + (chipBounds[entry.id]?.first ?: 0f)
                                    val target = chipBounds.entries
                                        .mapNotNull { (id, range) ->
                                            val mid = (range.first + range.second) / 2f
                                            id to mid
                                        }
                                        .minByOrNull { (_, mid) -> kotlin.math.abs(mid - absX) }
                                        ?.first
                                    if (target != null && target != entry.id) {
                                        val from = viewModel.entries.indexOfFirst { it.id == entry.id }
                                        val to = viewModel.entries.indexOfFirst { it.id == target }
                                        if (from >= 0 && to >= 0) viewModel.move(from, to)
                                    }
                                },
                            )
                        }
                        .alpha(if (draggingId == entry.id) 0.7f else 1f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Preview",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        ) {
            Text(
                text = viewModel.renderedPrompt.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    copyToClipboard(context, viewModel.renderedPrompt)
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                },
                enabled = viewModel.renderedPrompt.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            FilledTonalButton(
                onClick = {
                    copyToClipboard(context, viewModel.renderedPrompt)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(NOVELAI_URL)),
                    )
                    scope.launch { snackbarHostState.showSnackbar("Copied — opening NovelAI") }
                },
                enabled = viewModel.renderedPrompt.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy+Open")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.renderedPrompt)
                    }
                    context.startActivity(Intent.createChooser(send, "Share prompt"))
                },
                enabled = viewModel.renderedPrompt.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
            FilledTonalButton(
                onClick = { showSaveDialog = true },
                enabled = viewModel.entries.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editing != null) {
        WeightEditorSheet(
            entry = editing,
            useNumeric = viewModel.useNumericWeights,
            onDismiss = { viewModel.setEditing(null) },
            onBump = { viewModel.bumpBracket(editing.id, it) },
            onNumeric = { viewModel.setNumericWeight(editing.id, it) },
            onToggleEnabled = { viewModel.toggleEnabled(editing.id) },
            onDelete = {
                viewModel.remove(editing.id)
                viewModel.setEditing(null)
            },
            onTagChange = { newTag ->
                viewModel.updateEntry(editing.id) { it.copy(tag = newTag) }
            },
        )
    }

    if (showSaveDialog) {
        SaveDialog(
            onDismiss = { showSaveDialog = false },
            onSaveCombo = { title ->
                viewModel.saveAsCombo(title) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) "Combo saved" else "Save failed")
                    }
                }
                showSaveDialog = false
            },
            onSavePrompt = { title ->
                viewModel.saveAsPrompt(title) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) "Prompt saved" else "Save failed")
                    }
                }
                showSaveDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightEditorSheet(
    entry: TagEntry,
    useNumeric: Boolean,
    onDismiss: () -> Unit,
    onBump: (Int) -> Unit,
    onNumeric: (Float?) -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onTagChange: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var slider by remember(entry.id, entry.numericWeight) {
        mutableFloatStateOf(entry.numericWeight ?: 1f)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Weight · ${entry.tag}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entry.tag,
                onValueChange = onTagChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tag") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = NovelAiPromptRenderer.renderEntry(entry),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "×${"%.2f".format(NovelAiPromptRenderer.effectiveMultiplier(entry))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(16.dp))

            if (!useNumeric || entry.numericWeight == null) {
                Text("Brace / bracket strength", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    FilledTonalButton(onClick = { onBump(-1) }) { Text("− []") }
                    Text(
                        text = entry.bracketCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(40.dp),
                    )
                    FilledTonalButton(onClick = { onBump(1) }) { Text("+ {}") }
                }
            }

            if (useNumeric) {
                Spacer(Modifier.height(8.dp))
                Text("Numeric weight (x.x::tag::)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = slider,
                    onValueChange = {
                        slider = (it * 20f).roundToInt() / 20f
                        onNumeric(slider)
                    },
                    valueRange = 0f..2.5f,
                )
                Row {
                    TextButton(onClick = {
                        slider = 1f
                        onNumeric(null)
                    }) { Text("Clear numeric") }
                    Text("  ${"%.2f".format(slider)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = entry.enabled,
                    onClick = onToggleEnabled,
                    label = { Text(if (entry.enabled) "Enabled" else "Disabled") },
                )
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SaveDialog(
    onDismiss: () -> Unit,
    onSaveCombo: (String) -> Unit,
    onSavePrompt: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSaveCombo(title) }) { Text("As combo") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSavePrompt(title) }) { Text("As prompt") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("NovelAI prompt", text))
}

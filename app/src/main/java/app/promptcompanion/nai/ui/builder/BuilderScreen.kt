package app.promptcompanion.nai.ui.builder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.promptcompanion.nai.domain.TagEntry
import app.promptcompanion.nai.ui.components.PromptPreviewBar
import app.promptcompanion.nai.ui.components.WeightEditor
import app.promptcompanion.nai.viewmodel.BuilderUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuilderScreen(
    state: BuilderUiState,
    onDraftChange: (String) -> Unit,
    onAddTag: (asArtist: Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onUpdate: (String, (TagEntry) -> TagEntry) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onCopyAndOpen: () -> Unit,
    onShare: () -> Unit,
    onSavePrompt: (String) -> Unit,
    onSaveCombo: (String) -> Unit,
) {
    val selected = state.entries.firstOrNull { it.id == state.selectedId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSavePrompt by remember { mutableStateOf(false) }
    var showSaveCombo by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf("") }
    var dragFrom by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Prompt Companion", style = MaterialTheme.typography.titleLarge)
                        Text("Compose → copy → paste into NovelAI", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { showSaveCombo = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Save combo")
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            PromptPreviewBar(
                rendered = state.rendered,
                onCopy = onCopy,
                onCopyAndOpen = onCopyAndOpen,
                onShare = onShare,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Combo builder", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Order matters — earlier tags carry more priority. Tap a chip to weight it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.draftTag,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Add tag") },
                    placeholder = { Text("1girl, artist:name, …") },
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onAddTag(false) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                IconButton(onClick = { onAddTag(true) }) {
                    Icon(Icons.Default.Brush, contentDescription = "Add as artist")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { onDraftChange("1girl"); onAddTag(false) },
                    label = { Text("1girl") },
                )
                FilterChip(
                    selected = false,
                    onClick = { onDraftChange("best quality"); onAddTag(false) },
                    label = { Text("best quality") },
                )
                FilterChip(
                    selected = false,
                    onClick = { showSavePrompt = true },
                    label = { Text("Save prompt") },
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Tags (${state.entries.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .padding(20.dp),
                ) {
                    Text(
                        "Your combo is empty. Add tags above, or pick artists from the Artists tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.entries.forEachIndexed { index, entry ->
                        InputChip(
                            selected = entry.id == state.selectedId,
                            onClick = { onSelect(entry.id) },
                            enabled = entry.enabled,
                            label = {
                                Text(
                                    buildChipLabel(entry),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.pointerInput(entry.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragFrom = index },
                                    onDragEnd = { dragFrom = null },
                                    onDragCancel = { dragFrom = null },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val from = dragFrom ?: return@detectDragGesturesAfterLongPress
                                        // Simple reorder: drag right/left by roughly one chip width.
                                        if (dragAmount.x > 48 && from < state.entries.lastIndex) {
                                            onMove(from, from + 1)
                                            dragFrom = from + 1
                                        } else if (dragAmount.x < -48 && from > 0) {
                                            onMove(from, from - 1)
                                            dragFrom = from - 1
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    "Long-press and drag sideways to reorder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { onSelect(null) },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            WeightEditor(
                entry = selected,
                onChange = { updated -> onUpdate(selected.id) { updated } },
                onDelete = {
                    onRemove(selected.id)
                    onSelect(null)
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    AnimatedVisibility(showSavePrompt || showSaveCombo) {
        if (showSavePrompt || showSaveCombo) {
            ModalBottomSheet(onDismissRequest = {
                showSavePrompt = false
                showSaveCombo = false
                saveTitle = ""
            }) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (showSavePrompt) "Save prompt" else "Save combo",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveTitle,
                        onValueChange = { saveTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        TextButton(onClick = {
                            if (showSavePrompt) onSavePrompt(saveTitle) else onSaveCombo(saveTitle)
                            showSavePrompt = false
                            showSaveCombo = false
                            saveTitle = ""
                        }) { Text("Save") }
                        TextButton(onClick = {
                            showSavePrompt = false
                            showSaveCombo = false
                            saveTitle = ""
                        }) { Text("Cancel") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun buildChipLabel(entry: TagEntry): String {
    val base = entry.tag
    return when {
        entry.numericWeight != null -> "${entry.numericWeight}::$base"
        entry.bracketCount > 0 -> "{".repeat(entry.bracketCount) + base + "}".repeat(entry.bracketCount)
        entry.bracketCount < 0 -> "[".repeat(-entry.bracketCount) + base + "]".repeat(-entry.bracketCount)
        else -> base
    }
}

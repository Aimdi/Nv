package dev.naicompanion.app.ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.data.catalog.ArtistEntity
import dev.naicompanion.app.ui.common.SaveDialog
import dev.naicompanion.app.ui.theme.PromptPreviewTextStyle
import dev.naicompanion.app.ui.util.ClipboardBridge
import dev.naicompanion.app.ui.util.ShareBridge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    viewModel: BuilderViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var editing by remember { mutableStateOf<TagEntry?>(null) }
    var showReorder by remember { mutableStateOf(false) }
    var saveTarget by remember { mutableStateOf<SaveTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                withDismissAction = message.undo == null,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
        }
    }

    // Keep the sheet bound to the live entry so edits are reflected immediately.
    val editingEntry = editing?.let { current -> state.entries.firstOrNull { it.id == current.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Builder") },
                actions = {
                    IconButton(
                        onClick = {
                            val pasted = ClipboardBridge.paste(context)
                            if (pasted.isNullOrBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Clipboard is empty") }
                            } else {
                                viewModel.appendFromText(pasted)
                            }
                        },
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste prompt")
                    }
                    IconButton(
                        onClick = { showReorder = true },
                        enabled = state.entries.size > 1,
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Reorder tags")
                    }
                    IconButton(onClick = viewModel::clear, enabled = !state.isEmpty) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear combo")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TagInputField(
                    query = query,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSubmit = {
                        viewModel.addFromInput(query)
                        focusManager.clearFocus()
                    },
                )

                if (suggestions.isNotEmpty()) {
                    SuggestionList(
                        suggestions = suggestions,
                        onPick = { viewModel.addCatalogTag(it) },
                    )
                }

                if (state.isEmpty) {
                    EmptyBuilderHint()
                } else {
                    TagChipFlow(
                        entries = state.entries,
                        onClick = { editing = it },
                        onRemove = { viewModel.remove(it.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.warnings.isNotEmpty()) {
                    WarningCard(messages = state.warnings.map { it.message })
                }
            }

            PromptOutputBar(
                rendered = state.rendered,
                tagCount = state.enabledCount,
                copyOpensNovelAi = state.settings.copyOpensNovelAi,
                onCopy = {
                    val shouldConfirm = ClipboardBridge.copy(context, state.rendered)
                    if (state.settings.copyOpensNovelAi) ShareBridge.openNovelAi(context)
                    if (shouldConfirm) {
                        scope.launch { snackbarHostState.showSnackbar("Prompt copied") }
                    }
                },
                onShare = { ShareBridge.shareText(context, state.rendered) },
                onSaveCombo = { saveTarget = SaveTarget.COMBO },
                onSavePrompt = { saveTarget = SaveTarget.PROMPT },
            )
        }
    }

    if (editingEntry != null) {
        TagEditorSheet(
            entry = editingEntry,
            renderOptions = state.settings.renderOptions,
            onDismiss = { editing = null },
            onTagTextChange = { viewModel.setTagText(editingEntry.id, it) },
            onKindChange = { viewModel.setKind(editingEntry.id, it) },
            onBracketStep = { viewModel.stepBracketCount(editingEntry.id, it) },
            onBracketSet = { viewModel.setBracketCount(editingEntry.id, it) },
            onWeightChange = { viewModel.setNumericWeight(editingEntry.id, it) },
            onEnabledToggle = { viewModel.toggleEnabled(editingEntry.id) },
            onDelete = {
                viewModel.remove(editingEntry.id)
                editing = null
            },
        )
    }

    if (showReorder) {
        ReorderTagsSheet(
            entries = state.entries,
            onMove = viewModel::move,
            onDismiss = { showReorder = false },
        )
    }

    saveTarget?.let { target ->
        SaveDialog(
            title = if (target == SaveTarget.COMBO) "Save combo" else "Save to library",
            label = if (target == SaveTarget.COMBO) "Combo name" else "Prompt title",
            initialValue = state.comboName,
            confirmLabel = "Save",
            onDismiss = { saveTarget = null },
            onConfirm = { name ->
                if (target == SaveTarget.COMBO) {
                    viewModel.saveCombo(name)
                } else {
                    viewModel.saveAsPrompt(name)
                }
                saveTarget = null
            },
        )
    }
}

private enum class SaveTarget { COMBO, PROMPT }

@Composable
private fun TagInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Add a tag") },
        placeholder = { Text("e.g. 1girl, artist:wlop, {{smile}}") },
        supportingText = {
            Text("Commas and NovelAI emphasis are parsed into separate chips")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            IconButton(onClick = onSubmit, enabled = query.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        },
    )
}

@Composable
private fun SuggestionList(suggestions: List<ArtistEntity>, onPick: (ArtistEntity) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            items(suggestions, key = { it.id }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(artist) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (TagKind.fromStorage(artist.kind) == TagKind.ARTIST) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = artist.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatPostCount(artist.postCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyBuilderHint() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Start building", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Search the tag catalog above, paste an existing prompt, or type any tag " +
                    "and press add. Tap a chip to adjust its emphasis, and drag to reorder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarningCard(messages: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Check your prompt",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            messages.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/**
 * The always-visible output bar. Copying is the primary action because the mobile NovelAI flow is
 * compose here, paste into the site.
 */
@Composable
private fun PromptOutputBar(
    rendered: String,
    tagCount: Int,
    copyOpensNovelAi: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSaveCombo: () -> Unit,
    onSavePrompt: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$tagCount ${if (tagCount == 1) "tag" else "tags"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${rendered.length} chars",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 132.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            Text(
                text = rendered.ifEmpty { "Your prompt will appear here" },
                style = PromptPreviewTextStyle,
                color = if (rendered.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCopy,
                enabled = rendered.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (copyOpensNovelAi) Icons.Default.OpenInBrowser else Icons.Default.ContentCopy,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = if (copyOpensNovelAi) "Copy & open" else "Copy")
            }
            FilledTonalButton(onClick = onShare, enabled = rendered.isNotEmpty()) {
                Icon(Icons.Default.Share, contentDescription = "Share prompt")
            }
            FilledTonalButton(onClick = onSaveCombo, enabled = rendered.isNotEmpty()) {
                Icon(Icons.Default.Save, contentDescription = "Save combo")
            }
        }

        TextButton(onClick = onSavePrompt, enabled = rendered.isNotEmpty()) {
            Text("Save rendered text to library")
        }
    }
}

fun formatPostCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M posts"
    count >= 1_000 -> "${count / 1_000}k posts"
    count > 0 -> "$count posts"
    else -> ""
}

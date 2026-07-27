package com.aimdi.nv.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.PromptEntity
import com.aimdi.nv.domain.NovelAiPromptRenderer
import com.aimdi.nv.domain.TagEntry
import com.aimdi.nv.ui.builder.copyToClipboard
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    onLoadIntoBuilder: (String) -> Unit,
    onLoadCombo: (List<TagEntry>) -> Unit,
) {
    val prompts by viewModel.prompts.collectAsState()
    val combos by viewModel.combos.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val displayedPrompts = viewModel.searchResults ?: prompts

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Saved prompts & combos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        TabRow(selectedTabIndex = viewModel.tab) {
            Tab(
                selected = viewModel.tab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("Prompts (${prompts.size})") },
            )
            Tab(
                selected = viewModel.tab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text("Combos (${combos.size})") },
            )
        }

        if (viewModel.tab == 0) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                label = { Text("Search prompts") },
                singleLine = true,
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (displayedPrompts.isEmpty()) {
                    item {
                        Text(
                            "No saved prompts yet. Save from the Builder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(displayedPrompts, key = { it.id }) { prompt ->
                    PromptRow(
                        prompt = prompt,
                        onCopy = {
                            copyToClipboard(context, prompt.body)
                            scope.launch { snackbarHostState.showSnackbar("Copied") }
                        },
                        onLoad = { onLoadIntoBuilder(prompt.body) },
                        onFavorite = { viewModel.toggleFavorite(prompt.id) },
                        onDelete = { viewModel.deletePrompt(prompt.id) },
                    )
                    HorizontalDivider()
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (combos.isEmpty()) {
                    item {
                        Text(
                            "No saved combos yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(combos, key = { it.id }) { combo ->
                    val entries = viewModel.parseCombo(combo)
                    val preview = NovelAiPromptRenderer.render(entries)
                    ComboRow(
                        combo = combo,
                        preview = preview,
                        onCopy = {
                            copyToClipboard(context, preview)
                            scope.launch { snackbarHostState.showSnackbar("Copied") }
                        },
                        onLoad = { onLoadCombo(entries) },
                        onDelete = { viewModel.deleteCombo(combo.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PromptRow(
    prompt: PromptEntity,
    onCopy: () -> Unit,
    onLoad: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                prompt.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onFavorite) {
                Icon(
                    if (prompt.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Favorite",
                    tint = if (prompt.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
        Text(
            prompt.body,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onLoad) { Text("Load into builder") }
    }
}

@Composable
private fun ComboRow(
    combo: ComboEntity,
    preview: String,
    onCopy: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                combo.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onLoad) { Text("Load into builder") }
    }
}

package app.promptcompanion.nai.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.viewmodel.LibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onLoadPrompt: (PromptEntity) -> Unit,
    onLoadCombo: (ComboEntity) -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onDeleteCombo: (Long) -> Unit,
    onTogglePromptFavorite: (PromptEntity) -> Unit,
    onToggleComboFavorite: (ComboEntity) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onCopyText: (String) -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = {
                        exportLauncher.launch("prompt-companion-backup.json")
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "Export JSON")
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Import JSON")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search prompts") },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Prompts and saved combos stay on-device. Export JSON for backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Text("Combos (${state.combos.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (state.combos.isEmpty()) {
                item {
                    Text("No saved combos yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.combos, key = { "c-${it.id}" }) { combo ->
                    LibraryRow(
                        title = combo.title,
                        subtitle = combo.entriesJson.take(120),
                        favorite = combo.isFavorite,
                        onClick = { onLoadCombo(combo) },
                        onFavorite = { onToggleComboFavorite(combo) },
                        onDelete = { onDeleteCombo(combo.id) },
                        onCopy = null,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Prompts (${state.prompts.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (state.prompts.isEmpty()) {
                item {
                    Text("No saved prompts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.prompts, key = { "p-${it.id}" }) { prompt ->
                    LibraryRow(
                        title = prompt.title,
                        subtitle = prompt.body,
                        favorite = prompt.isFavorite,
                        onClick = { onLoadPrompt(prompt) },
                        onFavorite = { onTogglePromptFavorite(prompt) },
                        onDelete = { onDeletePrompt(prompt.id) },
                        onCopy = { onCopyText(prompt.body) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tap to load into builder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

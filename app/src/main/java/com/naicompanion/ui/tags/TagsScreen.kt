package com.naicompanion.ui.tags

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naicompanion.data.AppRepository
import com.naicompanion.data.db.ArtistWithFavorite
import com.naicompanion.ui.builder.BuilderViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun TagsScreen(
    repository: AppRepository,
    builderViewModel: BuilderViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val viewModel: TagsViewModel = viewModel(factory = TagsViewModel.factory(repository))

    val query by viewModel.query.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val sortByName by viewModel.sortByName.collectAsState()
    val artistPrefixEnabled by viewModel.artistPrefixEnabled.collectAsState()
    val catalogReady by viewModel.catalogReady.collectAsState()
    val artists by viewModel.artists.collectAsState()

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var detailTarget by remember { mutableStateOf<ArtistWithFavorite?>(null) }

    fun copyRawTag(rawName: String) {
        clipboard.setText(AnnotatedString(rawName.replace('_', ' ')))
        scope.launch { snackbarHostState.showSnackbar("Tag copied") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search artist tags") },
            placeholder = { Text("e.g. mizuki hitoshi") },
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { viewModel.setFavoritesOnly(!favoritesOnly) },
                label = { Text("Favorites") },
            )
            FilterChip(
                selected = sortByName,
                onClick = { viewModel.setSortByName(!sortByName) },
                label = { Text(if (sortByName) "Sort: name" else "Sort: posts") },
            )
            FilterChip(
                selected = artistPrefixEnabled,
                onClick = { viewModel.setArtistPrefixEnabled(!artistPrefixEnabled) },
                label = { Text("artist: prefix") },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (!catalogReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Importing tag catalog…")
                }
            }
        } else if (artists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No matching artist tags.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(artists, key = { it.artist.id }) { item ->
                    ArtistRow(
                        item = item,
                        onClick = { detailTarget = item },
                        onLongClick = { copyRawTag(item.artist.name) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        onAdd = {
                            builderViewModel.addArtist(item.artist)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Added \"${item.artist.displayName}\" to combo"
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    detailTarget?.let { item ->
        ArtistDetailDialog(
            item = item,
            onAdd = {
                builderViewModel.addArtist(item.artist)
                scope.launch {
                    snackbarHostState.showSnackbar("Added \"${item.artist.displayName}\" to combo")
                }
            },
            onCopy = { copyRawTag(item.artist.name) },
            onToggleFavorite = {
                viewModel.toggleFavorite(item)
                detailTarget = item.copy(isFavorite = !item.isFavorite)
            },
            onDismiss = { detailTarget = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    item: ArtistWithFavorite,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.artist.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.artist.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${NumberFormat.getInstance().format(item.artist.postCount)} posts · ${item.artist.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite)
                        MaterialTheme.colorScheme.secondary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add to combo")
            }
        }
    }
}

@Composable
private fun ArtistDetailDialog(
    item: ArtistWithFavorite,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.artist.displayName) },
        text = {
            Column {
                Text(
                    "${NumberFormat.getInstance().format(item.artist.postCount)} Danbooru posts",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Source: ${item.artist.source} preview dataset",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Long-press any row to copy the raw tag. Preview images arrive with the phase-4 image pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(); onDismiss() }) { Text("Add to combo") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onToggleFavorite) {
                    Text(if (item.isFavorite) "Unfavorite" else "Favorite")
                }
                TextButton(onClick = { onCopy(); onDismiss() }) { Text("Copy tag") }
            }
        },
    )
}

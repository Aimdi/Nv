package app.promptcompanion.nai.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.promptcompanion.nai.data.db.entity.ArtistEntity
import app.promptcompanion.nai.viewmodel.BrowserUiState
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    thumbnailResolver: (ArtistEntity) -> File?,
    onQueryChange: (String) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onSort: (String) -> Unit,
    onSelect: (ArtistEntity?) -> Unit,
    onToggleFavorite: (ArtistEntity) -> Unit,
    onAddToBuilder: (ArtistEntity, weighted: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Artists")
                        Text(
                            "${state.catalogCount} tags · source: NAI v3",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search artists (FTS)") },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { onFavoritesOnly(!state.favoritesOnly) },
                    label = { Text("Favorites") },
                )
                FilterChip(
                    selected = state.sort == "posts",
                    onClick = { onSort("posts") },
                    label = { Text("Posts") },
                )
                FilterChip(
                    selected = state.sort == "name",
                    onClick = { onSort("name") },
                    label = { Text("Name") },
                )
            }
            if (!state.packInstalled) {
                Text(
                    "Preview pack not installed — showing names only. Download from Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.artists, key = { it.id }) { artist ->
                    ArtistCell(
                        artist = artist,
                        thumb = thumbnailResolver(artist),
                        onClick = { onSelect(artist) },
                        onLongClick = { onAddToBuilder(artist, false) },
                        onFavorite = { onToggleFavorite(artist) },
                    )
                }
            }
        }
    }

    state.selected?.let { artist ->
        ModalBottomSheet(
            onDismissRequest = { onSelect(null) },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(Modifier.padding(20.dp)) {
                val detail = thumbnailResolver(artist)
                if (detail != null) {
                    AsyncImage(
                        model = detail,
                        contentDescription = artist.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(artist.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "artist:${artist.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("Posts: ${artist.postCount}")
                Text("Source: ${sourceLabel(artist.source)}")
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { onAddToBuilder(artist, false) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add to combo")
                    }
                    TextButton(onClick = { onAddToBuilder(artist, true) }) {
                        Text("Add 1.1::")
                    }
                    IconButton(onClick = { onToggleFavorite(artist) }) {
                        Icon(
                            if (artist.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistCell(
    artist: ArtistEntity,
    thumb: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = artist.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    artist.displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                )
                .padding(10.dp),
        ) {
            Column {
                Text(
                    artist.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${artist.postCount} posts · ${sourceLabel(artist.source)}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onFavorite,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                if (artist.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (artist.isFavorite) MaterialTheme.colorScheme.secondary else Color.White,
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    ArtistEntity.SOURCE_NAI_V3 -> "NAI v3"
    ArtistEntity.SOURCE_ILLUSTRIOUS -> "Illustrious/NoobAI"
    else -> source
}

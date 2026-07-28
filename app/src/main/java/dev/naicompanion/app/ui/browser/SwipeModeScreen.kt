package dev.naicompanion.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.naicompanion.app.ui.builder.formatPostCount
import kotlinx.coroutines.launch

/**
 * Full-screen, one-tag-at-a-time discovery. Swiping sideways moves through the same filtered list
 * the grid shows, which makes it a fast way to triage a few hundred unfamiliar artist tags.
 */
@Composable
fun SwipeModeScreen(
    items: List<BrowseItem>,
    onExit: () -> Unit,
    onFavorite: (BrowseItem) -> Unit,
    onAdd: (BrowseItem) -> Unit,
) {
    // Filters can empty the list while swipe mode is open; leaving must happen as an effect
    // rather than as a side effect of composition.
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) onExit()
    }
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
        ) { page ->
            val item = items[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.thumbnail != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.thumbnail)
                                .memoryCacheKey(item.artist.name)
                                .diskCacheKey(item.artist.name)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.artist.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Default.ImageNotSupported, contentDescription = null)
                    }
                }

                Text(
                    text = item.artist.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = listOfNotNull(
                        sourceLabel(item.artist.source),
                        formatPostCount(item.artist.postCount).takeIf { it.isNotEmpty() },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Exit swipe mode")
        }

        Text(
            text = "${pagerState.currentPage + 1} / ${items.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(20.dp),
        )

        val current = items.getOrNull(pagerState.currentPage)
        if (current != null) {
            fun advance() {
                scope.launch {
                    val next = pagerState.currentPage + 1
                    if (next < items.size) pagerState.animateScrollToPage(next)
                }
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { advance() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Skip")
                }

                LargeFloatingActionButton(
                    onClick = {
                        onFavorite(current)
                        advance()
                    },
                ) {
                    Icon(
                        imageVector = if (current.isFavorite) {
                            Icons.Default.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = "Favorite",
                    )
                }

                FilledTonalIconButton(
                    onClick = { onAdd(current) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add to builder")
                }
            }
        }
    }
}

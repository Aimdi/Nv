package dev.naicompanion.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.naicompanion.app.core.prompt.NovelAiPromptRenderer
import dev.naicompanion.app.core.prompt.RenderOptions
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.ui.builder.formatPostCount
import dev.naicompanion.app.ui.theme.PromptPreviewTextStyle
import dev.naicompanion.app.ui.util.ClipboardBridge
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagDetailSheet(
    item: BrowseItem,
    fullImage: File?,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val renderedTag = NovelAiPromptRenderer.renderEntry(
        TagEntry(tag = item.artist.name, kind = item.kind),
        RenderOptions.Default,
    ).orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The upstream previews are portrait (832x1216), so a square frame would
                    // letterbox them heavily.
                    .aspectRatio(PREVIEW_ASPECT_RATIO)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (fullImage != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(fullImage).crossfade(true).build(),
                        contentDescription = item.artist.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.ImageNotSupported, contentDescription = null)
                        Text(
                            text = "No preview installed for this source",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text(item.artist.displayName, style = MaterialTheme.typography.headlineSmall)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = {}, label = { Text(sourceLabel(item.artist.source)) })
                if (item.artist.postCount > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(formatPostCount(item.artist.postCount)) },
                    )
                }
                item.artist.uniqueness?.let { uniqueness ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Distinctiveness ${"%.2f".format(uniqueness)}") },
                    )
                }
            }

            Text(
                text = renderedTag,
                style = PromptPreviewTextStyle,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Previews are generated samples from the source dataset, not the artist's " +
                    "own work, and other NovelAI model versions can render this tag differently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onAdd()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to builder")
                }
                FilledTonalButton(onClick = { ClipboardBridge.copy(context, renderedTag) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy tag")
                }
                FilledTonalButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) {
                            Icons.Default.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = "Toggle favorite",
                    )
                }
            }
        }
    }
}

/** Aspect ratio of the upstream preview renders (832x1216). */
private const val PREVIEW_ASPECT_RATIO = 832f / 1216f

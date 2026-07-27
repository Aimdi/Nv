package app.promptcompanion.nai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.promptcompanion.nai.domain.NovelAiSyntax
import app.promptcompanion.nai.domain.TagEntry
import kotlin.math.roundToInt

@Composable
fun PromptPreviewBar(
    rendered: String,
    onCopy: () -> Unit,
    onCopyAndOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text("Live prompt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
        ) {
            Text(
                text = rendered.ifBlank { "Add tags to build a NovelAI prompt…" },
                style = MaterialTheme.typography.bodySmall,
                color = if (rendered.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onCopy, enabled = rendered.isNotBlank()) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onShare, enabled = rendered.isNotBlank()) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCopyAndOpen, enabled = rendered.isNotBlank()) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("NovelAI")
            }
        }
    }
}

@Composable
fun WeightEditor(
    entry: TagEntry,
    onChange: (TagEntry) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(entry.tag, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Text("Brace / bracket nesting", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    onChange(entry.copy(bracketCount = (entry.bracketCount - 1).coerceAtLeast(-5), numericWeight = null))
                },
            ) { Icon(Icons.Default.Remove, contentDescription = "Weaken") }
            Text(
                text = when {
                    entry.numericWeight != null -> "numeric"
                    entry.bracketCount > 0 -> "{".repeat(entry.bracketCount) + "…}" 
                    entry.bracketCount < 0 -> "[".repeat(-entry.bracketCount) + "…]"
                    else -> "neutral"
                },
                modifier = Modifier.width(96.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = {
                    onChange(entry.copy(bracketCount = (entry.bracketCount + 1).coerceAtMost(5), numericWeight = null))
                },
            ) { Icon(Icons.Default.Add, contentDescription = "Strengthen") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Numeric weight: ${entry.numericWeight?.let { NovelAiSyntax.formatWeight(it) } ?: "off"}",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = (entry.numericWeight ?: 1.0).toFloat().coerceIn(0f, 2.5f),
            onValueChange = { v ->
                val rounded = ((v * 20).roundToInt() / 20f).toDouble()
                onChange(entry.copy(numericWeight = rounded, bracketCount = 0))
            },
            valueRange = 0f..2.5f,
        )
        Row {
            TextButton(onClick = { onChange(entry.copy(numericWeight = null)) }) { Text("Clear numeric") }
            TextButton(onClick = { onChange(entry.copy(enabled = !entry.enabled)) }) {
                Text(if (entry.enabled) "Disable" else "Enable")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("Delete") }
        }

        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "Preview: " + (NovelAiSyntax.renderTag(
                    entry.tag,
                    entry.bracketCount,
                    entry.numericWeight,
                    entry.enabled,
                    entry.forceArtistPrefix,
                ) ?: "(disabled)"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

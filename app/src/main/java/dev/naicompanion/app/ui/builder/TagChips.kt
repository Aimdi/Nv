package dev.naicompanion.app.ui.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.naicompanion.app.core.prompt.TagEntry
import dev.naicompanion.app.core.prompt.TagKind
import dev.naicompanion.app.core.prompt.formatWeight

/**
 * Compact overview of the combo. Order is meaningful (NovelAI weights earlier tags more), so the
 * chips always read left-to-right, top-to-bottom in prompt order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipFlow(
    entries: List<TagEntry>,
    onClick: (TagEntry) -> Unit,
    onRemove: (TagEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        entries.forEach { entry ->
            TagChip(
                entry = entry,
                onClick = { onClick(entry) },
                onRemove = { onRemove(entry) },
            )
        }
    }
}

@Composable
fun TagChip(
    entry: TagEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badge = weightBadge(entry)
    InputChip(
        modifier = modifier,
        selected = entry.enabled,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = if (entry.enabled) null else TextDecoration.LineThrough,
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        },
        leadingIcon = if (entry.kind == TagKind.ARTIST) {
            {
                Icon(
                    Icons.Default.Brush,
                    contentDescription = "Artist tag",
                    modifier = Modifier.size(InputChipDefaults.AvatarSize),
                )
            }
        } else {
            null
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${entry.tag}",
                modifier = Modifier
                    .size(18.dp)
                    .clickableNoRipple(onRemove),
            )
        },
    )
}

/**
 * Short label describing the emphasis on a chip: the numeric weight when set, otherwise the
 * bracket nesting as `{}`/`[]` so it mirrors what will be emitted.
 */
fun weightBadge(entry: TagEntry): String? {
    val numeric = entry.numericWeight
    val brackets = entry.bracketCount
    return when {
        numeric != null && brackets != 0 -> "${formatWeight(numeric)} ${bracketLabel(brackets)}"
        numeric != null -> formatWeight(numeric)
        brackets != 0 -> bracketLabel(brackets)
        else -> null
    }
}

private fun bracketLabel(count: Int): String =
    if (count > 0) "{}".repeat(minOf(count, 3)).let { if (count > 3) "{}x$count" else it }
    else "[]".repeat(minOf(-count, 3)).let { if (-count > 3) "[]x${-count}" else it }

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

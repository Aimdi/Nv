package dev.naicompanion.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.naicompanion.app.core.prompt.ArtistStrength
import dev.naicompanion.app.data.catalog.ArtistEntity

/**
 * Compact V4.5 strength chip. Post count is popularity on Danbooru; this is whether the tag
 * actually pulls style on NovelAI V4.5 according to nax.moe community votes.
 */
@Composable
fun StrengthBadge(
    strength: ArtistStrength,
    score: Int? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (strength == ArtistStrength.UNKNOWN && compact) return

    val (background, foreground) = strengthColors(strength)
    val label = buildString {
        append(strength.shortLabel)
        if (score != null && strength != ArtistStrength.UNKNOWN) {
            append(' ')
            if (score > 0) append('+')
            append(score)
        }
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

@Composable
fun ArtistMetaRow(
    artist: ArtistEntity,
    showPostCount: Boolean = true,
    showVoteCount: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrengthBadge(strength = artist.strength, score = artist.naxScore, compact = true)
        val votes = artist.naxVotes
        if (showVoteCount && votes != null && votes > 0) {
            Text(
                text = "$votes votes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showPostCount && artist.postCount > 0) {
            Text(
                text = formatCompactCount(artist.postCount) + " posts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun strengthColors(strength: ArtistStrength): Pair<Color, Color> = when (strength) {
    ArtistStrength.STRONG -> Color(0xFF1B5E20) to Color(0xFFC8E6C9)
    ArtistStrength.SOLID -> Color(0xFF0D47A1) to Color(0xFFBBDEFB)
    ArtistStrength.MIXED -> Color(0xFF4E342E) to Color(0xFFFFE0B2)
    ArtistStrength.WEAK -> Color(0xFF4A148C).copy(alpha = 0.35f) to
        MaterialTheme.colorScheme.onSurfaceVariant
    ArtistStrength.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
}

fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}k"
    else -> "$count"
}

fun strengthExplanation(artist: ArtistEntity): String = when (artist.strength) {
    ArtistStrength.STRONG ->
        "Strong style pull on NovelAI V4.5 (nax.moe ${signed(artist.naxScore)} from " +
            "${artist.naxVotes} votes). Changes the look reliably — start at neutral weight."
    ArtistStrength.SOLID ->
        "Solid style pull on V4.5 (score ${signed(artist.naxScore)}). Usually works without " +
            "extra weight; place it early if you want it to lead."
    ArtistStrength.MIXED ->
        "Mixed V4.5 results (score ${signed(artist.naxScore)}). Pair with a stronger artist, " +
            "or try a light 1.1:: nudge."
    ArtistStrength.WEAK ->
        "Weak style pull on V4.5 (score ${signed(artist.naxScore)}). Popularity is not strength — " +
            "prefer a Solid/Strong tag, or raise the weight yourself if you know the look."
    ArtistStrength.UNKNOWN ->
        "No V4.5 community rating yet. Danbooru post count is popularity, not style pull."
}

private fun signed(value: Int?): String = when {
    value == null -> "?"
    value > 0 -> "+$value"
    else -> "$value"
}

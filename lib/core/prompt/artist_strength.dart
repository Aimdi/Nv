import 'dart:math' as math;

/// How strongly an artist tag tends to pull NovelAI V4.5 toward its style.
///
/// Rankings come from community votes on nax.moe V4.5 artist galleries, not
/// Danbooru post counts. High-post artists are often stylistically weak.
enum ArtistStrength {
  strong('Strong on V4.5', 'Strong'),
  solid('Solid on V4.5', 'Solid'),
  mixed('Mixed on V4.5', 'Mixed'),
  weak('Weak on V4.5', 'Weak'),
  unknown('Untested on V4.5', '—');

  const ArtistStrength(this.label, this.shortLabel);
  final String label;
  final String shortLabel;

  /// Minimum total votes before a score is treated as meaningful.
  static const minVotes = 3;

  static ArtistStrength fromVotes(int? score, int? votes) {
    if (score == null || votes == null || votes < minVotes) {
      return ArtistStrength.unknown;
    }
    if (score >= 15) return ArtistStrength.strong;
    if (score >= 5) return ArtistStrength.solid;
    if (score <= -3) return ArtistStrength.weak;
    return ArtistStrength.mixed;
  }

  /// Default numeric emphasis when adding as a primary style tag.
  ///
  /// NovelAI V4+ prefers `1.1::artist:name::` over `{artist:name}` —
  /// braces only multiply by 1.05 per nesting and get hard to read.
  double get primaryEmphasis => switch (this) {
        ArtistStrength.strong => 1.1,
        ArtistStrength.solid => 1.1,
        ArtistStrength.mixed => 1.15,
        ArtistStrength.weak => 1.2,
        ArtistStrength.unknown => 1.1,
      };

  /// Numeric deemphasis when adding as a support / accent artist.
  ///
  /// Prefer `0.8::artist:name::` over `[artist:name]` (brackets ≈ ÷1.05).
  double get supportEmphasis => switch (this) {
        ArtistStrength.strong => 0.85,
        ArtistStrength.solid => 0.8,
        ArtistStrength.mixed => 0.75,
        ArtistStrength.weak => 0.7,
        ArtistStrength.unknown => 0.8,
      };
}

/// Bayesian shrinkage toward zero: `score * votes / (votes + prior)`.
/// Same raw score with more votes ranks higher; low-vote spikes shrink.
double strengthRankScore(int? score, int? votes) {
  if (score == null || votes == null || votes <= 0) {
    return double.negativeInfinity;
  }
  return score.toDouble() * votes / (votes + 10.0);
}

/// Sampling weight for MIX ARTISTS. Rated artists use shrunk strength;
/// unrated fall back to a soft Danbooru popularity prior.
double artistSampleWeight({
  required int postCount,
  int? naxScore,
  int? naxVotes,
}) {
  final rank = strengthRankScore(naxScore, naxVotes);
  if (rank.isFinite) {
    // Shift so negative scores still have a small chance, but lose to Solid+.
    return math.max(0.05, rank + 8.0);
  }
  return math.max(1.0, math.log(postCount + 1.0));
}

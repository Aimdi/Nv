import 'artist_mix_engine.dart';
import 'artist_strength.dart';
import 'style_fingerprint.dart';

enum MixRole { lead, support, accent }

class PlannedPick {
  const PlannedPick({
    required this.hit,
    required this.role,
    required this.reason,
  });

  final StyleMatchHit hit;
  final MixRole role;
  final String reason;

  String get name => hit.name;
}

class StyleMixPlan {
  const StyleMixPlan({
    required this.brief,
    required this.picks,
    required this.mix,
    required this.steps,
  });

  final StyleProfile brief;
  final List<PlannedPick> picks;
  final String mix;
  final List<String> steps;
}

/// Builds a 2–3 artist mix from a *read* of the image, not top-3 clones.
class StyleMixPlanner {
  StyleMixPlanner._();

  static StyleMixPlan plan({
    required StyleProfile query,
    required List<StyleMatchHit> sourceHits,
    required List<StyleFingerprintEntry> catalog,
    ArtistMixCatalog? mixCatalog,
  }) {
    final cat = mixCatalog ?? ArtistMixEngine.catalog;
    final steps = <String>[
      'Read the image as ${query.summary}.',
      ...query.observations,
    ];

    final scored = <_Scored>[];
    for (final entry in catalog) {
      if (entry.strength == ArtistStrength.weak) continue;
      final style = StyleFingerprint.cosine(query.vector, entry.vector);
      final naxBoost = _naxBoost(entry.naxScore, entry.naxVotes);
      scored.add(
        _Scored(
          entry: entry,
          style: style,
          total: style + naxBoost,
        ),
      );
    }
    scored.sort((a, b) => b.total.compareTo(a.total));

    final picks = <PlannedPick>[];
    final used = <String>{};

    final sourceLead = sourceHits.cast<StyleMatchHit?>().firstWhere(
          (h) => h!.strength != ArtistStrength.weak,
          orElse: () => null,
        );
    if (sourceLead != null) {
      picks.add(
        PlannedPick(
          hit: sourceLead,
          role: MixRole.lead,
          reason: sourceLead.source == StyleMatchSource.metadata
              ? 'This file already names the artist — use it as the lead, not a guess.'
              : 'Danbooru reverse search hit this artist. That’s a source ID, so it leads.',
        ),
      );
      used.add(ArtistMixEngine.canonicalName(sourceLead.name));
      steps.add(
        'Lead is a source hit (${sourceLead.name}), not a visual neighbor.',
      );
    } else {
      final lead = _firstUsable(scored, used);
      if (lead != null) {
        picks.add(
          PlannedPick(
            hit: lead.toHit(),
            role: MixRole.lead,
            reason:
                'Closest ${query.family} rendering among Solid+ V4.5 tags '
                '(style ${lead.style.toStringAsFixed(2)}, '
                '${lead.entry.strength.shortLabel}).',
          ),
        );
        used.add(ArtistMixEngine.canonicalName(lead.entry.tag));
        steps.add(
          'No source ID, so the lead is the best style match that also '
          'pulls on V4.5 — not the nearest color histogram.',
        );
      }
    }

    final glue = cat.glue.map(ArtistMixEngine.canonicalName).toSet();
    final bucket = cat.buckets[query.bucketHint]
            ?.map(ArtistMixEngine.canonicalName)
            .toSet() ??
        {};

    final support = _pickSupport(
      scored: scored,
      used: used,
      glue: glue,
      bucket: bucket,
      preferGlue: true,
    );
    if (support != null) {
      final why = glue.contains(ArtistMixEngine.canonicalName(support.entry.tag))
          ? 'Known mixer/glue so the lead does not flatten into a single look.'
          : 'Same ${query.bucketHint} family, but not a clone of the lead.';
      picks.add(
        PlannedPick(
          hit: support.toHit(),
          role: MixRole.support,
          reason: why,
        ),
      );
      used.add(ArtistMixEngine.canonicalName(support.entry.tag));
      steps.add('Support is a mixer, not the 2nd-nearest twin of the lead.');
    }

    final accent = _pickAccent(
      query: query,
      scored: scored,
      used: used,
      glue: glue,
    );
    if (accent != null) {
      picks.add(
        PlannedPick(
          hit: accent.toHit(),
          role: MixRole.accent,
          reason: query.edge >= 0.10
              ? 'Softer accent so the mix does not go full graphic-novel.'
              : 'A bit more structure so the painterly lead still reads as a tag.',
        ),
      );
      used.add(ArtistMixEngine.canonicalName(accent.entry.tag));
      steps.add('Accent is complementary (line vs paint), not another neighbor.');
    }

    if (picks.length < 2) {
      for (final row in scored) {
        final name = ArtistMixEngine.canonicalName(row.entry.tag);
        if (used.contains(name)) continue;
        picks.add(
          PlannedPick(
            hit: row.toHit(),
            role: picks.isEmpty ? MixRole.lead : MixRole.support,
            reason: 'Fallback Solid+ style match.',
          ),
        );
        used.add(name);
        if (picks.length >= 2) break;
      }
    }

    final mix = ArtistMixEngine.buildRankedMix(picks.map((p) => p.name).toList());
    return StyleMixPlan(brief: query, picks: picks, mix: mix, steps: steps);
  }

  static double _naxBoost(int? score, int? votes) {
    final rank = strengthRankScore(score, votes);
    if (!rank.isFinite) return -0.04;
    // Small prior: a Strong tag beats a slightly closer untested one.
    return (rank / (rank.abs() + 20.0)) * 0.12;
  }

  static _Scored? _firstUsable(List<_Scored> scored, Set<String> used) {
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.entry.strength == ArtistStrength.unknown && row.style < 0.82) {
        continue;
      }
      return row;
    }
    return scored.cast<_Scored?>().firstWhere(
          (r) => !used.contains(ArtistMixEngine.canonicalName(r!.entry.tag)),
          orElse: () => null,
        );
  }

  static _Scored? _pickSupport({
    required List<_Scored> scored,
    required Set<String> used,
    required Set<String> glue,
    required Set<String> bucket,
    required bool preferGlue,
  }) {
    _Scored? best;
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.style > 0.97) continue; // clone of the lead
      var bonus = 0.0;
      if (glue.contains(name)) bonus += 0.06;
      if (bucket.contains(name)) bonus += 0.03;
      if (preferGlue && glue.contains(name)) bonus += 0.04;
      final total = row.total + bonus;
      if (best == null || total > best.total + 0.001) {
        best = _Scored(entry: row.entry, style: row.style, total: total);
      }
    }
    return best;
  }

  static _Scored? _pickAccent({
    required StyleProfile query,
    required List<_Scored> scored,
    required Set<String> used,
    required Set<String> glue,
  }) {
    _Scored? best;
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.style > 0.96) continue;
      final edge = row.entry.edge ?? query.edge;
      // Complementary: if query is line-heavy, prefer softer accents.
      final complement = query.edge >= 0.10
          ? (0.12 - edge).clamp(0.0, 0.12)
          : (edge - 0.06).clamp(0.0, 0.12);
      var total = row.total + complement * 0.4;
      if (glue.contains(name)) total += 0.03;
      if (best == null || total > best.total) {
        best = _Scored(entry: row.entry, style: row.style, total: total);
      }
    }
    return best;
  }
}

class _Scored {
  const _Scored({
    required this.entry,
    required this.style,
    required this.total,
  });

  final StyleFingerprintEntry entry;
  final double style;
  final double total;

  StyleMatchHit toHit() => StyleMatchHit(
        name: entry.tag,
        score: style,
        source: StyleMatchSource.visual,
        naxScore: entry.naxScore,
        naxVotes: entry.naxVotes,
        detail: 'style ${style.toStringAsFixed(2)}',
      );
}

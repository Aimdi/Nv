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

/// Builds a 2–3 artist mix from a *read* of the image.
///
/// Ranking is z-distance on ink / sat / contrast, not cosine of a sat/val
/// histogram (that space is collapsed on nax hoodie previews). After the
/// lead is chosen, a community-tested seed triple wins over an invented
/// nearest-neighbor stack.
class StyleMixPlanner {
  StyleMixPlanner._();

  static const _compatibleDistance = 3.2;
  static const _minDiversity = 0.50;

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
      final dist = query.distanceTo(entry);
      final fit = 1.0 / (1.0 + dist);
      scored.add(
        _Scored(
          entry: entry,
          dist: dist,
          fit: fit,
          total: fit + _naxBoost(entry.naxScore, entry.naxVotes),
        ),
      );
    }
    scored.sort((a, b) => b.total.compareTo(a.total));

    final picks = <PlannedPick>[];
    final used = <String>{};
    StyleFingerprintEntry? leadEntry;

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
      leadEntry = _lookup(catalog, sourceLead.name);
      steps.add(
        'Lead is a source hit (${sourceLead.name}), not a visual neighbor.',
      );
    } else {
      final lead = _firstUsable(scored, used);
      if (lead != null) {
        leadEntry = lead.entry;
        picks.add(
          PlannedPick(
            hit: lead.toHit(),
            role: MixRole.lead,
            reason:
                'Closest ${query.family} rendering among Solid+ V4.5 tags '
                '(Δ ${lead.dist.toStringAsFixed(2)}, '
                '${lead.entry.strength.shortLabel}).',
          ),
        );
        used.add(ArtistMixEngine.canonicalName(lead.entry.tag));
        steps.add(
          'No source ID, so the lead is the nearest ink/sat/contrast match '
          'that also pulls on V4.5 — not a color histogram neighbor.',
        );
      }
    }

    final glue = cat.glue.map(ArtistMixEngine.canonicalName).toSet();
    final bucket = cat.buckets[query.bucketHint]
            ?.map(ArtistMixEngine.canonicalName)
            .toSet() ??
        {};

    final triple = _bestTriple(
      leadName: picks.isEmpty ? null : picks.first.name,
      query: query,
      catalog: catalog,
      triples: cat.triples,
    );
    if (triple != null && picks.isNotEmpty) {
      steps.add(
        'Lead sits in a community-tested ${triple.style} triple — '
        'use that mix instead of inventing neighbors.',
      );
      for (final raw in triple.artists) {
        final name = ArtistMixEngine.canonicalName(raw);
        if (used.contains(name)) continue;
        final entry = _lookup(catalog, raw);
        final hit = entry != null
            ? _Scored(
                entry: entry,
                dist: query.distanceTo(entry),
                fit: query.fitTo(entry),
                total: query.fitTo(entry),
              ).toHit()
            : StyleMatchHit(
                name: raw,
                score: 0.6,
                source: StyleMatchSource.visual,
                detail: 'seed triple',
              );
        final role = picks.length == 1 ? MixRole.support : MixRole.accent;
        picks.add(
          PlannedPick(
            hit: hit,
            role: role,
            reason:
                'Partner in the known ${triple.style} triple with '
                '${ArtistMixEngine.promptName(picks.first.name)}.',
          ),
        );
        used.add(name);
        if (picks.length >= 3) break;
      }
    }

    if (picks.length < 2) {
      final support = _pickSupport(
        query: query,
        scored: scored,
        used: used,
        glue: glue,
        bucket: bucket,
        lead: leadEntry,
      );
      if (support != null) {
        final name = ArtistMixEngine.canonicalName(support.entry.tag);
        final why = glue.contains(name)
            ? 'Known mixer/glue, compatible with this ${query.family} read '
                '(Δ ${support.dist.toStringAsFixed(2)}), not a clone of the lead.'
            : 'Same ${query.bucketHint} family and a different rendering '
                '(Δ ${support.dist.toStringAsFixed(2)} from the image).';
        picks.add(
          PlannedPick(
            hit: support.toHit(),
            role: MixRole.support,
            reason: why,
          ),
        );
        used.add(name);
        steps.add(
          'Support must be compatible and diverse — glue preferred, clones skipped.',
        );
      }
    }

    if (picks.length < 3) {
      final accent = _pickAccent(
        query: query,
        scored: scored,
        used: used,
        glue: glue,
        lead: leadEntry,
      );
      if (accent != null) {
        picks.add(
          PlannedPick(
            hit: accent.toHit(),
            role: MixRole.accent,
            reason: _accentReason(query, accent.entry),
          ),
        );
        used.add(ArtistMixEngine.canonicalName(accent.entry.tag));
        steps.add(
          'Accent is complementary on the strongest axis (ink vs paint, or sat).',
        );
      }
    }

    if (picks.length < 2) {
      for (final row in scored) {
        final name = ArtistMixEngine.canonicalName(row.entry.tag);
        if (used.contains(name)) continue;
        if (row.dist < StyleFingerprint.cloneDistance) continue;
        picks.add(
          PlannedPick(
            hit: row.toHit(),
            role: picks.isEmpty ? MixRole.lead : MixRole.support,
            reason: 'Fallback Solid+ style match that is not a clone.',
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
    if (!rank.isFinite) return -0.05;
    return (rank / (rank.abs() + 20.0)) * 0.10;
  }

  static _Scored? _firstUsable(List<_Scored> scored, Set<String> used) {
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.entry.strength == ArtistStrength.unknown && row.fit < 0.45) {
        continue;
      }
      return row;
    }
    return scored.cast<_Scored?>().firstWhere(
          (r) => !used.contains(ArtistMixEngine.canonicalName(r!.entry.tag)),
          orElse: () => null,
        );
  }

  static ArtistMixTriple? _bestTriple({
    required String? leadName,
    required StyleProfile query,
    required List<StyleFingerprintEntry> catalog,
    required List<ArtistMixTriple> triples,
  }) {
    if (leadName == null || triples.isEmpty) return null;
    final lead = ArtistMixEngine.canonicalName(leadName);
    final matching = triples
        .where(
          (t) => t.artists.any((a) => ArtistMixEngine.canonicalName(a) == lead),
        )
        .toList();
    if (matching.isEmpty) return null;
    final preferred = matching.where((t) => t.style == query.bucketHint).toList();
    final pool = preferred.isNotEmpty ? preferred : matching;

    ArtistMixTriple? best;
    var bestScore = -1.0;
    for (final triple in pool) {
      var sum = 0.0;
      var n = 0;
      for (final raw in triple.artists) {
        if (ArtistMixEngine.canonicalName(raw) == lead) continue;
        n++;
        final entry = _lookup(catalog, raw);
        sum += entry == null ? 0.40 : query.fitTo(entry);
      }
      var score = n == 0 ? 0.0 : sum / n;
      if (triple.style == query.bucketHint) score += 0.08;
      if (score > bestScore) {
        bestScore = score;
        best = triple;
      }
    }
    return best;
  }

  static _Scored? _pickSupport({
    required StyleProfile query,
    required List<_Scored> scored,
    required Set<String> used,
    required Set<String> glue,
    required Set<String> bucket,
    required StyleFingerprintEntry? lead,
  }) {
    _Scored? best;
    var bestScore = -1e9;
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.dist < StyleFingerprint.cloneDistance) continue;
      if (row.dist > _compatibleDistance) continue;
      if (lead != null &&
          StyleFingerprint.entryDistance(lead, row.entry) <
              StyleFingerprint.cloneDistance) {
        continue;
      }
      if (lead != null &&
          StyleFingerprint.entryDistance(lead, row.entry) < _minDiversity &&
          !glue.contains(name)) {
        continue;
      }
      var score = row.fit * 0.55 + row.total * 0.15;
      if (glue.contains(name)) score += 0.14;
      if (bucket.contains(name)) score += 0.08;
      if (row.entry.family == query.family) score += 0.04;
      if (best == null || score > bestScore) {
        best = row;
        bestScore = score;
      }
    }
    return best;
  }

  static _Scored? _pickAccent({
    required StyleProfile query,
    required List<_Scored> scored,
    required Set<String> used,
    required Set<String> glue,
    required StyleFingerprintEntry? lead,
  }) {
    _Scored? best;
    var bestScore = -1e9;
    for (final row in scored) {
      final name = ArtistMixEngine.canonicalName(row.entry.tag);
      if (used.contains(name)) continue;
      if (row.dist < StyleFingerprint.cloneDistance) continue;
      if (row.dist > _compatibleDistance + 0.6) continue;
      if (lead != null &&
          StyleFingerprint.entryDistance(lead, row.entry) <
              StyleFingerprint.cloneDistance) {
        continue;
      }
      var score = row.fit * 0.35 + _complement(query, row.entry);
      if (glue.contains(name)) score += 0.04;
      if (best == null || score > bestScore) {
        best = row;
        bestScore = score;
      }
    }
    return best;
  }

  /// Reward the opposite of the query's strongest axis.
  static double _complement(StyleProfile query, StyleFingerprintEntry entry) {
    final edge = entry.edge ?? query.edge;
    final sat = entry.satMean ?? query.satMean;
    if (query.edge >= 0.085 || query.strong >= 0.22) {
      return ((0.070 - edge) / StyleFingerprint.edgeScale).clamp(0.0, 4.0) * 0.08;
    }
    if (query.edge < 0.050) {
      return ((edge - 0.055) / StyleFingerprint.edgeScale).clamp(0.0, 4.0) * 0.08;
    }
    if (query.satMean >= 0.36) {
      return ((0.22 - sat) / StyleFingerprint.satScale).clamp(0.0, 4.0) * 0.07;
    }
    if (query.satMean < 0.13) {
      return ((sat - 0.18) / StyleFingerprint.satScale).clamp(0.0, 4.0) * 0.07;
    }
    return 0.0;
  }

  static String _accentReason(StyleProfile query, StyleFingerprintEntry entry) {
    if (query.edge >= 0.085 || query.strong >= 0.22) {
      return 'Softer accent (edge ${(entry.edge ?? 0).toStringAsFixed(2)}) '
          'so the mix does not go full graphic-novel.';
    }
    if (query.edge < 0.050) {
      return 'A bit more structure (edge ${(entry.edge ?? 0).toStringAsFixed(2)}) '
          'so the painterly lead still reads as a tag.';
    }
    if (query.satMean >= 0.36) {
      return 'More restrained color so the vibrant lead does not blow out.';
    }
    if (query.satMean < 0.13) {
      return 'A touch more chroma so the muted lead does not go grey.';
    }
    return 'Complementary rendering, not another neighbor of the lead.';
  }

  static StyleFingerprintEntry? _lookup(
    List<StyleFingerprintEntry> catalog,
    String name,
  ) {
    final key = ArtistMixEngine.canonicalName(name);
    for (final entry in catalog) {
      if (ArtistMixEngine.canonicalName(entry.tag) == key) return entry;
    }
    return null;
  }
}

class _Scored {
  const _Scored({
    required this.entry,
    required this.dist,
    required this.fit,
    required this.total,
  });

  final StyleFingerprintEntry entry;
  final double dist;
  final double fit;
  final double total;

  StyleMatchHit toHit() => StyleMatchHit(
        name: entry.tag,
        score: fit,
        source: StyleMatchSource.visual,
        naxScore: entry.naxScore,
        naxVotes: entry.naxVotes,
        detail: 'Δ ${dist.toStringAsFixed(2)}',
      );
}

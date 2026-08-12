import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/services.dart' show rootBundle;

import 'artist_strength.dart';

/// Quality-aware NovelAI artist mixer.
///
/// Why older mixers felt terrible: they shuffled top Danbooru post-count tags
/// into heavy `drawn by` stacks. Popularity ≠ style pull on NovelAI V4.5.
///
/// Algorithm:
/// 1. ~40% emit a curated seed triple (coolv3-style).
/// 2. Otherwise pick a style bucket, one lead + 2 supports from mid-count bands
///    with **nax.moe V4.5 style-pull** sampling (log(count) fallback when unrated)
///    and a glue/mixer bias. Weak-rated tags are demoted.
/// 3. Format mostly plain `artist:name` with mild optional lead emphasis.
class ArtistMixEngine {
  ArtistMixEngine._();

  static ArtistMixCatalog? _catalog;

  static final _drawnBy = RegExp(
    r'^\s*(?:artist\s*:|drawn\s+by\s+)\s*(.+?)\s*$',
    caseSensitive: false,
  );
  static final _weightUnwrap = RegExp(
    r'^\s*(-?\d+(?:\.\d+)?)\s*::\s*(.*?)\s*::\s*$',
  );
  static final _outerBraces = RegExp(r'^[\{\[]+(.*?)[\}\]]+$');

  /// Load bundled seed triples / glue / buckets (call once at startup or lazily).
  static Future<void> loadCatalog({
    String assetPath = 'assets/artist_mix/seed_mixes.json',
  }) async {
    if (_catalog != null) return;
    try {
      final raw = await rootBundle.loadString(assetPath);
      _catalog = ArtistMixCatalog.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      _catalog = ArtistMixCatalog.empty();
    }
  }

  /// Inject catalog for tests.
  static void debugSetCatalog(ArtistMixCatalog? catalog) {
    _catalog = catalog;
  }

  static ArtistMixCatalog get catalog => _catalog ?? ArtistMixCatalog.empty();

  /// Strip artist-like segments from [prompt], keeping characters / general tags.
  static String stripArtists(
    String prompt, {
    required Set<String> artistNames,
    Set<String> characterNames = const {},
  }) {
    final artists = _normalizeNameSet(artistNames);
    final characters = _normalizeNameSet(characterNames);
    final kept = <String>[];

    for (final segment in splitSegments(prompt)) {
      if (_isArtistSegment(segment, artists: artists, characters: characters)) {
        continue;
      }
      kept.add(segment.trim());
    }

    return kept.where((s) => s.isNotEmpty).join(', ');
  }

  /// Build a quality mix from scored artist candidates.
  static String buildMix(
    List<ArtistCandidate> candidates, {
    math.Random? random,
    ArtistMixCatalog? catalog,
  }) {
    final rng = random ?? math.Random();
    final cat = catalog ?? ArtistMixEngine.catalog;
    final byNorm = <String, ArtistCandidate>{};
    for (final c in candidates) {
      byNorm.putIfAbsent(canonicalName(c.name), () => c);
    }
    if (byNorm.isEmpty) return '';

    // Prefer known-good triples when available in the local DB.
    final usableTriples = cat.triples
        .where((t) => t.artists.every((a) => byNorm.containsKey(canonicalName(a))))
        .toList();
    if (usableTriples.isNotEmpty && rng.nextDouble() < 0.42) {
      final triple = usableTriples[rng.nextInt(usableTriples.length)];
      final names = triple.artists
          .map((a) => byNorm[canonicalName(a)]!.name)
          .toList();
      return _formatMix(names, random: rng, preferSeedStyle: true);
    }

    return _buildGenerativeMix(byNorm, cat, rng);
  }

  /// Replace existing artist tags with a new quality mix.
  static String replaceArtistsInPrompt(
    String prompt, {
    required List<ArtistCandidate> candidates,
    required Set<String> artistNames,
    Set<String> characterNames = const {},
    math.Random? random,
    ArtistMixCatalog? catalog,
  }) {
    final cleaned = stripArtists(
      prompt,
      artistNames: artistNames,
      characterNames: characterNames,
    );
    final mix = buildMix(candidates, random: random, catalog: catalog);
    if (mix.isEmpty) return cleaned;
    if (cleaned.isEmpty) return mix;
    return '$mix, $cleaned';
  }

  static String _buildGenerativeMix(
    Map<String, ArtistCandidate> byNorm,
    ArtistMixCatalog cat,
    math.Random rng,
  ) {
    final styles = cat.buckets.keys.toList();
    final style = styles.isEmpty ? null : styles[rng.nextInt(styles.length)];
    final bucket = style == null
        ? <String>{}
        : cat.buckets[style]!.map(canonicalName).toSet();

    bool inBucket(ArtistCandidate c) =>
        bucket.isEmpty || bucket.contains(canonicalName(c.name));

    final all = byNorm.values
        .where((c) => !_isBanned(c.name))
        .where((c) => c.strength != ArtistStrength.weak)
        .where((c) => c.count >= 150 || c.strengthRank.isFinite)
        .toList();
    if (all.isEmpty) return '';

    final glueNorm = cat.glue.map(canonicalName).toSet();
    // Prefer Solid+ artists when enough exist; otherwise keep mid-count bands.
    final ratedSolid = all
        .where((c) =>
            c.strength == ArtistStrength.strong ||
            c.strength == ArtistStrength.solid)
        .toList();
    final leadPool = _leadPool(all, ratedSolid).where(inBucket).toList();
    final leadFallback = _leadPool(all, ratedSolid);
    final supportPool = _supportPool(all, ratedSolid).where(inBucket).toList();
    final supportFallback = _supportPool(all, ratedSolid);
    final gluePool = all
        .where((c) => glueNorm.contains(canonicalName(c.name)))
        .where(inBucket)
        .toList();
    final glueFallback =
        all.where((c) => glueNorm.contains(canonicalName(c.name))).toList();

    ArtistCandidate? pickLead() {
      final roll = rng.nextDouble();
      if (roll < 0.45) {
        return _weightedPick(gluePool.isNotEmpty ? gluePool : glueFallback, rng);
      }
      if (roll < 0.85) {
        return _weightedPick(leadPool.isNotEmpty ? leadPool : leadFallback, rng);
      }
      return _weightedPick(leadFallback.isNotEmpty ? leadFallback : all, rng);
    }

    final lead = pickLead();
    if (lead == null) return '';

    final picked = <ArtistCandidate>[lead];
    final used = {canonicalName(lead.name)};
    final supportCount = rng.nextDouble() < 0.22 ? 3 : 2;

    ArtistCandidate? pickSupport({required bool preferGlue}) {
      Iterable<ArtistCandidate> source;
      if (preferGlue) {
        source = (gluePool.isNotEmpty ? gluePool : glueFallback)
            .where((c) => !used.contains(canonicalName(c.name)));
      } else {
        source = (supportPool.isNotEmpty ? supportPool : supportFallback)
            .where((c) => !used.contains(canonicalName(c.name)));
      }
      final list = source.toList();
      if (list.isEmpty) {
        final any = all.where((c) => !used.contains(canonicalName(c.name))).toList();
        return _weightedPick(any, rng);
      }
      return _weightedPick(list, rng);
    }

    // First support prefers a known "mixer"/glue artist.
    final first = pickSupport(preferGlue: true);
    if (first != null) {
      picked.add(first);
      used.add(canonicalName(first.name));
    }
    while (picked.length < supportCount + 1) {
      final next = pickSupport(preferGlue: false);
      if (next == null) break;
      picked.add(next);
      used.add(canonicalName(next.name));
    }

    // Avoid two mega-popular artists dominating the same mix.
    final mega = picked.where((c) => c.count > 3200).toList();
    if (mega.length > 1) {
      picked.removeWhere((c) => c.count > 3200 && c != mega.first);
      while (picked.length < 3) {
        final next = pickSupport(preferGlue: false);
        if (next == null) break;
        if (next.count > 3200) continue;
        picked.add(next);
        used.add(canonicalName(next.name));
      }
    }

    return _formatMix(picked.map((c) => c.name).toList(), random: rng);
  }

  /// V4+ numeric hierarchy: lead above 1.0, supports below 1.0.
  ///
  /// Prefer `1.1::artist:x::, 0.8::artist:y::` over `{…}` / `[…]` —
  /// braces/brackets only step by ×/÷1.05 and get hard to read.
  static String _formatMix(
    List<String> artists, {
    math.Random? random,
    bool preferSeedStyle = false,
  }) {
    if (artists.isEmpty) return '';
    final rng = random ?? math.Random();
    final names = artists.map(promptName).toList();

    // Mild recipe jitter so mixes don't all look identical.
    final lead = preferSeedStyle
        ? (rng.nextBool() ? 1.1 : 1.15)
        : (rng.nextDouble() < 0.35 ? 1.15 : 1.1);
    final support = rng.nextDouble() < 0.4 ? 0.85 : 0.8;
    final accent = rng.nextDouble() < 0.5 ? 0.7 : 0.75;

    final parts = <String>[];
    for (var i = 0; i < names.length; i++) {
      final weight = switch (i) {
        0 => lead,
        1 => support,
        _ => accent,
      };
      parts.add('${_formatWeight(weight)}::artist:${names[i]}::');
    }
    return parts.join(', ');
  }

  static String _formatWeight(double weight) {
    if (weight == weight.roundToDouble()) return weight.toInt().toString();
    final text = weight.toStringAsFixed(2);
    return text.replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '');
  }

  static List<ArtistCandidate> _leadPool(
    List<ArtistCandidate> all,
    List<ArtistCandidate> ratedSolid,
  ) {
    if (ratedSolid.length >= 12) {
      return ratedSolid
          .where((c) => c.count <= 8000)
          .toList();
    }
    return all.where((c) => c.count >= 250 && c.count <= 2800).toList();
  }

  static List<ArtistCandidate> _supportPool(
    List<ArtistCandidate> all,
    List<ArtistCandidate> ratedSolid,
  ) {
    if (ratedSolid.length >= 12) {
      return ratedSolid.where((c) => c.count <= 5000).toList();
    }
    return all.where((c) => c.count >= 150 && c.count <= 1600).toList();
  }

  static ArtistCandidate? _weightedPick(List<ArtistCandidate> pool, math.Random rng) {
    if (pool.isEmpty) return null;
    var total = 0.0;
    final weights = <double>[];
    for (final c in pool) {
      final w = artistSampleWeight(
        postCount: c.count,
        naxScore: c.naxScore,
        naxVotes: c.naxVotes,
      );
      weights.add(w);
      total += w;
    }
    var tick = rng.nextDouble() * total;
    for (var i = 0; i < pool.length; i++) {
      tick -= weights[i];
      if (tick <= 0) return pool[i];
    }
    return pool.last;
  }

  static bool _isBanned(String name) {
    final n = canonicalName(name);
    return n == 'banned_artist' || n == 'banned artist';
  }

  /// Name as typed into NovelAI prompts (unescape danbooru `\(`).
  static String promptName(String tag) {
    return tag
        .trim()
        .replaceAll(r'\(', '(')
        .replaceAll(r'\)', ')')
        .replaceAll('_', ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
  }

  static String canonicalName(String raw) {
    return promptName(raw).toLowerCase();
  }

  static Set<String> _normalizeNameSet(Set<String> names) =>
      names.map(canonicalName).where((n) => n.isNotEmpty).toSet();

  static List<String> splitSegments(String prompt) {
    final out = <String>[];
    final buf = StringBuffer();
    var braces = 0;
    var brackets = 0;
    var inNumeric = false;

    void flush() {
      final raw = buf.toString().trim();
      buf.clear();
      if (raw.isNotEmpty) out.add(raw);
    }

    for (var i = 0; i < prompt.length; i++) {
      final ch = prompt[i];
      if (ch == '\\' && i + 1 < prompt.length) {
        buf.write(prompt[i + 1]);
        i++;
        continue;
      }
      if (ch == '{' && !inNumeric) {
        braces++;
        buf.write(ch);
        continue;
      }
      if (ch == '}' && !inNumeric) {
        if (braces > 0) braces--;
        buf.write(ch);
        continue;
      }
      if (ch == '[' && !inNumeric) {
        brackets++;
        buf.write(ch);
        continue;
      }
      if (ch == ']' && !inNumeric) {
        if (brackets > 0) brackets--;
        buf.write(ch);
        continue;
      }
      if (ch == ':' && i + 1 < prompt.length && prompt[i + 1] == ':') {
        buf.write('::');
        i++;
        inNumeric = !inNumeric;
        continue;
      }
      if ((ch == ',' || ch == '\n') && braces == 0 && brackets == 0 && !inNumeric) {
        flush();
        continue;
      }
      buf.write(ch);
    }
    flush();
    return out;
  }

  static bool _isArtistSegment(
    String segment, {
    required Set<String> artists,
    required Set<String> characters,
  }) {
    final bare = _bareContent(segment);
    if (bare.isEmpty) return false;

    final members = bare
        .split(',')
        .map((m) => m.trim())
        .where((m) => m.isNotEmpty)
        .toList();
    if (members.length > 1) {
      var artistHits = 0;
      var characterHits = 0;
      for (final member in members) {
        final name = canonicalName(_stripDrawnBy(member));
        if (characters.contains(name)) characterHits++;
        if (artists.contains(name) || _looksDrawnBy(member)) artistHits++;
      }
      return artistHits > 0 &&
          artistHits >= characterHits &&
          artistHits >= (members.length / 2).ceil();
    }

    final single = _stripDrawnBy(bare);
    final name = canonicalName(single);
    if (name.isEmpty) return false;
    if (characters.contains(name) && !artists.contains(name)) return false;
    if (_looksDrawnBy(bare) || bare.toLowerCase().startsWith('artist:')) {
      return true;
    }
    return artists.contains(name);
  }

  static String _bareContent(String segment) {
    var text = segment.trim();
    final weight = _weightUnwrap.firstMatch(text);
    if (weight != null) text = weight.group(2)!.trim();
    while (true) {
      final m = _outerBraces.firstMatch(text);
      if (m == null) break;
      final inner = m.group(1)!.trim();
      if (inner.isEmpty || inner == text) break;
      text = inner;
    }
    return text.trim();
  }

  static String _stripDrawnBy(String text) {
    final m = _drawnBy.firstMatch(text.trim());
    if (m != null) return m.group(1)!.trim();
    var t = text.trim();
    if (t.toLowerCase().startsWith('artist:')) {
      t = t.substring(7).trim();
    }
    return t;
  }

  static bool _looksDrawnBy(String text) => _drawnBy.hasMatch(text.trim());
}

class ArtistCandidate {
  const ArtistCandidate({
    required this.name,
    required this.count,
    this.naxScore,
    this.naxVotes,
  });

  final String name;
  final int count;
  final int? naxScore;
  final int? naxVotes;

  ArtistStrength get strength => ArtistStrength.fromVotes(naxScore, naxVotes);
  double get strengthRank => strengthRankScore(naxScore, naxVotes);
}

class ArtistMixCatalog {
  const ArtistMixCatalog({
    required this.glue,
    required this.buckets,
    required this.triples,
  });

  final List<String> glue;
  final Map<String, List<String>> buckets;
  final List<ArtistMixTriple> triples;

  factory ArtistMixCatalog.empty() => const ArtistMixCatalog(
        glue: [],
        buckets: {},
        triples: [],
      );

  factory ArtistMixCatalog.fromJson(Map<String, dynamic> json) {
    final glue = (json['glue'] as List? ?? const [])
        .whereType<String>()
        .toList();
    final bucketsRaw = json['buckets'] as Map<String, dynamic>? ?? const {};
    final buckets = <String, List<String>>{};
    for (final entry in bucketsRaw.entries) {
      buckets[entry.key] = (entry.value as List? ?? const [])
          .whereType<String>()
          .toList();
    }
    final triples = (json['triples'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => ArtistMixTriple.fromJson(Map<String, dynamic>.from(e)))
        .where((t) => t.artists.length >= 2)
        .toList();
    return ArtistMixCatalog(glue: glue, buckets: buckets, triples: triples);
  }
}

class ArtistMixTriple {
  const ArtistMixTriple({required this.style, required this.artists});
  final String style;
  final List<String> artists;

  factory ArtistMixTriple.fromJson(Map<String, dynamic> json) => ArtistMixTriple(
        style: json['style'] as String? ?? 'anime',
        artists: (json['artists'] as List? ?? const [])
            .whereType<String>()
            .toList(),
      );
}

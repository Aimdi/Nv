import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:naiweaver/core/prompt/artist_mix_engine.dart';
import 'package:naiweaver/core/prompt/style_fingerprint.dart';
import 'package:naiweaver/core/prompt/style_mix_planner.dart';
import 'package:naiweaver/core/services/style_match_service.dart';

Uint8List _png(img.Image image) => Uint8List.fromList(img.encodePng(image));

/// High-edge, low-sat checkerboard — reads as sketchy / inked.
Uint8List _inkedPng() {
  final image = img.Image(width: 96, height: 96);
  for (var y = 0; y < 96; y++) {
    for (var x = 0; x < 96; x++) {
      final on = ((x ~/ 4) + (y ~/ 4)) % 2 == 0;
      final v = on ? 20 : 230;
      image.setPixelRgb(x, y, v, v, v);
    }
  }
  return _png(image);
}

/// Soft saturated wash — reads as painterly.
Uint8List _painterlyPng() {
  final image = img.Image(width: 96, height: 96);
  for (var y = 0; y < 96; y++) {
    for (var x = 0; x < 96; x++) {
      final t = y / 95.0;
      image.setPixelRgb(
        x,
        y,
        (255 - 40 * t).round(),
        (140 + 40 * t).round(),
        (80 + 80 * t).round(),
      );
    }
  }
  return _png(image);
}

/// Flat grey-green — muted, low edge.
Uint8List _mutedPng() {
  final image = img.Image(width: 96, height: 96);
  for (final p in image) {
    p.r = 118;
    p.g = 122;
    p.b = 120;
  }
  return _png(image);
}

StyleFingerprintEntry _entry(
  String tag,
  StyleProfile profile, {
  int score = 18,
  int votes = 40,
}) {
  return StyleFingerprintEntry(
    tag: tag,
    vector: profile.vector,
    naxScore: score,
    naxVotes: votes,
    edge: profile.edge,
    fine: profile.fine,
    strong: profile.strong,
    satMean: profile.satMean,
    satVar: profile.satVar,
    contrast: profile.contrast,
  );
}

Map<String, dynamic> _row(String tag, StyleProfile profile) => {
      'tag': tag,
      's': 20,
      'votes': 30,
      'v': profile.vector,
      'edge': profile.edge,
      'fine': profile.fine,
      'strong': profile.strong,
      'sat': profile.satMean,
      'satVar': profile.satVar,
      'contrast': profile.contrast,
    };

void main() {
  test('identical images have cosine ~ 1', () {
    final bytes = _inkedPng();
    final a = StyleFingerprint.compute(bytes);
    final b = StyleFingerprint.compute(bytes);
    expect(a, isNotNull);
    expect(StyleFingerprint.cosine(a!, b!), closeTo(1.0, 1e-6));
  });

  test('linework and painterly washes are different families', () {
    final inked = StyleFingerprint.analyze(_inkedPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final muted = StyleFingerprint.analyze(_mutedPng())!;
    expect(inked.family, 'sketchy');
    expect(paint.family, 'painterly');
    expect(muted.family, 'muted');
    expect(inked.edge, greaterThan(paint.edge));
    expect(inked.strong, greaterThan(paint.strong));
    expect(
      inked.distanceTo(_entry('paint', paint)),
      greaterThan(StyleFingerprint.cloneDistance),
    );
  });

  test('hue twins are closer than ink vs paint', () {
    Uint8List solid(int r, int g, int b) {
      final image = img.Image(width: 32, height: 32);
      for (final p in image) {
        p.r = r;
        p.g = g;
        p.b = b;
      }
      return _png(image);
    }

    final red = StyleFingerprint.analyze(solid(220, 40, 40))!;
    final blue = StyleFingerprint.analyze(solid(40, 60, 220))!;
    final inked = StyleFingerprint.analyze(_inkedPng())!;
    expect(
      red.distanceTo(_entry('blue', blue)),
      lessThan(inked.distanceTo(_entry('blue', blue))),
    );
  });

  test('extracts artist tags from a NovelAI-style prompt', () {
    const prompt =
        '1.1::artist:sciamano240::, 0.8::artist:xaxaxa::, 1girl, long hair';
    final hits = StyleMatchService.extractArtistsFromPrompt(prompt);
    expect(hits.map((h) => h.name.toLowerCase()), contains('sciamano240'));
    expect(hits.map((h) => h.name.toLowerCase()), contains('xaxaxa'));
    expect(hits.every((h) => h.source == StyleMatchSource.metadata), isTrue);
  });

  test('parseIqdb keeps high-score artist tags and drops banned posts', () {
    final hits = StyleMatchService.parseIqdb([
      {
        'score': 92.0,
        'post': {
          'tag_string_artist': 'ciloranko',
          'is_banned': false,
        },
      },
      {
        'score': 88.0,
        'post': {
          'tag_string_artist': 'banned_artist',
          'is_banned': true,
        },
      },
      {
        'score': 12.0,
        'post': {
          'tag_string_artist': 'noise_artist',
          'is_banned': false,
        },
      },
    ]);
    expect(hits.map((h) => h.name), ['ciloranko']);
    expect(hits.single.source, StyleMatchSource.iqdb);
  });

  test('ranked mix uses numeric emphasis, not braces', () {
    final mix = ArtistMixEngine.buildRankedMix(['sciamano240', 'xaxaxa', 'modare']);
    expect(mix, contains('1.1::artist:sciamano240::'));
    expect(mix, contains('artist:xaxaxa'));
    expect(mix.contains('{'), isFalse);
    expect(mix.contains('['), isFalse);
  });

  test('fingerprint index ranks the matching rendering first', () {
    final inked = StyleFingerprint.analyze(_inkedPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final index = StyleFingerprintIndex.fromJson({
      'artists': [_row('ink_artist', inked), _row('paint_artist', paint)],
    });
    final hits = index.query(inked, limit: 2);
    expect(hits.first.name, 'ink_artist');
    expect(hits.first.score, greaterThan(hits.last.score));
  });

  test('planner uses a source ID as lead, not the nearest clone', () {
    final query = StyleFingerprint.analyze(_inkedPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final catalog = [
      _entry('visual_twin', query),
      _entry('mixer_artist', paint, score: 12, votes: 20),
    ];
    final plan = StyleMixPlanner.plan(
      query: query,
      sourceHits: const [
        StyleMatchHit(
          name: 'named_in_png',
          score: 1.0,
          source: StyleMatchSource.metadata,
          naxScore: 18,
          naxVotes: 40,
        ),
      ],
      catalog: catalog,
      mixCatalog: const ArtistMixCatalog(
        glue: ['mixer_artist'],
        buckets: {
          'anime': ['mixer_artist'],
        },
        triples: [],
      ),
    );
    expect(plan.picks.first.name, 'named_in_png');
    expect(plan.picks.first.role, MixRole.lead);
    expect(plan.picks.any((p) => p.name == 'visual_twin'), isFalse);
    expect(plan.picks.any((p) => p.name == 'mixer_artist'), isTrue);
    expect(plan.mix, contains('1.1::artist:named in png::'));
    expect(plan.steps.any((s) => s.contains('source hit')), isTrue);
  });

  test('weak source hits do not steal the lead', () {
    final query = StyleFingerprint.analyze(_inkedPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final plan = StyleMixPlanner.plan(
      query: query,
      sourceHits: const [
        StyleMatchHit(
          name: 'weak_source',
          score: 1.0,
          source: StyleMatchSource.iqdb,
          naxScore: -5,
          naxVotes: 20,
        ),
      ],
      catalog: [
        _entry('ink_lead', query),
        _entry('paint_mixer', paint, score: 10, votes: 20),
      ],
      mixCatalog: const ArtistMixCatalog(
        glue: ['paint_mixer'],
        buckets: {
          'anime': ['paint_mixer'],
        },
        triples: [],
      ),
    );
    expect(plan.picks.first.name, 'ink_lead');
    expect(plan.picks.first.role, MixRole.lead);
    expect(plan.picks.any((p) => p.name == 'weak_source'), isFalse);
  });

  test('planner does not stack three near-clones as the mix', () {
    final query = StyleFingerprint.analyze(_inkedPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final muted = StyleFingerprint.analyze(_mutedPng())!;
    final plan = StyleMixPlanner.plan(
      query: query,
      sourceHits: const [],
      catalog: [
        _entry('clone_a', query),
        _entry('clone_b', query, score: 16, votes: 25),
        _entry('clone_c', query, score: 14, votes: 22),
        _entry('mixer_artist', paint, score: 12, votes: 20),
        _entry('accent_artist', muted, score: 10, votes: 18),
      ],
      mixCatalog: const ArtistMixCatalog(
        glue: ['mixer_artist'],
        buckets: {
          'anime': ['mixer_artist'],
        },
        triples: [],
      ),
    );
    final names = plan.picks.map((p) => p.name).toList();
    expect(names.first, 'clone_a');
    expect(names.where((n) => n.startsWith('clone_')).length, 1);
    expect(names, contains('mixer_artist'));
    expect(plan.picks.length, greaterThanOrEqualTo(2));
    expect(plan.picks.length, lessThanOrEqualTo(3));
  });

  test('known seed triple beats invented neighbors', () {
    final query = StyleFingerprint.analyze(_painterlyPng())!;
    final paint = StyleFingerprint.analyze(_painterlyPng())!;
    final inked = StyleFingerprint.analyze(_inkedPng())!;
    final muted = StyleFingerprint.analyze(_mutedPng())!;
    final plan = StyleMixPlanner.plan(
      query: query,
      sourceHits: const [
        StyleMatchHit(
          name: 'sciamano240',
          score: 1.0,
          source: StyleMatchSource.metadata,
          naxScore: 20,
          naxVotes: 40,
        ),
      ],
      catalog: [
        _entry('sciamano240', paint),
        _entry('random_neighbor', paint, score: 30, votes: 50),
        _entry('rei (sanbonzakura)', muted, score: 12, votes: 20),
        _entry('shimhaq', inked, score: 10, votes: 18),
      ],
      mixCatalog: const ArtistMixCatalog(
        glue: ['xaxaxa'],
        buckets: {
          'western': ['sciamano240', 'rei (sanbonzakura)', 'shimhaq'],
        },
        triples: [
          ArtistMixTriple(
            style: 'western',
            artists: ['sciamano240', 'rei (sanbonzakura)', 'shimhaq'],
          ),
        ],
      ),
    );
    final names = plan.picks.map((p) => ArtistMixEngine.canonicalName(p.name)).toList();
    expect(names.first, 'sciamano240');
    expect(names, contains('rei (sanbonzakura)'));
    expect(names, contains('shimhaq'));
    expect(names, isNot(contains('random neighbor')));
    expect(plan.steps.any((s) => s.contains('community-tested')), isTrue);
  });
}

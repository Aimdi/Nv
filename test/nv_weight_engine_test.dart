import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:naiweaver/core/prompt/artist_mix_engine.dart';
import 'package:naiweaver/core/prompt/artist_strength.dart';
import 'package:naiweaver/core/prompt/weight_engine.dart';
import 'package:naiweaver/core/services/nv_enrichment.dart';

void main() {
  group('WeightEngine', () {
    test('brace depth maps to 1.05 powers', () {
      expect(WeightEngine.braceDepthToWeight(0), 1.0);
      expect(WeightEngine.braceDepthToWeight(1), closeTo(1.05, 1e-9));
    });

    test('render applies artist prefix and spaces', () {
      final text = WeightEngine.render(
        [
          TagChip(tag: 'hammer_(sunset_beach)', kind: TagKind.artist, bracketCount: 1),
          TagChip(tag: '1girl'),
        ],
        options: const RenderOptions(
          underscoresToSpaces: true,
          artistPrefix: true,
        ),
      );
      expect(text, '{artist:hammer (sunset beach)}, 1girl');
    });
  });

  group('ArtistPreviewUrls', () {
    test('candidates try primary then fallback encodings', () {
      final urls = ArtistPreviewUrls.candidates('hammer_(sunset_beach)');
      expect(urls.length, greaterThanOrEqualTo(2));
      expect(urls.every((u) => u.endsWith('.jpg')), isTrue);
    });
  });

  group('ArtistMixEngine quality algo', () {
    late List<ArtistCandidate> candidates;
    late ArtistMixCatalog catalog;

    setUp(() {
      candidates = const [
        ArtistCandidate(name: 'xaxaxa', count: 196, naxScore: 20, naxVotes: 30),
        ArtistCandidate(name: 'zankuro', count: 818, naxScore: 18, naxVotes: 28),
        ArtistCandidate(name: 'stanley lau', count: 606, naxScore: 12, naxVotes: 22),
        ArtistCandidate(name: 'kedama milk', count: 629, naxScore: 16, naxVotes: 24),
        ArtistCandidate(name: 'modare', count: 810, naxScore: 14, naxVotes: 20),
        ArtistCandidate(name: 'ohisashiburi', count: 1479, naxScore: 10, naxVotes: 18),
        ArtistCandidate(name: 'ningen mame', count: 351, naxScore: 9, naxVotes: 16),
        ArtistCandidate(name: 'ateoyh', count: 565, naxScore: 8, naxVotes: 14),
        ArtistCandidate(name: 'memeh', count: 158, naxScore: 6, naxVotes: 12),
        ArtistCandidate(name: 'cyancapsule', count: 349, naxScore: 7, naxVotes: 15),
        ArtistCandidate(name: 'goto p', count: 736, naxScore: 11, naxVotes: 19),
        ArtistCandidate(name: 'shimhaq', count: 318, naxScore: 13, naxVotes: 21),
        ArtistCandidate(name: 'sciamano240', count: 899, naxScore: 102, naxVotes: 138),
        ArtistCandidate(name: r'rei \(sanbonzakura\)', count: 585, naxScore: 15, naxVotes: 25),
        ArtistCandidate(name: 'ebifurya', count: 5842, naxScore: -8, naxVotes: 20),
        ArtistCandidate(name: 'banned artist', count: 84474),
      ];
      catalog = ArtistMixCatalog.fromJson({
        'glue': ['xaxaxa', 'zankuro', 'stanley lau', 'kedama milk', 'goto p'],
        'buckets': {
          'anime': [
            'xaxaxa',
            'zankuro',
            'stanley lau',
            'modare',
            'ohisashiburi',
            'ningen mame',
            'ateoyh',
            'goto p',
          ],
          'cartoony': ['xaxaxa', 'kedama milk', 'zankuro', 'cyancapsule'],
          'western': ['sciamano240', r'rei \(sanbonzakura\)', 'shimhaq'],
        },
        'triples': [
          {
            'style': 'anime',
            'artists': ['modare', 'ohisashiburi', 'ningen mame'],
          },
          {
            'style': 'cartoony',
            'artists': ['kedama milk', 'xaxaxa', 'zankuro'],
          },
          {
            'style': 'western',
            'artists': ['sciamano240', r'rei \(sanbonzakura\)', 'shimhaq'],
          },
        ],
      });
      ArtistMixEngine.debugSetCatalog(catalog);
    });

    tearDown(() => ArtistMixEngine.debugSetCatalog(null));

    test('stripArtists keeps characters and removes artist stacks', () {
      const prompt =
          '1.3:: kana arima::, artist:modare, artist:ohisashiburi, [artist:ningen mame], 1girl';
      final cleaned = ArtistMixEngine.stripArtists(
        prompt,
        artistNames: candidates.map((c) => c.name).toSet(),
        characterNames: {'kana arima', 'kana_arima'},
      );
      expect(cleaned.toLowerCase(), contains('kana arima'));
      expect(cleaned.toLowerCase(), contains('1girl'));
      expect(cleaned.toLowerCase(), isNot(contains('modare')));
    });

    test('seed triple path emits curated three-artist artist: mix', () {
      // Force seed path with catalog that only has one triple usable.
      final forced = ArtistMixCatalog.fromJson({
        'glue': ['xaxaxa'],
        'buckets': {
          'anime': ['modare', 'ohisashiburi', 'ningen mame'],
        },
        'triples': [
          {
            'style': 'anime',
            'artists': ['modare', 'ohisashiburi', 'ningen mame'],
          },
        ],
      });
      // High seed probability: retry until we hit seed (deterministic Random).
      String? mix;
      for (var seed = 0; seed < 40; seed++) {
        mix = ArtistMixEngine.buildMix(
          candidates,
          random: Random(seed),
          catalog: forced,
        );
        if (mix.contains('modare') &&
            mix.contains('ohisashiburi') &&
            mix.contains('ningen mame')) {
          break;
        }
      }
      expect(mix, isNotNull);
      expect(mix!, contains('artist:'));
      expect(mix.toLowerCase(), contains('modare'));
      expect(RegExp(r'artist:').allMatches(mix).length, greaterThanOrEqualTo(2));
      // V4 numeric hierarchy, not brace/bracket spam or drawn-by stacks.
      expect(RegExp(r'\d+(?:\.\d+)?::artist:').hasMatch(mix), isTrue);
      expect(mix.contains('{'), isFalse);
      expect(mix.contains('['), isFalse);
      expect(mix.toLowerCase().contains('drawn by'), isFalse);
    });

    test('generative mixes stay small and avoid banned_artist', () {
      final emptySeeds = ArtistMixCatalog.fromJson({
        'glue': ['xaxaxa', 'zankuro', 'stanley lau', 'goto p'],
        'buckets': {
          'anime': [
            'xaxaxa',
            'zankuro',
            'stanley lau',
            'modare',
            'ohisashiburi',
            'ningen mame',
            'ateoyh',
            'goto p',
            'memeh',
          ],
        },
        'triples': <Map<String, dynamic>>[],
      });
      for (var seed = 0; seed < 20; seed++) {
        final mix = ArtistMixEngine.buildMix(
          candidates,
          random: Random(seed),
          catalog: emptySeeds,
        );
        expect(mix, isNotEmpty);
        expect(mix.toLowerCase(), isNot(contains('banned')));
        final artistCount = RegExp(r'artist:').allMatches(mix).length;
        expect(artistCount, inInclusiveRange(2, 5));
        expect(RegExp(r'\d+(?:\.\d+)?::artist:').allMatches(mix).length, artistCount);
        // Lead is mildly above 1; supports stay below 1. No brace leftovers.
        expect(RegExp(r'1\.(?:1|15)::artist:').hasMatch(mix), isTrue);
        expect(RegExp(r'0\.\d+::artist:').hasMatch(mix), isTrue);
        expect(mix.contains('{'), isFalse);
        expect(mix.contains('['), isFalse);
      }
    });

    test('replace keeps character and installs artist: lead mix', () {
      const prompt = '1.3:: kana arima::, 1girl, long hair';
      final next = ArtistMixEngine.replaceArtistsInPrompt(
        prompt,
        candidates: candidates,
        artistNames: candidates.map((c) => c.name).toSet(),
        characterNames: {'kana arima'},
        random: Random(11),
        catalog: catalog,
      );
      expect(next.toLowerCase(), contains('kana arima'));
      expect(next.toLowerCase(), contains('1girl'));
      expect(next.contains('artist:'), isTrue);
    });

    test('promptName unescapes danbooru parentheses', () {
      expect(
        ArtistMixEngine.promptName(r'rei \(sanbonzakura\)'),
        'rei (sanbonzakura)',
      );
    });

    test('generative mixes avoid weak-rated high-post artists', () {
      final emptySeeds = ArtistMixCatalog.fromJson({
        'glue': ['xaxaxa', 'zankuro'],
        'buckets': {
          'anime': ['xaxaxa', 'zankuro', 'modare', 'ohisashiburi', 'ningen mame'],
        },
        'triples': <Map<String, dynamic>>[],
      });
      for (var seed = 0; seed < 25; seed++) {
        final mix = ArtistMixEngine.buildMix(
          candidates,
          random: Random(seed),
          catalog: emptySeeds,
        );
        expect(mix.toLowerCase(), isNot(contains('ebifurya')));
      }
    });
  });

  group('ArtistStrength', () {
    test('tiers and bayesian rank prefer high-vote scores', () {
      expect(ArtistStrength.fromVotes(20, 25), ArtistStrength.strong);
      expect(ArtistStrength.fromVotes(-5, 8), ArtistStrength.weak);
      expect(ArtistStrength.fromVotes(100, 2), ArtistStrength.unknown);
      expect(
        strengthRankScore(5, 40),
        greaterThan(strengthRankScore(5, 3)),
      );
    });

    test('primary emphasis is above 1 and support is below 1', () {
      for (final tier in ArtistStrength.values) {
        expect(tier.primaryEmphasis, greaterThan(1.0));
        expect(tier.supportEmphasis, lessThan(1.0));
        expect(tier.supportEmphasis, greaterThan(0.0));
      }
      expect(ArtistStrength.strong.primaryEmphasis, 1.1);
      expect(ArtistStrength.solid.supportEmphasis, 0.8);
    });
  });
}

import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:naiweaver/core/prompt/artist_mix_engine.dart';
import 'package:naiweaver/core/prompt/weight_engine.dart';
import 'package:naiweaver/core/services/nv_enrichment.dart';

void main() {
  group('WeightEngine', () {
    test('brace depth maps to 1.05 powers', () {
      expect(WeightEngine.braceDepthToWeight(0), 1.0);
      expect(WeightEngine.braceDepthToWeight(1), closeTo(1.05, 1e-9));
      expect(WeightEngine.braceDepthToWeight(2), closeTo(1.1025, 1e-9));
      expect(WeightEngine.braceDepthToWeight(-2), closeTo(1 / (1.05 * 1.05), 1e-6));
    });

    test('weight to brace depth round trips', () {
      expect(WeightEngine.weightToBraceDepth(1.0), 0);
      expect(WeightEngine.weightToBraceDepth(1.05), 1);
      expect(WeightEngine.weightToBraceDepth(1.1025), 2);
      expect(WeightEngine.weightToBraceDepth(1 / 1.05), -1);
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

    test('numeric syntax round trips through parse', () {
      final rendered = WeightEngine.render([
        TagChip(tag: 'monochrome', numericWeight: 1.3),
      ]);
      expect(rendered.contains('1.3::'), isTrue);
      final parsed = WeightEngine.parse(rendered);
      expect(parsed, hasLength(1));
      expect(parsed.first.tag, 'monochrome');
      expect(parsed.first.numericWeight, closeTo(1.3, 1e-9));
    });

    test('double brackets parse to depth -2', () {
      final parsed = WeightEngine.parse('[[tag]]');
      expect(parsed, hasLength(1));
      expect(parsed.first.bracketCount, -2);
    });
  });

  group('ArtistPreviewUrls', () {
    test('candidates try primary then fallback encodings', () {
      final urls = ArtistPreviewUrls.candidates('hammer_(sunset_beach)');
      expect(urls.length, greaterThanOrEqualTo(2));
      expect(urls[0], contains('/images/1_10000/'));
      expect(urls.any((u) => u.contains('/images/2_5000/')), isTrue);
      expect(urls.every((u) => u.endsWith('.jpg')), isTrue);
    });
  });

  group('ArtistMixEngine', () {
    const artists = {
      'memeh',
      'pakosun',
      'ningen_mame',
      'modare',
      'ohisashiburi',
      'ateoyh',
      'rezodwel',
      'teddypocky',
      'midfinger',
      'snegovski',
      '96yottea',
      'akakura',
      'akai_sashimi',
      '7010',
    };
    const characters = {'kana_arima', 'chigusa_minori'};

    test('stripArtists removes artist stack but keeps character', () {
      const prompt =
          '1.3:: kana arima::, 1.2:: drawn by ateoyh::, {memeh}, [pakosun,ningen_mame], [modare], {ohisashiburi}, 1girl';
      final cleaned = ArtistMixEngine.stripArtists(
        prompt,
        artistNames: artists,
        characterNames: characters,
      );
      expect(cleaned.toLowerCase(), contains('kana arima'));
      expect(cleaned.toLowerCase(), contains('1girl'));
      expect(cleaned.toLowerCase(), isNot(contains('ateoyh')));
      expect(cleaned.toLowerCase(), isNot(contains('memeh')));
      expect(cleaned.toLowerCase(), isNot(contains('modare')));
    });

    test('stripArtists keeps weakened character weights', () {
      const prompt =
          '0.8::chigusa_minori::, {{memeh}}, [pakosun,ningen_mame], [modare], {ohisashiburi}';
      final cleaned = ArtistMixEngine.stripArtists(
        prompt,
        artistNames: artists,
        characterNames: characters,
      );
      expect(cleaned.toLowerCase(), contains('chigusa_minori'));
      expect(cleaned.toLowerCase(), isNot(contains('memeh')));
    });

    test('buildMix always emphasizes at least one artist', () {
      final mix = ArtistMixEngine.buildMix(
        artists.toList(),
        random: Random(7),
        minArtists: 5,
        maxArtists: 5,
      );
      expect(mix, isNotEmpty);
      final emphasized = mix.contains('::') ||
          mix.contains('{{') ||
          RegExp(r'\{[^{].*\}').hasMatch(mix);
      expect(emphasized, isTrue);
      expect(
        mix.contains('drawn by') || mix.contains('{') || mix.contains('['),
        isTrue,
      );
    });

    test('replaceArtistsInPrompt swaps previous mix and keeps character', () {
      const prompt =
          '1.3:: kana arima::, [teddypocky], {pakosun,ningen_mame}, [modare], {midfinger}';
      final next = ArtistMixEngine.replaceArtistsInPrompt(
        prompt,
        artistPool: artists.toList(),
        artistNames: artists,
        characterNames: characters,
        random: Random(3),
      );
      expect(next.toLowerCase(), contains('kana arima'));
      // Previous grouped/weak artists from the old stack are gone unless re-picked.
      expect(next.toLowerCase().contains('[pakosun,ningen_mame]'), isFalse);
      expect(next.contains('::') || next.contains('{{') || next.contains('{'), isTrue);
    });

    test('second replace removes the previous generated mix', () {
      const prompt = '1girl, long hair';
      final first = ArtistMixEngine.replaceArtistsInPrompt(
        prompt,
        artistPool: artists.toList(),
        artistNames: artists,
        characterNames: characters,
        random: Random(1),
      );
      final second = ArtistMixEngine.replaceArtistsInPrompt(
        first,
        artistPool: artists.toList(),
        artistNames: artists,
        characterNames: characters,
        random: Random(2),
      );
      expect(second.toLowerCase(), contains('1girl'));
      expect(second.toLowerCase(), contains('long hair'));
      // Should not stack two full mixes — strip happens first.
      final drawnByCount = RegExp(r'drawn by', caseSensitive: false).allMatches(second).length;
      expect(drawnByCount, lessThan(12));
    });
  });
}

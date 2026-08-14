import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:naiweaver/core/prompt/artist_mix_engine.dart';
import 'package:naiweaver/core/prompt/style_fingerprint.dart';
import 'package:naiweaver/core/services/style_match_service.dart';

Uint8List _solidPng({required int r, required int g, required int b}) {
  final image = img.Image(width: 32, height: 32);
  for (final p in image) {
    p.r = r;
    p.g = g;
    p.b = b;
  }
  return Uint8List.fromList(img.encodePng(image));
}

void main() {
  test('identical images have cosine ~ 1', () {
    final bytes = _solidPng(r: 220, g: 80, b: 90);
    final a = StyleFingerprint.compute(bytes);
    final b = StyleFingerprint.compute(bytes);
    expect(a, isNotNull);
    expect(StyleFingerprint.cosine(a!, b!), closeTo(1.0, 1e-6));
  });

  test('very different palettes are less similar than near twins', () {
    final red = StyleFingerprint.compute(_solidPng(r: 220, g: 40, b: 40))!;
    final red2 = StyleFingerprint.compute(_solidPng(r: 200, g: 50, b: 45))!;
    final blue = StyleFingerprint.compute(_solidPng(r: 40, g: 60, b: 220))!;
    expect(
      StyleFingerprint.cosine(red, red2),
      greaterThan(StyleFingerprint.cosine(red, blue)),
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

  test('fingerprint index ranks the matching artist first', () {
    final red = StyleFingerprint.compute(_solidPng(r: 210, g: 40, b: 40))!;
    final blue = StyleFingerprint.compute(_solidPng(r: 40, g: 50, b: 210))!;
    final index = StyleFingerprintIndex.fromJson({
      'artists': [
        {'tag': 'red_artist', 's': 20, 'votes': 30, 'v': red},
        {'tag': 'blue_artist', 's': 20, 'votes': 30, 'v': blue},
      ],
    });
    final hits = index.query(red, limit: 2);
    expect(hits.first.name, 'red_artist');
    expect(hits.first.score, greaterThan(hits.last.score));
  });
}

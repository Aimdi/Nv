import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:naiweaver/core/prompt/artist_mix_engine.dart';
import 'package:naiweaver/core/prompt/style_knowledge.dart';
import 'package:naiweaver/core/services/nax_strength_service.dart';
import 'package:naiweaver/core/services/nv_knowledge_api.dart';
import 'package:naiweaver/core/services/style_advisor_service.dart';
import 'package:naiweaver/core/services/style_match_service.dart';

Uint8List _png() {
  final image = img.Image(width: 32, height: 32);
  for (final p in image) {
    p.r = 200;
    p.g = 120;
    p.b = 80;
  }
  return Uint8List.fromList(img.encodePng(image));
}

void main() {
  setUp(() {
    NaxStrengthCatalog.debugSet(
      NaxStrengthCatalog.fromJson({
        'artists': {
          'sciamano240': {'s': 20, 'v': 40, 'u': 30, 'd': 10},
          'xaxaxa': {'s': 18, 'v': 30, 'u': 24, 'd': 6},
          'modare': {'s': 12, 'v': 20, 'u': 16, 'd': 4},
          'weak_tag': {'s': -5, 'v': 20, 'u': 5, 'd': 15},
        },
      }),
    );
    ArtistMixEngine.debugSetCatalog(
      const ArtistMixCatalog(
        glue: ['xaxaxa'],
        buckets: {
          'western': ['sciamano240', 'xaxaxa'],
        },
        triples: [
          ArtistMixTriple(
            style: 'western',
            artists: ['sciamano240', 'xaxaxa', 'modare'],
          ),
        ],
      ),
    );
    StyleFingerprintIndex.debugSet(StyleFingerprintIndex.fromJson({'artists': []}));
  });

  tearDown(() {
    NaxStrengthCatalog.debugSet(null);
    ArtistMixEngine.debugSetCatalog(null);
    StyleFingerprintIndex.debugSet(null);
  });

  test('knowledge payload includes rules, glue, and triples', () {
    final json = StyleKnowledge.catalogPayload();
    expect(json['rules'], contains(contains('nax.moe')));
    expect(json['glue'], contains('xaxaxa'));
    expect((json['triples'] as List).first['artists'], contains('sciamano240'));
    expect(json['emphasis']['form'], 'w::artist:name::');
  });

  test('brief from a report keeps the local mix and rules', () {
    const report = StyleMatchReport(
      sourceHits: [
        StyleMatchHit(
          name: 'sciamano240',
          score: 1,
          source: StyleMatchSource.metadata,
          naxScore: 20,
          naxVotes: 40,
        ),
      ],
      visualHits: [],
      mix: '1.1::artist:sciamano240::, 0.8::artist:xaxaxa::',
      notes: ['Lead is a source hit'],
    );
    final brief = StyleKnowledge.briefFromReport(report);
    expect(brief['rules'], isNotEmpty);
    expect(brief['local_plan']['mix'], contains('sciamano240'));
    expect(brief['source_hits'].first['name'], 'sciamano240');
  });

  test('advisor drops invented names and keeps grounded ones', () {
    const report = StyleMatchReport(
      sourceHits: [
        StyleMatchHit(
          name: 'sciamano240',
          score: 1,
          source: StyleMatchSource.metadata,
          naxScore: 20,
          naxVotes: 40,
        ),
      ],
      visualHits: [],
      mix: '1.1::artist:sciamano240::',
      notes: const [],
    );
    final advice = StyleAdvisorService.groundAdvice(
      {
        'picks': [
          {'name': 'sciamano240', 'role': 'lead', 'reason': 'source'},
          {'name': 'totally_fake_artist', 'role': 'support', 'reason': 'famous'},
          {'name': 'xaxaxa', 'role': 'support', 'reason': 'glue'},
        ],
        'notes': ['checked'],
      },
      report: report,
    );
    expect(advice.picks.map((p) => ArtistMixEngine.canonicalName(p.name)), [
      'sciamano240',
      'xaxaxa',
    ]);
    expect(advice.mix, contains('1.1::artist:sciamano240::'));
    expect(advice.mix.contains('{'), isFalse);
    expect(advice.notes.any((n) => n.contains('totally_fake_artist')), isTrue);
  });

  test('API health and artist search', () async {
    final api = NvKnowledgeApi();
    final health = await api.handle(
      const NvKnowledgeRequest(method: 'GET', path: '/v1/health'),
    );
    expect(health.status, 200);
    expect((health.body as Map)['ok'], true);

    final search = await api.handle(
      const NvKnowledgeRequest(
        method: 'GET',
        path: '/v1/artists',
        query: {'q': 'sciamano', 'limit': '5'},
      ),
    );
    expect(search.status, 200);
    final artists = (search.body as Map)['artists'] as List;
    expect(artists.first['tag'], 'sciamano240');
    expect(artists.first['strength'], 'Strong');
  });

  test('API knowledge and match without IQDB', () async {
    final api = NvKnowledgeApi();
    final knowledge = await api.handle(
      const NvKnowledgeRequest(method: 'GET', path: '/v1/knowledge'),
    );
    expect(knowledge.status, 200);
    expect((knowledge.body as Map)['rules'], isNotEmpty);

    final image = _png();
    final match = await api.handle(
      NvKnowledgeRequest(
        method: 'POST',
        path: '/v1/style/match',
        query: const {'iqdb': '0'},
        headers: const {'content-type': 'application/json'},
        body: Uint8List.fromList(
          utf8.encode(jsonEncode({'image_base64': base64Encode(image)})),
        ),
      ),
    );
    expect(match.status, 200);
    final body = match.body as Map<String, dynamic>;
    expect(body['brief']['rules'], isNotEmpty);
  });

  test('API advise without a key still returns the local brief', () async {
    final api = NvKnowledgeApi(xaiKeyProvider: () async => '');
    final image = _png();
    final res = await api.handle(
      NvKnowledgeRequest(
        method: 'POST',
        path: '/v1/style/advise',
        query: const {'iqdb': '0'},
        headers: const {'content-type': 'application/json'},
        body: Uint8List.fromList(
          utf8.encode(jsonEncode({'image_base64': base64Encode(image)})),
        ),
      ),
    );
    expect(res.status, 200);
    final body = res.body as Map<String, dynamic>;
    expect(body['advice'], isNull);
    expect(body['advice_error'], contains('xAI'));
    expect(body['brief']['rules'], isNotEmpty);
  });
}

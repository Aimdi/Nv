import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:image/image.dart' as img;

import '../prompt/artist_mix_engine.dart';
import '../prompt/artist_strength.dart';
import '../prompt/style_knowledge.dart';
import '../prompt/style_mix_planner.dart';
import 'nax_strength_service.dart';
import 'style_match_service.dart';

class StyleAdvice {
  const StyleAdvice({
    required this.mix,
    required this.picks,
    required this.notes,
    required this.model,
    required this.grounded,
  });

  final String mix;
  final List<PlannedPick> picks;
  final List<String> notes;
  final String model;
  final bool grounded;
}

/// Asks Grok (xAI) to refine a mix *using Nv's knowledge*, then grounds
/// every name against nax / source hits / glue / triples.
class StyleAdvisorService {
  StyleAdvisorService({Dio? dio, this.baseUrl = 'https://api.x.ai/v1'})
      : _dio = dio ?? Dio();

  final Dio _dio;
  final String baseUrl;

  static const defaultModel = 'grok-4.6';

  Future<StyleAdvice> advise({
    required StyleMatchReport report,
    required String apiKey,
    Uint8List? imageBytes,
    String model = defaultModel,
  }) async {
    if (apiKey.trim().isEmpty) {
      throw StateError('Set an xAI API key in Settings to ask Grok.');
    }
    await Future.wait([
      NaxStrengthCatalog.load(),
      ArtistMixEngine.loadCatalog(),
    ]);

    final brief = StyleKnowledge.briefFromReport(report);
    final messages = <Map<String, dynamic>>[
      {
        'role': 'system',
        'content': _systemPrompt,
      },
      {
        'role': 'user',
        'content': imageBytes == null
            ? jsonEncode(brief)
            : [
                {
                  'type': 'text',
                  'text':
                      'Here is Nv’s grounded brief. Refine the mix. '
                      'Stay inside the allowed names. Return JSON only.\n\n'
                      '${jsonEncode(brief)}',
                },
                {
                  'type': 'image_url',
                  'image_url': {
                    'url': 'data:image/jpeg;base64,${_jpegData(imageBytes)}',
                  },
                },
              ],
      },
    ];

    final response = await _dio.post<Map<String, dynamic>>(
      '$baseUrl/chat/completions',
      data: {
        'model': model,
        'temperature': 0.2,
        'messages': messages,
      },
      options: Options(
        responseType: ResponseType.json,
        headers: {
          'Authorization': 'Bearer ${apiKey.trim()}',
          'Content-Type': 'application/json',
        },
        sendTimeout: const Duration(seconds: 45),
        receiveTimeout: const Duration(seconds: 90),
      ),
    );

    final text = _messageText(response.data ?? const {});
    final parsed = parseAdviceJson(text);
    return groundAdvice(parsed, report: report, model: model);
  }

  static const _systemPrompt =
      'You are helping Nv, a NovelAI V4.5 frontend, pick artist tags.\n'
      'You MUST follow the rules in the JSON brief. Nv already read the '
      'image (ink / sat / contrast) and planned a lead / mixer / accent.\n'
      'Your job is to check that plan, use community triples when they fit, '
      'and only name artists that appear in local_plan, source_hits, '
      'also_considered, relevant_triples, or glue.\n'
      'Do not invent Danbooru-famous tags. Do not use {} or [].\n'
      'Reply with JSON only:\n'
      '{"picks":[{"name":"…","role":"lead|support|accent","reason":"…"}],'
      '"notes":["…"]}';

  static String _messageText(Map<String, dynamic> data) {
    final choices = data['choices'];
    if (choices is List && choices.isNotEmpty) {
      final first = choices.first;
      if (first is Map) {
        final message = first['message'];
        if (message is Map) {
          final content = message['content'];
          if (content is String) return content;
        }
      }
    }
    final output = data['output'];
    if (output is List && output.isNotEmpty) {
      return jsonEncode(output);
    }
    return jsonEncode(data);
  }

  static String _jpegData(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return base64Encode(bytes);
    final longest = decoded.width > decoded.height ? decoded.width : decoded.height;
    final scaled = longest > 768
        ? img.copyResize(
            decoded,
            width: decoded.width >= decoded.height ? 768 : null,
            height: decoded.height > decoded.width ? 768 : null,
          )
        : decoded;
    return base64Encode(img.encodeJpg(scaled, quality: 80));
  }

  /// Public for tests.
  static Map<String, dynamic> parseAdviceJson(String raw) {
    var text = raw.trim();
    final fence = RegExp(r'```(?:json)?\s*([\s\S]*?)```').firstMatch(text);
    if (fence != null) text = fence.group(1)!.trim();
    final start = text.indexOf('{');
    final end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      text = text.substring(start, end + 1);
    }
    final decoded = jsonDecode(text);
    if (decoded is Map<String, dynamic>) return decoded;
    if (decoded is Map) return Map<String, dynamic>.from(decoded);
    throw const FormatException('Grok did not return a JSON object.');
  }

  /// Drop invented names; re-emit numeric emphasis from grounded picks.
  static StyleAdvice groundAdvice(
    Map<String, dynamic> raw, {
    required StyleMatchReport report,
    String model = defaultModel,
  }) {
    final allowed = <String>{};
    void allow(String name) {
      final n = ArtistMixEngine.canonicalName(name);
      if (n.isNotEmpty) allowed.add(n);
    }

    for (final h in report.sourceHits) {
      allow(h.name);
    }
    for (final h in report.visualHits) {
      allow(h.name);
    }
    for (final p in report.plan?.picks ?? const <PlannedPick>[]) {
      allow(p.name);
    }
    for (final g in ArtistMixEngine.catalog.glue) {
      allow(g);
    }
    for (final t in ArtistMixEngine.catalog.triples) {
      for (final a in t.artists) {
        allow(a);
      }
    }

    final notes = <String>[];
    final rawNotes = raw['notes'];
    if (rawNotes is List) {
      notes.addAll(rawNotes.whereType<String>());
    }

    final picks = <PlannedPick>[];
    final used = <String>{};
    final rawPicks = raw['picks'];
    if (rawPicks is List) {
      for (final row in rawPicks) {
        if (row is! Map) continue;
        final data = Map<String, dynamic>.from(row);
        var name = (data['name'] as String? ?? '').trim();
        name = name.replaceFirst(RegExp(r'^artist\s*:\s*', caseSensitive: false), '');
        if (name.isEmpty) continue;
        final key = ArtistMixEngine.canonicalName(name);
        if (used.contains(key)) continue;
        if (!allowed.contains(key) && NaxStrengthCatalog.instance.lookup(name) == null) {
          notes.add('Dropped ungrounded artist "$name" — not in Nv’s catalog.');
          continue;
        }
        if (!allowed.contains(key)) {
          final nax = NaxStrengthCatalog.instance.lookup(name);
          if (nax == null || nax.strength == ArtistStrength.weak) {
            notes.add('Dropped "$name" — untested or Weak on V4.5.');
            continue;
          }
        }
        used.add(key);
        final role = switch ((data['role'] as String? ?? '').toLowerCase()) {
          'support' => MixRole.support,
          'accent' => MixRole.accent,
          _ => picks.isEmpty ? MixRole.lead : MixRole.support,
        };
        final nax = NaxStrengthCatalog.instance.lookup(name);
        picks.add(
          PlannedPick(
            hit: StyleMatchHit(
              name: name,
              score: 1.0,
              source: StyleMatchSource.visual,
              naxScore: nax?.score,
              naxVotes: nax?.votes,
              detail: 'grok',
            ),
            role: role,
            reason: (data['reason'] as String? ?? 'Grok pick, grounded in Nv catalog.')
                .trim(),
          ),
        );
        if (picks.length >= 3) break;
      }
    }

    if (picks.isEmpty && report.plan != null) {
      picks.addAll(report.plan!.picks);
      notes.add('Grok returned no grounded names; kept Nv’s local plan.');
    }

    final mix = ArtistMixEngine.buildRankedMix(picks.map((p) => p.name).toList());
    return StyleAdvice(
      mix: mix,
      picks: picks,
      notes: notes,
      model: model,
      grounded: picks.isNotEmpty,
    );
  }
}

import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:image/image.dart' as img;

import '../prompt/artist_mix_engine.dart';
import '../prompt/artist_strength.dart';
import '../prompt/style_fingerprint.dart';
import '../utils/image_utils.dart';
import 'nax_strength_service.dart';

enum StyleMatchSource { metadata, iqdb, visual }

class StyleMatchHit {
  const StyleMatchHit({
    required this.name,
    required this.score,
    required this.source,
    this.naxScore,
    this.naxVotes,
    this.detail,
  });

  final String name;
  final double score;
  final StyleMatchSource source;
  final int? naxScore;
  final int? naxVotes;
  final String? detail;

  ArtistStrength get strength => ArtistStrength.fromVotes(naxScore, naxVotes);
}

class StyleMatchReport {
  const StyleMatchReport({
    required this.sourceHits,
    required this.visualHits,
    required this.mix,
    required this.notes,
  });

  final List<StyleMatchHit> sourceHits;
  final List<StyleMatchHit> visualHits;
  final String mix;
  final List<String> notes;

  bool get isEmpty => sourceHits.isEmpty && visualHits.isEmpty && mix.isEmpty;
}

class StyleFingerprintIndex {
  StyleFingerprintIndex._(this.entries);

  final List<StyleFingerprintEntry> entries;

  static StyleFingerprintIndex? _instance;

  static StyleFingerprintIndex get instance =>
      _instance ?? StyleFingerprintIndex._(const []);

  static Future<void> load({
    String assetPath = 'assets/artist_mix/style_fingerprints_v45.json',
  }) async {
    if (_instance != null) return;
    try {
      final raw = await rootBundle.loadString(assetPath);
      _instance = StyleFingerprintIndex.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      _instance = StyleFingerprintIndex._(const []);
    }
  }

  static void debugSet(StyleFingerprintIndex? index) {
    _instance = index;
  }

  factory StyleFingerprintIndex.fromJson(Map<String, dynamic> json) {
    final rows = json['artists'] as List? ?? const [];
    final entries = <StyleFingerprintEntry>[];
    for (final row in rows) {
      if (row is! Map) continue;
      final data = Map<String, dynamic>.from(row);
      final tag = (data['tag'] as String? ?? '').trim();
      final vec = (data['v'] as List? ?? const [])
          .whereType<num>()
          .map((n) => n.toDouble())
          .toList();
      if (tag.isEmpty || vec.length < 8) continue;
      entries.add(
        StyleFingerprintEntry(
          tag: tag,
          vector: StyleFingerprint.l2Normalize(vec),
          naxScore: (data['s'] as num?)?.toInt(),
          naxVotes: (data['votes'] as num?)?.toInt(),
        ),
      );
    }
    return StyleFingerprintIndex._(entries);
  }

  int get size => entries.length;

  List<StyleMatchHit> query(List<double> vector, {int limit = 12}) {
    final scored = <StyleMatchHit>[];
    for (final entry in entries) {
      final cos = StyleFingerprint.cosine(vector, entry.vector);
      scored.add(
        StyleMatchHit(
          name: entry.tag,
          score: cos,
          source: StyleMatchSource.visual,
          naxScore: entry.naxScore,
          naxVotes: entry.naxVotes,
        ),
      );
    }
    scored.sort((a, b) => b.score.compareTo(a.score));
    return scored.take(limit).toList();
  }
}

class StyleFingerprintEntry {
  const StyleFingerprintEntry({
    required this.tag,
    required this.vector,
    this.naxScore,
    this.naxVotes,
  });

  final String tag;
  final List<double> vector;
  final int? naxScore;
  final int? naxVotes;
}

/// Hybrid: NAI PNG artists + Danbooru IQDB + visual NN on nax V4.5 previews.
class StyleMatchService {
  StyleMatchService({Dio? dio}) : _dio = dio ?? Dio();

  final Dio _dio;

  static const _userAgent = 'Nv/1.0.7 (https://github.com/Aimdi/Nv; style-from-image)';

  Future<StyleMatchReport> match(Uint8List bytes) async {
    await Future.wait([
      StyleFingerprintIndex.load(),
      NaxStrengthCatalog.load(),
      ArtistMixEngine.loadCatalog(),
    ]);

    final notes = <String>[];
    final sourceHits = <StyleMatchHit>[];

    final fromMeta = extractArtistsFromNovelAiPng(bytes);
    if (fromMeta.isNotEmpty) {
      sourceHits.addAll(fromMeta);
      notes.add('Found artist tags in the image metadata (NovelAI PNG).');
    }

    try {
      final iqdb = await queryIqdb(bytes);
      for (final hit in iqdb) {
        if (sourceHits.any((h) => _sameArtist(h.name, hit.name))) continue;
        sourceHits.add(hit);
      }
      if (iqdb.isNotEmpty) {
        notes.add('Danbooru reverse search found a similar posted image.');
      }
    } catch (_) {
      notes.add('Danbooru reverse search was unavailable.');
    }

    final visualHits = <StyleMatchHit>[];
    final fp = StyleFingerprint.compute(bytes);
    if (fp != null && StyleFingerprintIndex.instance.size > 0) {
      visualHits.addAll(
        StyleFingerprintIndex.instance.query(fp, limit: 16).where((h) {
          if (h.strength == ArtistStrength.weak) return false;
          return h.score >= 0.55;
        }),
      );
      if (visualHits.isEmpty) {
        notes.add(
          'No close visual match in the V4.5 preview index. '
          'This is a look-alike guess, not a source ID.',
        );
        visualHits.addAll(
          StyleFingerprintIndex.instance.query(fp, limit: 8).where(
                (h) => h.strength != ArtistStrength.weak,
              ),
        );
      } else {
        notes.add(
          'Visual matches compare your image to nax.moe V4.5 artist previews '
          '(same character, different style). Strong = similar rendering, '
          'not “this artist drew it”.',
        );
      }
    } else if (fp == null) {
      notes.add('Could not decode the image for visual matching.');
    }

    final mix = ArtistMixEngine.buildRankedMix(_pickMixNames(sourceHits, visualHits));
    if (mix.isEmpty) {
      notes.add('Not enough artist signal to build a mix.');
    }

    return StyleMatchReport(
      sourceHits: sourceHits,
      visualHits: visualHits,
      mix: mix,
      notes: notes,
    );
  }

  List<String> _pickMixNames(
    List<StyleMatchHit> sourceHits,
    List<StyleMatchHit> visualHits,
  ) {
    final names = <String>[];
    void add(String raw) {
      final n = ArtistMixEngine.canonicalName(raw);
      if (n.isEmpty || n == 'banned artist' || n == 'banned_artist') return;
      if (names.any((e) => ArtistMixEngine.canonicalName(e) == n)) return;
      names.add(raw);
    }

    // Prefer a Solid+ source artist as lead when IQDB/metadata actually hit.
    for (final hit in sourceHits) {
      if (hit.strength == ArtistStrength.weak) continue;
      add(hit.name);
      if (names.length >= 1) break;
    }
    for (final hit in visualHits) {
      add(hit.name);
      if (names.length >= 3) break;
    }
    if (names.length < 2) {
      for (final hit in sourceHits) {
        add(hit.name);
        if (names.length >= 3) break;
      }
    }
    return names.take(3).toList();
  }

  /// Public so tests can feed fixtures without hitting the network.
  Future<List<StyleMatchHit>> queryIqdb(Uint8List bytes) async {
    final form = FormData.fromMap({
      'search[file]': MultipartFile.fromBytes(
        _downscaleForSearch(bytes),
        filename: 'query.jpg',
      ),
      'limit': 8,
    });
    final response = await _dio.post<List<dynamic>>(
      'https://danbooru.donmai.us/iqdb_queries.json',
      data: form,
      options: Options(
        responseType: ResponseType.json,
        headers: {'User-Agent': _userAgent, 'Accept': 'application/json'},
        sendTimeout: const Duration(seconds: 20),
        receiveTimeout: const Duration(seconds: 20),
      ),
    );
    return parseIqdb(response.data ?? const []);
  }

  static List<StyleMatchHit> parseIqdb(List<dynamic> rows) {
    final hits = <StyleMatchHit>[];
    for (final row in rows) {
      if (row is! Map) continue;
      final data = Map<String, dynamic>.from(row);
      final score = (data['score'] as num?)?.toDouble() ?? 0;
      if (score < 50) continue;
      final post = data['post'];
      if (post is! Map) continue;
      final postMap = Map<String, dynamic>.from(post);
      if (postMap['is_banned'] == true) continue;
      final artists = (postMap['tag_string_artist'] as String? ?? '')
          .split(RegExp(r'\s+'))
          .where((s) => s.isNotEmpty)
          .toList();
      for (final artist in artists) {
        final nax = NaxStrengthCatalog.instance.lookup(artist);
        hits.add(
          StyleMatchHit(
            name: artist,
            score: score / 100.0,
            source: StyleMatchSource.iqdb,
            naxScore: nax?.score,
            naxVotes: nax?.votes,
            detail: score >= 80 ? 'Likely same post' : 'Possible similar post',
          ),
        );
      }
    }
    return hits;
  }

  static List<StyleMatchHit> extractArtistsFromNovelAiPng(Uint8List bytes) {
    final meta = extractMetadata(bytes);
    if (meta == null) return const [];
    String? prompt;
    if (meta['Comment'] != null) {
      final json = parseCommentJson(meta['Comment']!);
      prompt = (json?['prompt'] as String?) ??
          (json?['original_prompt'] as String?);
      prompt ??= json?['v4_prompt']?['caption']?['base_caption'] as String?;
    }
    prompt ??= meta['Description'];
    if (prompt == null || prompt.isEmpty) return const [];
    return extractArtistsFromPrompt(prompt);
  }

  static List<StyleMatchHit> extractArtistsFromPrompt(String prompt) {
    final hits = <StyleMatchHit>[];
    for (final segment in ArtistMixEngine.splitSegments(prompt)) {
      var text = segment.trim();
      final weight = RegExp(r'^-?\d+(?:\.\d+)?::\s*(.*?)\s*::$').firstMatch(text);
      if (weight != null) text = weight.group(1)!.trim();
      text = text.replaceAll(RegExp(r'^[\{\[]+|[\}\]]+$'), '').trim();
      final drawn = RegExp(
        r'^(?:artist\s*:|drawn\s+by\s+)\s*(.+)$',
        caseSensitive: false,
      ).firstMatch(text);
      if (drawn == null && !text.toLowerCase().startsWith('artist:')) {
        continue;
      }
      var name = drawn?.group(1) ?? text.substring(text.indexOf(':') + 1);
      name = name.trim();
      if (name.isEmpty) continue;
      final nax = NaxStrengthCatalog.instance.lookup(name);
      hits.add(
        StyleMatchHit(
          name: name,
          score: 1.0,
          source: StyleMatchSource.metadata,
          naxScore: nax?.score,
          naxVotes: nax?.votes,
          detail: 'From image prompt metadata',
        ),
      );
    }
    return hits;
  }

  static Uint8List _downscaleForSearch(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return bytes;
    final longest = decoded.width > decoded.height ? decoded.width : decoded.height;
    final scaled = longest > 512
        ? img.copyResize(
            decoded,
            width: decoded.width > decoded.height ? 512 : null,
            height: decoded.height >= decoded.width ? 512 : null,
          )
        : decoded;
    return Uint8List.fromList(img.encodeJpg(scaled, quality: 85));
  }

  static bool _sameArtist(String a, String b) =>
      ArtistMixEngine.canonicalName(a) == ArtistMixEngine.canonicalName(b);
}

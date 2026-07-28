import 'dart:convert';

import 'package:dio/dio.dart';

/// Live Danbooru autocomplete (reads only).
class DanbooruAutocompleteItem {
  const DanbooruAutocompleteItem({
    required this.value,
    required this.category,
    required this.postCount,
    this.label,
    this.antecedent,
  });

  final String? label;
  final String value;
  final int category;
  final int postCount;
  final String? antecedent;

  bool get isArtist => category == categoryArtist;
  String get tagName => value.trim();

  static const categoryGeneral = 0;
  static const categoryArtist = 1;
  static const categoryCopyright = 3;
  static const categoryCharacter = 4;
  static const categoryMeta = 5;

  factory DanbooruAutocompleteItem.fromJson(Map<String, dynamic> json) {
    return DanbooruAutocompleteItem(
      label: json['label'] as String?,
      value: json['value'] as String? ?? '',
      category: json['category'] as int? ?? 0,
      postCount: json['post_count'] as int? ?? 0,
      antecedent: json['antecedent'] as String?,
    );
  }
}

class DanbooruAutocompleteService {
  DanbooruAutocompleteService({Dio? dio}) : _dio = dio ?? Dio();

  static const baseUrl = 'https://danbooru.donmai.us';
  static const minQueryLength = 2;
  static const cacheTtl = Duration(minutes: 5);

  final Dio _dio;
  final Map<String, _CacheEntry> _cache = {};
  DateTime _backoffUntil = DateTime.fromMillisecondsSinceEpoch(0);

  Future<List<DanbooruAutocompleteItem>> autocomplete(
    String query, {
    int limit = 20,
    bool allowNsfwMeta = false,
  }) async {
    final normalized = query.trim().toLowerCase();
    if (normalized.length < minQueryLength) return const [];

    final now = DateTime.now();
    if (now.isBefore(_backoffUntil)) {
      return _filterNsfw(_cachedOrEmpty(normalized), allowNsfwMeta);
    }

    final cached = _cache[normalized];
    if (cached != null && now.difference(cached.storedAt) < cacheTtl) {
      return _filterNsfw(cached.items, allowNsfwMeta);
    }

    try {
      final response = await _dio.get<List<dynamic>>(
        '$baseUrl/autocomplete.json',
        queryParameters: {
          'search[query]': normalized,
          'search[type]': 'tag_query',
          'version': 1,
          'limit': limit,
        },
        options: Options(responseType: ResponseType.json),
      );
      final items = (response.data ?? const [])
          .whereType<Map>()
          .map((e) => DanbooruAutocompleteItem.fromJson(
                Map<String, dynamic>.from(e),
              ))
          .toList();
      _cache[normalized] = _CacheEntry(items, now);
      return _filterNsfw(items, allowNsfwMeta);
    } on DioException catch (e) {
      if (e.response?.statusCode == 429) {
        final retry = int.tryParse(e.response?.headers.value('retry-after') ?? '');
        _backoffUntil = now.add(Duration(seconds: (retry ?? 2).clamp(1, 30)));
      }
      return _filterNsfw(_cachedOrEmpty(normalized), allowNsfwMeta);
    } catch (_) {
      return _filterNsfw(_cachedOrEmpty(normalized), allowNsfwMeta);
    }
  }

  List<DanbooruAutocompleteItem> _cachedOrEmpty(String key) =>
      _cache[key]?.items ?? const [];

  List<DanbooruAutocompleteItem> _filterNsfw(
    List<DanbooruAutocompleteItem> items,
    bool allowNsfwMeta,
  ) {
    if (allowNsfwMeta) return items;
    return items.where((item) {
      final name = item.tagName.toLowerCase();
      if (!name.startsWith('rating:')) return true;
      return name == 'rating:general' || name == 'rating:g';
    }).toList();
  }
}

class _CacheEntry {
  _CacheEntry(this.items, this.storedAt);
  final List<DanbooruAutocompleteItem> items;
  final DateTime storedAt;
}

/// HuggingFace NovelAI v3 artist-comparison preview URLs.
class ArtistPreviewUrls {
  static const dataset = 'deus-ex-machina/novelai-anime-v3-artist-comparison';
  static const _base =
      'https://huggingface.co/datasets/$dataset/resolve/main/images';
  static const folderPrimary = '1_10000';
  static const folderFallback = '2_5000';

  static String primary(String name) => _url(folderPrimary, name);
  static String fallback(String name) => _url(folderFallback, name);
  static List<String> candidates(String name) => [primary(name), fallback(name)];

  static String _url(String folder, String name) {
    final encoded = Uri.encodeComponent(name);
    return '$_base/$folder/$encoded.jpg';
  }
}

/// Decode a tiny artists seed list (id/name/post_count) from JSON.
class ArtistSeed {
  const ArtistSeed({required this.id, required this.name, required this.postCount});
  final int id;
  final String name;
  final int postCount;
  String get displayName => name.replaceAll('_', ' ');

  factory ArtistSeed.fromJson(Map<String, dynamic> json) => ArtistSeed(
        id: json['id'] as int? ?? 0,
        name: json['name'] as String? ?? '',
        postCount: json['post_count'] as int? ?? 0,
      );

  static List<ArtistSeed> decodeList(String raw) {
    final decoded = jsonDecode(raw);
    if (decoded is! List) return const [];
    return decoded
        .whereType<Map>()
        .map((e) => ArtistSeed.fromJson(Map<String, dynamic>.from(e)))
        .where((a) => a.name.isNotEmpty)
        .toList();
  }
}

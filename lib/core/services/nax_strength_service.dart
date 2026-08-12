import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../prompt/artist_strength.dart';

/// Bundled nax.moe V4.5 artist style-pull votes.
class NaxStrengthEntry {
  const NaxStrengthEntry({
    required this.score,
    required this.up,
    required this.down,
    required this.votes,
  });

  final int score;
  final int up;
  final int down;
  final int votes;

  ArtistStrength get strength => ArtistStrength.fromVotes(score, votes);
  double get rankScore => strengthRankScore(score, votes);
}

class NaxStrengthCatalog {
  NaxStrengthCatalog._(this._byTag);

  final Map<String, NaxStrengthEntry> _byTag;

  static NaxStrengthCatalog? _instance;

  static NaxStrengthCatalog get instance =>
      _instance ?? NaxStrengthCatalog._(const {});

  static Future<void> load({
    String assetPath = 'assets/artist_mix/nax_v45_strength.json',
  }) async {
    if (_instance != null) return;
    try {
      final raw = await rootBundle.loadString(assetPath);
      _instance = NaxStrengthCatalog.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      _instance = NaxStrengthCatalog._(const {});
    }
  }

  /// Inject catalog for tests.
  static void debugSet(NaxStrengthCatalog? catalog) {
    _instance = catalog;
  }

  factory NaxStrengthCatalog.fromJson(Map<String, dynamic> json) {
    final artists = json['artists'] as Map<String, dynamic>? ?? const {};
    final map = <String, NaxStrengthEntry>{};
    for (final entry in artists.entries) {
      final value = entry.value;
      if (value is! Map) continue;
      final data = Map<String, dynamic>.from(value);
      final up = (data['u'] as num?)?.toInt() ?? 0;
      final down = (data['d'] as num?)?.toInt() ?? 0;
      final votes = (data['v'] as num?)?.toInt() ?? (up + down);
      final score = (data['s'] as num?)?.toInt() ?? (up - down);
      if (votes <= 0) continue;
      map[normalizeTag(entry.key)] = NaxStrengthEntry(
        score: score,
        up: up,
        down: down,
        votes: votes,
      );
    }
    return NaxStrengthCatalog._(map);
  }

  int get size => _byTag.length;

  NaxStrengthEntry? lookup(String tag) => _byTag[normalizeTag(tag)];

  static String normalizeTag(String tag) {
    return tag
        .trim()
        .replaceAll(r'\(', '(')
        .replaceAll(r'\)', ')')
        .replaceAll(' ', '_')
        .toLowerCase();
  }
}

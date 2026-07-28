/// NovelAI weighting helpers ported from the Aimdi/Nv Kotlin WeightEngine.
///
/// Rules (docs.novelai.net Strengthening/Weakening):
/// - Each `{…}` multiplies attention by 1.05; each `[…]` divides by 1.05.
/// - `w::tag::` applies a numeric weight to the enclosed span.
/// - Underscores become spaces on export when that setting is on.
/// - Artist tags take an `artist:` prefix on V4+.
library;

import 'dart:math' as math;

enum TagKind { general, artist, character, copyright, meta }

enum NovelAiModel {
  v3,
  v4,
  v45;

  bool get supportsArtistPrefix => this != NovelAiModel.v3;
  bool get supportsNegativeWeights => this == NovelAiModel.v45;
}

class TagChip {
  TagChip({
    String? id,
    required this.tag,
    this.kind = TagKind.general,
    this.bracketCount = 0,
    this.numericWeight,
    this.enabled = true,
  }) : id = id ?? UniqueKeyLike.generate();

  final String id;
  final String tag;
  final TagKind kind;
  final int bracketCount;
  final double? numericWeight;
  final bool enabled;

  static const maxBracketCount = 10;
  static const braceFactor = 1.05;

  TagChip copyWith({
    String? tag,
    TagKind? kind,
    int? bracketCount,
    double? numericWeight,
    bool clearNumericWeight = false,
    bool? enabled,
  }) {
    return TagChip(
      id: id,
      tag: tag ?? this.tag,
      kind: kind ?? this.kind,
      bracketCount: bracketCount ?? this.bracketCount,
      numericWeight:
          clearNumericWeight ? null : (numericWeight ?? this.numericWeight),
      enabled: enabled ?? this.enabled,
    );
  }
}

/// Tiny id helper without depending on Flutter.
class UniqueKeyLike {
  static int _n = 0;
  static String generate() {
    _n += 1;
    return 'chip_${DateTime.now().microsecondsSinceEpoch}_$_n';
  }
}

class RenderOptions {
  const RenderOptions({
    this.model = NovelAiModel.v45,
    this.underscoresToSpaces = true,
    this.artistPrefix = true,
    this.separator = ', ',
  });

  final NovelAiModel model;
  final bool underscoresToSpaces;
  final bool artistPrefix;
  final String separator;
}

class WeightEngine {
  static const braceFactor = 1.05;

  static int weightToBraceDepth(double weight) {
    if (weight <= 0 || weight.isNaN || weight.isInfinite) return 0;
    if ((weight - 1.0).abs() < 1e-9) return 0;
    final depth = (math.log(weight) / math.log(braceFactor)).round();
    return depth.clamp(-TagChip.maxBracketCount, TagChip.maxBracketCount);
  }

  static double braceDepthToWeight(int depth) {
    if (depth == 0) return 1.0;
    var result = 1.0;
    final levels = depth.abs();
    for (var i = 0; i < levels; i++) {
      result = depth > 0 ? result * braceFactor : result / braceFactor;
    }
    return result;
  }

  static String wrapWithBraces(String text, int depth) {
    if (depth == 0) return text;
    final open = depth > 0 ? '{' : '[';
    final close = depth > 0 ? '}' : ']';
    final levels = depth.abs();
    return '${open * levels}$text${close * levels}';
  }

  static String wrapNumeric(String text, double weight) {
    if ((weight - 1.0).abs() < 1e-9) return text;
    return '${_formatWeight(weight)}::$text::';
  }

  static String render(
    List<TagChip> entries, {
    RenderOptions options = const RenderOptions(),
  }) {
    final pieces = <String>[];
    for (final entry in entries) {
      if (!entry.enabled) continue;
      final rendered = renderEntry(entry, options: options);
      if (rendered != null && rendered.isNotEmpty) pieces.add(rendered);
    }
    return pieces.join(options.separator);
  }

  static String? renderEntry(
    TagChip entry, {
    RenderOptions options = const RenderOptions(),
  }) {
    var text = entry.tag.trim();
    if (text.isEmpty) return null;
    if (options.underscoresToSpaces) {
      text = text.replaceAll('_', ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
    }
    if (entry.kind == TagKind.artist &&
        options.artistPrefix &&
        options.model.supportsArtistPrefix &&
        !text.toLowerCase().startsWith('artist:')) {
      text = 'artist:$text';
    }
    text = wrapWithBraces(text, entry.bracketCount);
    final weight = entry.numericWeight;
    if (weight != null) {
      text = wrapNumeric(text, weight);
    }
    return text;
  }

  /// Lightweight parser for comma-separated NovelAI prompts into chips.
  static List<TagChip> parse(String prompt) {
    final entries = <TagChip>[];
    final buffer = StringBuffer();
    var braces = 0;
    var brackets = 0;
    final numericScopes = <_NumericScope>[];

    double? currentWeight() {
      if (numericScopes.isEmpty) return null;
      return numericScopes.fold<double>(1.0, (acc, s) => acc * s.weight);
    }

    void flush() {
      final raw = buffer.toString().trim();
      buffer.clear();
      if (raw.isEmpty) return;
      entries.add(_buildEntry(raw, braces - brackets, currentWeight()));
    }

    var i = 0;
    while (i < prompt.length) {
      final ch = prompt[i];
      if (ch == '\\' && i + 1 < prompt.length) {
        buffer.write(prompt[i + 1]);
        i += 2;
        continue;
      }
      switch (ch) {
        case '{':
          flush();
          braces++;
        case '}':
          flush();
          if (braces > 0) braces--;
        case '[':
          flush();
          brackets++;
        case ']':
          flush();
          if (brackets > 0) brackets--;
        case ':':
          if (i + 1 < prompt.length && prompt[i + 1] == ':') {
            i++;
            final opening = _takeOpeningWeight(buffer);
            if (opening != null) {
              flush();
              numericScopes.add(_NumericScope(opening, braces, brackets));
            } else {
              flush();
              if (numericScopes.isNotEmpty) {
                final scope = numericScopes.removeLast();
                braces = scope.bracesAtEntry;
                brackets = scope.bracketsAtEntry;
              }
            }
          } else {
            buffer.write(ch);
          }
        case ',':
        case '\n':
          flush();
        default:
          buffer.write(ch);
      }
      i++;
    }
    flush();
    return entries;
  }

  static TagChip _buildEntry(String rawTag, int bracketCount, double? weight) {
    const prefix = 'artist:';
    final isArtist = rawTag.toLowerCase().startsWith(prefix);
    final tag = isArtist ? rawTag.substring(prefix.length).trim() : rawTag;
    return TagChip(
      tag: tag,
      kind: isArtist ? TagKind.artist : TagKind.general,
      bracketCount: bracketCount.clamp(-TagChip.maxBracketCount, TagChip.maxBracketCount),
      numericWeight: weight,
    );
  }

  static double? _takeOpeningWeight(StringBuffer buffer) {
    final text = buffer.toString();
    final match = RegExp(r'(-?\d+(?:\.\d+)?)$').firstMatch(text);
    if (match == null) return null;
    final remainder = text.substring(0, match.start);
    final standalone = remainder.isEmpty ||
        remainder.endsWith(' ') ||
        remainder.endsWith(',') ||
        remainder.endsWith('\n');
    if (!standalone) return null;
    final weight = double.tryParse(match.group(1)!);
    if (weight == null) return null;
    buffer
      ..clear()
      ..write(remainder);
    return weight;
  }

  static String _formatWeight(double weight) {
    if (weight == weight.roundToDouble()) return weight.toInt().toString();
    return weight.toStringAsFixed(3).replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '');
  }
}

class _NumericScope {
  _NumericScope(this.weight, this.bracesAtEntry, this.bracketsAtEntry);
  final double weight;
  final int bracesAtEntry;
  final int bracketsAtEntry;
}

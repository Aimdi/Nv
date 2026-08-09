import 'dart:math';

/// Builds and replaces NovelAI-style artist stacks used by the Aimdi random
/// mix button — the brace / `drawn by` / numeric style people paste by hand.
///
/// Example outputs:
/// - `1.3:: drawn by ateoyh::, {memeh}, [pakosun,ningen_mame], [modare], {ohisashiburi}`
/// - `{drawn by 96yottea}, [drawn by akakura], drawn by akai sashimi, [7010]`
class ArtistMixEngine {
  ArtistMixEngine._();

  static final _drawnBy = RegExp(
    r'^\s*(?:artist\s*:|drawn\s+by\s+)\s*(.+?)\s*$',
    caseSensitive: false,
  );
  static final _weightUnwrap = RegExp(
    r'^\s*(-?\d+(?:\.\d+)?)\s*::\s*(.*?)\s*::\s*$',
  );
  static final _outerBraces = RegExp(r'^[\{\[]+(.*?)[\}\]]+$');

  /// Strip artist-like segments from [prompt], keeping characters / general tags.
  static String stripArtists(
    String prompt, {
    required Set<String> artistNames,
    Set<String> characterNames = const {},
  }) {
    final artists = _normalizeNameSet(artistNames);
    final characters = _normalizeNameSet(characterNames);
    final kept = <String>[];

    for (final segment in splitSegments(prompt)) {
      if (_isArtistSegment(segment, artists: artists, characters: characters)) {
        continue;
      }
      kept.add(segment.trim());
    }

    return kept.where((s) => s.isNotEmpty).join(', ');
  }

  /// Build a fresh weighted artist mix. Always emphasizes at least one artist.
  static String buildMix(
    List<String> artistTags, {
    Random? random,
    int minArtists = 4,
    int maxArtists = 7,
  }) {
    if (artistTags.isEmpty) return '';
    final rng = random ?? Random();
    final want = (minArtists + rng.nextInt(maxArtists - minArtists + 1))
        .clamp(1, artistTags.length);
    final pool = List<String>.of(artistTags)..shuffle(rng);
    final picked = pool.take(want).map(_displayName).toList();
    return _renderRecipe(picked, rng);
  }

  /// Replace existing artist tags with a new mix; leave characters alone.
  static String replaceArtistsInPrompt(
    String prompt, {
    required List<String> artistPool,
    required Set<String> artistNames,
    Set<String> characterNames = const {},
    Random? random,
  }) {
    final cleaned = stripArtists(
      prompt,
      artistNames: artistNames,
      characterNames: characterNames,
    );
    final mix = buildMix(artistPool, random: random);
    if (mix.isEmpty) return cleaned;
    if (cleaned.isEmpty) return mix;
    // Artists early = stronger NovelAI influence; characters/other tags follow.
    return '$mix, $cleaned';
  }

  /// Split a prompt into top-level comma segments (respects `{}` `[]` `n:: ::`).
  static List<String> splitSegments(String prompt) {
    final out = <String>[];
    final buf = StringBuffer();
    var braces = 0;
    var brackets = 0;
    var inNumeric = false;

    void flush() {
      final raw = buf.toString().trim();
      buf.clear();
      if (raw.isNotEmpty) out.add(raw);
    }

    for (var i = 0; i < prompt.length; i++) {
      final ch = prompt[i];
      if (ch == '\\' && i + 1 < prompt.length) {
        buf.write(prompt[i + 1]);
        i++;
        continue;
      }
      if (ch == '{' && !inNumeric) {
        braces++;
        buf.write(ch);
        continue;
      }
      if (ch == '}' && !inNumeric) {
        if (braces > 0) braces--;
        buf.write(ch);
        continue;
      }
      if (ch == '[' && !inNumeric) {
        brackets++;
        buf.write(ch);
        continue;
      }
      if (ch == ']' && !inNumeric) {
        if (brackets > 0) brackets--;
        buf.write(ch);
        continue;
      }
      if (ch == ':' && i + 1 < prompt.length && prompt[i + 1] == ':') {
        buf.write('::');
        i++;
        inNumeric = !inNumeric;
        continue;
      }
      if ((ch == ',' || ch == '\n') && braces == 0 && brackets == 0 && !inNumeric) {
        flush();
        continue;
      }
      buf.write(ch);
    }
    flush();
    return out;
  }

  static bool _isArtistSegment(
    String segment, {
    required Set<String> artists,
    required Set<String> characters,
  }) {
    final bare = _bareContent(segment);
    if (bare.isEmpty) return false;

    // Grouped weaken: [pakosun,ningen_mame]
    final members = bare.split(',').map((m) => m.trim()).where((m) => m.isNotEmpty);
    final memberList = members.toList();
    if (memberList.length > 1) {
      var artistHits = 0;
      var characterHits = 0;
      for (final member in memberList) {
        final name = _canonicalName(_stripDrawnBy(member));
        if (characters.contains(name)) characterHits++;
        if (artists.contains(name) || _looksDrawnBy(member)) artistHits++;
      }
      // Treat as artist group if mostly artists and not mostly characters.
      return artistHits > 0 && artistHits >= characterHits && artistHits >= (memberList.length / 2).ceil();
    }

    final single = _stripDrawnBy(bare);
    final name = _canonicalName(single);
    if (name.isEmpty) return false;
    if (characters.contains(name) && !artists.contains(name)) return false;
    if (_looksDrawnBy(bare) || bare.toLowerCase().startsWith('artist:')) return true;
    return artists.contains(name);
  }

  static String _bareContent(String segment) {
    var text = segment.trim();
    final weight = _weightUnwrap.firstMatch(text);
    if (weight != null) {
      text = weight.group(2)!.trim();
    }
    // Peel matching outer brace/bracket layers.
    while (true) {
      final m = _outerBraces.firstMatch(text);
      if (m == null) break;
      final inner = m.group(1)!.trim();
      if (inner.isEmpty || inner == text) break;
      text = inner;
    }
    return text.trim();
  }

  static String _stripDrawnBy(String text) {
    final m = _drawnBy.firstMatch(text.trim());
    if (m != null) return m.group(1)!.trim();
    return text.trim();
  }

  static bool _looksDrawnBy(String text) => _drawnBy.hasMatch(text.trim());

  static String _canonicalName(String raw) {
    return raw
        .trim()
        .toLowerCase()
        .replaceAll('artist:', '')
        .replaceAll(RegExp(r'\s+'), '_')
        .replaceAll(RegExp(r'^_+|_+$'), '');
  }

  static Set<String> _normalizeNameSet(Set<String> names) {
    return names.map(_canonicalName).where((n) => n.isNotEmpty).toSet();
  }

  static String _displayName(String tag) {
    return tag.trim().replaceAll('_', ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  /// Underscore form sometimes looks more "danbooru" inside groups.
  static String _danbooruName(String tag) {
    return tag.trim().replaceAll(' ', '_');
  }

  static String _renderRecipe(List<String> artists, Random rng) {
    if (artists.isEmpty) return '';
    if (artists.length == 1) {
      return _emphasize(artists.first, rng);
    }

    final recipe = rng.nextInt(4);
    switch (recipe) {
      case 0:
        return _recipeLeadDrawnBy(artists, rng);
      case 1:
        return _recipeDoubleEmphasis(artists, rng);
      case 2:
        return _recipeAllDrawnBy(artists, rng);
      default:
        return _recipeMixedWeights(artists, rng);
    }
  }

  /// `1.3:: drawn by A::, {B}, [C,D], [E], {F}`
  static String _recipeLeadDrawnBy(List<String> artists, Random rng) {
    final parts = <String>[];
    parts.add(_numericDrawnBy(artists.first, _strongWeight(rng)));
    var i = 1;
    if (i < artists.length && rng.nextBool()) {
      parts.add('{${artists[i]}}');
      i++;
    }
    if (i + 1 < artists.length && rng.nextBool()) {
      parts.add('[${_danbooruName(artists[i])},${_danbooruName(artists[i + 1])}]');
      i += 2;
    }
    while (i < artists.length) {
      parts.add(_weakOrBrace(artists[i], rng));
      i++;
    }
    return parts.join(', ');
  }

  /// `{{A}}, [B,C], [D], {E}` with guaranteed emphasis
  static String _recipeDoubleEmphasis(List<String> artists, Random rng) {
    final parts = <String>[];
    parts.add('{{${artists.first}}}');
    var i = 1;
    if (i + 1 < artists.length) {
      parts.add('[${_danbooruName(artists[i])},${_danbooruName(artists[i + 1])}]');
      i += 2;
    }
    while (i < artists.length) {
      parts.add(_weakOrBrace(artists[i], rng));
      i++;
    }
    return parts.join(', ');
  }

  /// `{drawn by A}, [drawn by B], drawn by C, drawn by D`
  static String _recipeAllDrawnBy(List<String> artists, Random rng) {
    final parts = <String>[];
    for (var i = 0; i < artists.length; i++) {
      final name = artists[i];
      if (i == 0) {
        parts.add(rng.nextBool()
            ? _numericDrawnBy(name, _strongWeight(rng))
            : '{drawn by $name}');
      } else if (i == 1 && rng.nextBool()) {
        parts.add('[drawn by $name]');
      } else if (rng.nextDouble() < 0.25) {
        parts.add('[$name]');
      } else {
        parts.add('drawn by $name');
      }
    }
    return parts.join(', ');
  }

  /// `1.4:: drawn by A::, 1.2:: B::, [C], [D], {E}`
  static String _recipeMixedWeights(List<String> artists, Random rng) {
    final parts = <String>[];
    parts.add(_numericDrawnBy(artists.first, _strongWeight(rng)));
    var i = 1;
    if (i < artists.length) {
      parts.add('${_midWeight(rng)}:: ${artists[i]}::');
      i++;
    }
    while (i < artists.length) {
      parts.add(_weakOrBrace(artists[i], rng));
      i++;
    }
    return parts.join(', ');
  }

  static String _emphasize(String name, Random rng) {
    if (rng.nextBool()) return _numericDrawnBy(name, _strongWeight(rng));
    return '{{$name}}';
  }

  static String _numericDrawnBy(String name, double weight) {
    final w = weight == weight.roundToDouble()
        ? weight.toInt().toString()
        : weight.toStringAsFixed(1);
    return '$w:: drawn by $name::';
  }

  static String _weakOrBrace(String name, Random rng) {
    final roll = rng.nextDouble();
    if (roll < 0.35) return '[$name]';
    if (roll < 0.7) return '{${_danbooruName(name)}}';
    if (roll < 0.85) return '{$name}';
    return '[${_danbooruName(name)}]';
  }

  static double _strongWeight(Random rng) {
    const options = [1.2, 1.3, 1.4];
    return options[rng.nextInt(options.length)];
  }

  static double _midWeight(Random rng) {
    const options = [1.1, 1.2, 1.3];
    return options[rng.nextInt(options.length)];
  }
}

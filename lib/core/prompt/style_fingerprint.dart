import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;

import 'artist_strength.dart';

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

class StyleFingerprintEntry {
  const StyleFingerprintEntry({
    required this.tag,
    required this.vector,
    this.naxScore,
    this.naxVotes,
    this.edge,
    this.satMean,
    this.contrast,
  });

  final String tag;
  final List<double> vector;
  final int? naxScore;
  final int? naxVotes;
  final double? edge;
  final double? satMean;
  final double? contrast;

  ArtistStrength get strength => ArtistStrength.fromVotes(naxScore, naxVotes);
}

/// How an image is *rendered*, not what is in it.
///
/// Hue / mean RGB are dropped on purpose: nax V4.5 previews all show the same
/// orange-hoodie girl, so matching those channels just finds “similar clothes.”
class StyleProfile {
  const StyleProfile({
    required this.vector,
    required this.satMean,
    required this.contrast,
    required this.edge,
    required this.colorfulness,
    required this.warmth,
    required this.brightness,
  });

  /// L2-normalized style-only vector used for nearest-neighbor.
  final List<double> vector;
  final double satMean;
  final double contrast;
  final double edge;
  final double colorfulness;
  final double warmth;
  final double brightness;

  String get family {
    if (edge >= 0.11 && satMean < 0.32) return 'sketchy';
    if (edge >= 0.10 && contrast >= 0.16) return 'cel-shaded';
    if (edge < 0.075 && satMean >= 0.28) return 'painterly';
    if (satMean < 0.22) return 'muted';
    if (satMean >= 0.48) return 'vibrant';
    return 'balanced';
  }

  /// Hint for seed_mixes buckets.
  String get bucketHint {
    if (family == 'muted' || (warmth < 0.35 && satMean < 0.30)) return 'western';
    if (family == 'cel-shaded' || family == 'sketchy') return 'anime';
    if (family == 'vibrant' && edge >= 0.08) return 'cartoony';
    if (family == 'painterly') return 'western';
    return 'anime';
  }

  String get summary {
    final tone = warmth >= 0.58
        ? 'warm'
        : warmth <= 0.42
            ? 'cool'
            : 'neutral';
    final lines = edge >= 0.11
        ? 'crisp linework'
        : edge <= 0.07
            ? 'soft edges'
            : 'moderate linework';
    return '$family, $tone grade, $lines';
  }

  List<String> get observations {
    final out = <String>[
      'Saturation ${satMean.toStringAsFixed(2)} — '
          '${satMean >= 0.45 ? 'punchy color' : satMean <= 0.25 ? 'restrained / greyed' : 'moderate color'}',
      'Contrast ${contrast.toStringAsFixed(2)} — '
          '${contrast >= 0.20 ? 'hard lighting / graphic' : contrast <= 0.10 ? 'flat / even' : 'natural range'}',
      'Line density ${edge.toStringAsFixed(2)} — '
          '${edge >= 0.11 ? 'inked / cel' : edge <= 0.07 ? 'painted / blended' : 'mixed'}',
    ];
    return out;
  }
}

/// Style-only fingerprint: saturation/value shape + contrast + line density.
class StyleFingerprint {
  static const size = 64;
  static const satBins = 6;
  static const valBins = 6;
  static const dimensions = satBins + valBins + 4;

  StyleFingerprint._();

  static StyleProfile? analyze(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return null;
    final small = img.copyResize(
      decoded,
      width: size,
      height: size,
      interpolation: img.Interpolation.average,
    );
    return fromImage(small);
  }

  /// Back-compat for tests that still call [compute].
  static List<double>? compute(Uint8List bytes) => analyze(bytes)?.vector;

  static StyleProfile fromImage(img.Image image) {
    final satHist = List<double>.filled(satBins, 0);
    final valHist = List<double>.filled(valBins, 0);
    var sumS = 0.0, sumV = 0.0, sumV2 = 0.0, sumWarm = 0.0, sumChroma = 0.0;
    var edge = 0.0;
    var count = 0;

    final w = image.width;
    final h = image.height;
    final gray = List<double>.filled(w * h, 0);

    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final p = image.getPixel(x, y);
        final r = p.r / 255.0;
        final g = p.g / 255.0;
        final b = p.b / 255.0;
        final hsv = rgbToHsv(r, g, b);
        satHist[_bin(hsv[1], satBins)] += 1;
        valHist[_bin(hsv[2], valBins)] += 1;
        sumS += hsv[1];
        sumV += hsv[2];
        sumV2 += hsv[2] * hsv[2];
        sumChroma += hsv[1] * hsv[2];
        // Warmth: red-yellow vs blue-cyan, ignoring near-greys.
        if (hsv[1] > 0.12) {
          final hue = hsv[0];
          final warm = hue < 70 || hue > 320 ? 1.0 : (hue > 160 && hue < 260 ? 0.0 : 0.5);
          sumWarm += warm * hsv[1];
        }
        gray[y * w + x] = 0.299 * r + 0.587 * g + 0.114 * b;
        count++;
      }
    }

    final n = math.max(1, count).toDouble();
    for (var y = 0; y < h - 1; y++) {
      for (var x = 0; x < w - 1; x++) {
        final i = y * w + x;
        final dx = gray[i + 1] - gray[i];
        final dy = gray[i + w] - gray[i];
        edge += math.sqrt(dx * dx + dy * dy);
      }
    }
    final edgeCount = math.max(1, (w - 1) * (h - 1));
    final satMean = sumS / n;
    final brightness = sumV / n;
    final contrast = math.sqrt(math.max(0.0, sumV2 / n - brightness * brightness));
    final colorfulness = sumChroma / n;
    final warmth = (sumS < 1e-6) ? 0.5 : (sumWarm / sumS).clamp(0.0, 1.0);
    final edgeMean = edge / edgeCount;

    final raw = <double>[
      ...satHist.map((v) => v / n),
      ...valHist.map((v) => v / n),
      satMean,
      contrast,
      edgeMean,
      colorfulness,
    ];

    return StyleProfile(
      vector: l2Normalize(raw),
      satMean: satMean,
      contrast: contrast,
      edge: edgeMean,
      colorfulness: colorfulness,
      warmth: warmth,
      brightness: brightness,
    );
  }

  static List<double> rgbToHsv(double r, double g, double b) {
    final max = math.max(r, math.max(g, b));
    final min = math.min(r, math.min(g, b));
    final delta = max - min;
    var h = 0.0;
    if (delta > 1e-9) {
      if (max == r) {
        h = 60.0 * (((g - b) / delta) % 6.0);
      } else if (max == g) {
        h = 60.0 * (((b - r) / delta) + 2.0);
      } else {
        h = 60.0 * (((r - g) / delta) + 4.0);
      }
    }
    if (h < 0) h += 360.0;
    final s = max <= 1e-9 ? 0.0 : delta / max;
    return [h, s, max];
  }

  static int _bin(double t, int bins) {
    final i = (t.clamp(0.0, 0.999999) * bins).floor();
    return i.clamp(0, bins - 1);
  }

  static List<double> l2Normalize(List<double> v) {
    var sum = 0.0;
    for (final x in v) {
      sum += x * x;
    }
    final norm = math.sqrt(sum);
    if (norm < 1e-12) return List<double>.from(v);
    return [for (final x in v) x / norm];
  }

  static double cosine(List<double> a, List<double> b) {
    final n = math.min(a.length, b.length);
    var dot = 0.0;
    for (var i = 0; i < n; i++) {
      dot += a[i] * b[i];
    }
    return dot;
  }
}

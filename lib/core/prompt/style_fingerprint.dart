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
    this.fine,
    this.strong,
    this.satMean,
    this.satVar,
    this.contrast,
  });

  final String tag;
  final List<double> vector;
  final int? naxScore;
  final int? naxVotes;
  final double? edge;
  final double? fine;
  final double? strong;
  final double? satMean;
  final double? satVar;
  final double? contrast;

  ArtistStrength get strength => ArtistStrength.fromVotes(naxScore, naxVotes);

  String get family => StyleFingerprint.familyOf(
        edge: edge ?? 0,
        strong: strong ?? 0,
        satMean: satMean ?? 0,
        contrast: contrast ?? 0,
      );
}

/// How an image is *rendered*, not what is in it.
///
/// Matching uses z-distance on ink / paint / sat scalars. A concatenated
/// sat/val histogram of the nax hoodie scene collapses to cosine ≈ 0.99
/// across the whole catalog, so it is not used for ranking.
class StyleProfile {
  const StyleProfile({
    required this.vector,
    required this.satMean,
    required this.satVar,
    required this.contrast,
    required this.edge,
    required this.fine,
    required this.strong,
    required this.colorfulness,
    required this.warmth,
    required this.brightness,
  });

  /// Scaled, L2-normalized signature (tests / cosine fallback).
  final List<double> vector;
  final double satMean;
  final double satVar;
  final double contrast;
  final double edge;
  final double fine;
  final double strong;
  final double colorfulness;
  final double warmth;
  final double brightness;

  String get family => StyleFingerprint.familyOf(
        edge: edge,
        strong: strong,
        satMean: satMean,
        contrast: contrast,
      );

  /// Hint for seed_mixes buckets.
  String get bucketHint {
    if (family == 'muted' || (warmth < 0.35 && satMean < 0.30)) return 'western';
    if (family == 'cel-shaded' || family == 'sketchy') return 'anime';
    if (family == 'vibrant' && edge >= 0.07) return 'cartoony';
    if (family == 'painterly') return 'western';
    return 'anime';
  }

  String get summary {
    final tone = warmth >= 0.58
        ? 'warm'
        : warmth <= 0.42
            ? 'cool'
            : 'neutral';
    final lines = (edge >= 0.085 || strong >= 0.22)
        ? 'crisp linework'
        : edge <= 0.050
            ? 'soft edges'
            : 'moderate linework';
    return '$family, $tone grade, $lines';
  }

  List<String> get observations {
    return [
      'Saturation ${satMean.toStringAsFixed(2)} — '
          '${satMean >= 0.36 ? 'punchy color' : satMean <= 0.13 ? 'restrained / greyed' : 'moderate color'}',
      'Contrast ${contrast.toStringAsFixed(2)} — '
          '${contrast >= 0.30 ? 'hard lighting / graphic' : contrast <= 0.16 ? 'flat / even' : 'natural range'}',
      'Line density ${edge.toStringAsFixed(2)} (ink ${strong.toStringAsFixed(2)}) — '
          '${(edge >= 0.085 || strong >= 0.22) ? 'inked / cel' : edge <= 0.050 ? 'painted / blended' : 'mixed'}',
    ];
  }

  double distanceTo(StyleFingerprintEntry entry) =>
      StyleFingerprint.renderDistance(this, entry);

  double fitTo(StyleFingerprintEntry entry) =>
      StyleFingerprint.renderFit(this, entry);
}

/// Style-only fingerprint: ink, texture, saturation, contrast.
class StyleFingerprint {
  static const size = 96;

  /// Catalog std-ish scales so each axis can move the distance.
  static const edgeScale = 0.015;
  static const fineScale = 0.006;
  static const strongScale = 0.07;
  static const satScale = 0.09;
  static const satVarScale = 0.04;
  static const contrastScale = 0.07;

  static const dimensions = 6;

  /// Near-clone in render space (same inking / sat / contrast).
  static const cloneDistance = 0.40;

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

  static String familyOf({
    required double edge,
    required double strong,
    required double satMean,
    required double contrast,
  }) {
    // Thresholds are on real nax V4.5 preview ranges (edge p90 ≈ 0.09),
    // not the old 0.11 cutoff that only synthetic checkerboards hit.
    if (edge >= 0.085 || strong >= 0.22) {
      return satMean < 0.32 ? 'sketchy' : 'cel-shaded';
    }
    if (edge >= 0.070 && contrast >= 0.26) return 'cel-shaded';
    if (edge < 0.050 && satMean >= 0.18) return 'painterly';
    if (satMean < 0.13) return 'muted';
    if (satMean >= 0.36) return 'vibrant';
    return 'balanced';
  }

  static StyleProfile fromImage(img.Image image) {
    var sumS = 0.0, sumS2 = 0.0, sumV = 0.0, sumV2 = 0.0, sumWarm = 0.0, sumChroma = 0.0;
    var count = 0;

    final w = image.width;
    final h = image.height;
    final gray = List<double>.filled(w * h, 0);
    final sat = List<double>.filled(w * h, 0);

    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final p = image.getPixel(x, y);
        final r = p.r / 255.0;
        final g = p.g / 255.0;
        final b = p.b / 255.0;
        final hsv = rgbToHsv(r, g, b);
        sat[y * w + x] = hsv[1];
        sumS += hsv[1];
        sumS2 += hsv[1] * hsv[1];
        sumV += hsv[2];
        sumV2 += hsv[2] * hsv[2];
        sumChroma += hsv[1] * hsv[2];
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
    var edge = 0.0;
    var strong = 0.0;
    final edgeCount = math.max(1, (w - 1) * (h - 1));
    for (var y = 0; y < h - 1; y++) {
      for (var x = 0; x < w - 1; x++) {
        final i = y * w + x;
        final dx = gray[i + 1] - gray[i];
        final dy = gray[i + w] - gray[i];
        final mag = math.sqrt(dx * dx + dy * dy);
        edge += mag;
        if (mag > 0.12) strong += 1;
      }
    }

    var fine = 0.0;
    final fineCount = math.max(1, (w - 2) * (h - 2));
    for (var y = 1; y < h - 1; y++) {
      for (var x = 1; x < w - 1; x++) {
        final i = y * w + x;
        final blur = (gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] + gray[i] * 4) / 8;
        fine += (gray[i] - blur).abs();
      }
    }

    final satMean = sumS / n;
    final satVar = math.max(0.0, sumS2 / n - satMean * satMean);
    final brightness = sumV / n;
    final contrast = math.sqrt(math.max(0.0, sumV2 / n - brightness * brightness));
    final colorfulness = sumChroma / n;
    final warmth = (sumS < 1e-6) ? 0.5 : (sumWarm / sumS).clamp(0.0, 1.0);
    final edgeMean = edge / edgeCount;
    final strongRatio = strong / edgeCount;
    final fineMean = fine / fineCount;

    return StyleProfile(
      vector: scaledVector(
        edge: edgeMean,
        fine: fineMean,
        strong: strongRatio,
        satMean: satMean,
        satVar: satVar,
        contrast: contrast,
      ),
      satMean: satMean,
      satVar: satVar,
      contrast: contrast,
      edge: edgeMean,
      fine: fineMean,
      strong: strongRatio,
      colorfulness: colorfulness,
      warmth: warmth,
      brightness: brightness,
    );
  }

  static List<double> scaledVector({
    required double edge,
    required double fine,
    required double strong,
    required double satMean,
    required double satVar,
    required double contrast,
  }) {
    return l2Normalize([
      edge / edgeScale,
      fine / fineScale,
      strong / strongScale,
      satMean / satScale,
      satVar / satVarScale,
      contrast / contrastScale,
    ]);
  }

  static double renderDistance(StyleProfile query, StyleFingerprintEntry entry) {
    return _axisDistance(
      qEdge: query.edge,
      qFine: query.fine,
      qStrong: query.strong,
      qSat: query.satMean,
      qSatVar: query.satVar,
      qContrast: query.contrast,
      eEdge: entry.edge,
      eFine: entry.fine,
      eStrong: entry.strong,
      eSat: entry.satMean,
      eSatVar: entry.satVar,
      eContrast: entry.contrast,
    );
  }

  static double entryDistance(StyleFingerprintEntry a, StyleFingerprintEntry b) {
    return _axisDistance(
      qEdge: a.edge ?? 0,
      qFine: a.fine ?? 0,
      qStrong: a.strong ?? 0,
      qSat: a.satMean ?? 0,
      qSatVar: a.satVar ?? 0,
      qContrast: a.contrast ?? 0,
      eEdge: b.edge,
      eFine: b.fine,
      eStrong: b.strong,
      eSat: b.satMean,
      eSatVar: b.satVar,
      eContrast: b.contrast,
    );
  }

  static double _axisDistance({
    required double qEdge,
    required double qFine,
    required double qStrong,
    required double qSat,
    required double qSatVar,
    required double qContrast,
    double? eEdge,
    double? eFine,
    double? eStrong,
    double? eSat,
    double? eSatVar,
    double? eContrast,
  }) {
    var d2 = 0.0;
    var axes = 0;
    void add(double q, double? e, double scale) {
      if (e == null) return;
      final z = (q - e) / scale;
      d2 += z * z;
      axes++;
    }

    add(qEdge, eEdge, edgeScale);
    add(qFine, eFine, fineScale);
    add(qStrong, eStrong, strongScale);
    add(qSat, eSat, satScale);
    add(qSatVar, eSatVar, satVarScale);
    add(qContrast, eContrast, contrastScale);
    if (axes == 0) return 99;
    return math.sqrt(d2);
  }

  static double renderFit(StyleProfile query, StyleFingerprintEntry entry) {
    return 1.0 / (1.0 + renderDistance(query, entry));
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

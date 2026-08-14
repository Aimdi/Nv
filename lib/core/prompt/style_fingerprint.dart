import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;

/// Compact visual signature for “which NAI artist preview looks like this”.
///
/// Not CLIP — hue / value / line-density stats. Useful because nax V4.5
/// constrained previews hold the subject still, so the leftover differences
/// are mostly rendering (palette, contrast, linework).
class StyleFingerprint {
  static const size = 48;
  static const hueBins = 12;
  static const satBins = 6;
  static const valBins = 6;
  static const dimensions = hueBins + satBins + valBins + 6;

  StyleFingerprint._();

  /// Decode [bytes] and return an L2-normalized [dimensions]-vector, or null.
  static List<double>? compute(Uint8List bytes) {
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

  static List<double> fromImage(img.Image image) {
    final hue = List<double>.filled(hueBins, 0);
    final sat = List<double>.filled(satBins, 0);
    final val = List<double>.filled(valBins, 0);
    var sumR = 0.0, sumG = 0.0, sumB = 0.0;
    var sumS = 0.0, sumV = 0.0, sumV2 = 0.0;
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
        hue[_bin(hsv[0] / 360.0, hueBins)] += 1;
        sat[_bin(hsv[1], satBins)] += 1;
        val[_bin(hsv[2], valBins)] += 1;
        sumR += r;
        sumG += g;
        sumB += b;
        sumS += hsv[1];
        sumV += hsv[2];
        sumV2 += hsv[2] * hsv[2];
        gray[y * w + x] = 0.299 * r + 0.587 * g + 0.114 * b;
        count++;
      }
    }

    if (count == 0) return List<double>.filled(dimensions, 0);

    for (var y = 0; y < h - 1; y++) {
      for (var x = 0; x < w - 1; x++) {
        final i = y * w + x;
        final dx = gray[i + 1] - gray[i];
        final dy = gray[i + w] - gray[i];
        edge += math.sqrt(dx * dx + dy * dy);
      }
    }
    final edgeCount = (w - 1) * (h - 1);
    final n = count.toDouble();
    final meanV = sumV / n;
    final varV = math.max(0.0, sumV2 / n - meanV * meanV);

    final raw = <double>[
      ...hue.map((v) => v / n),
      ...sat.map((v) => v / n),
      ...val.map((v) => v / n),
      sumR / n,
      sumG / n,
      sumB / n,
      sumS / n,
      math.sqrt(varV),
      edge / edgeCount,
    ];
    return l2Normalize(raw);
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

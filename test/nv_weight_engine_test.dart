import 'package:flutter_test/flutter_test.dart';
import 'package:naiweaver/core/prompt/weight_engine.dart';
import 'package:naiweaver/core/services/nv_enrichment.dart';

void main() {
  group('WeightEngine', () {
    test('brace depth maps to 1.05 powers', () {
      expect(WeightEngine.braceDepthToWeight(0), 1.0);
      expect(WeightEngine.braceDepthToWeight(1), closeTo(1.05, 1e-9));
      expect(WeightEngine.braceDepthToWeight(2), closeTo(1.1025, 1e-9));
      expect(WeightEngine.braceDepthToWeight(-2), closeTo(1 / (1.05 * 1.05), 1e-6));
    });

    test('weight to brace depth round trips', () {
      expect(WeightEngine.weightToBraceDepth(1.0), 0);
      expect(WeightEngine.weightToBraceDepth(1.05), 1);
      expect(WeightEngine.weightToBraceDepth(1.1025), 2);
      expect(WeightEngine.weightToBraceDepth(1 / 1.05), -1);
    });

    test('render applies artist prefix and spaces', () {
      final text = WeightEngine.render(
        [
          TagChip(tag: 'hammer_(sunset_beach)', kind: TagKind.artist, bracketCount: 1),
          TagChip(tag: '1girl'),
        ],
        options: const RenderOptions(
          underscoresToSpaces: true,
          artistPrefix: true,
        ),
      );
      expect(text, '{artist:hammer (sunset beach)}, 1girl');
    });

    test('numeric syntax round trips through parse', () {
      final rendered = WeightEngine.render([
        TagChip(tag: 'monochrome', numericWeight: 1.3),
      ]);
      expect(rendered.contains('1.3::'), isTrue);
      final parsed = WeightEngine.parse(rendered);
      expect(parsed, hasLength(1));
      expect(parsed.first.tag, 'monochrome');
      expect(parsed.first.numericWeight, closeTo(1.3, 1e-9));
    });

    test('double brackets parse to depth -2', () {
      final parsed = WeightEngine.parse('[[tag]]');
      expect(parsed, hasLength(1));
      expect(parsed.first.bracketCount, -2);
    });
  });

  group('ArtistPreviewUrls', () {
    test('candidates try primary then fallback encodings', () {
      final urls = ArtistPreviewUrls.candidates('hammer_(sunset_beach)');
      expect(urls.length, greaterThanOrEqualTo(2));
      expect(urls[0], contains('/images/1_10000/'));
      expect(urls.any((u) => u.contains('/images/2_5000/')), isTrue);
      expect(urls.every((u) => u.endsWith('.jpg')), isTrue);
    });
  });
}

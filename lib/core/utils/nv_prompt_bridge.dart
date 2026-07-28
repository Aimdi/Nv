import 'dart:math';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../features/generation/providers/generation_notifier.dart';
import '../../features/tools/tools_hub_screen.dart';
import '../prompt/weight_engine.dart';
import '../services/tag_service.dart';

/// Bridges Aimdi chip/artist tools into the NAIWeaver generation prompt.
class NvPromptBridge {
  static const chipComposerToolId = 'chip_composer';
  static const artistBrowserToolId = 'artist_browser';

  static Future<void> openChipComposer(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const ToolsHubScreen(initialToolId: chipComposerToolId),
      ),
    );
  }

  static Future<void> openArtistBrowser(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const ToolsHubScreen(initialToolId: artistBrowserToolId),
      ),
    );
  }

  /// Replace or append [text] on the main positive prompt, then return home.
  static void applyToMainPrompt(
    BuildContext context,
    String text, {
    bool append = false,
    String? snackbar,
  }) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return;

    final messenger = ScaffoldMessenger.maybeOf(context);
    _writePrompt(context, trimmed, append: append);

    Navigator.of(context).popUntil((route) => route.isFirst);
    if (snackbar != null && messenger != null) {
      messenger.showSnackBar(SnackBar(content: Text(snackbar)));
    }
  }

  /// Append [text] to the main prompt without navigating away.
  static void appendInPlace(
    BuildContext context,
    String text, {
    String? snackbar,
  }) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return;

    _writePrompt(context, trimmed, append: true);
    if (snackbar != null) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(content: Text(snackbar)),
      );
    }
  }

  /// Instantly append 2–3 random popular artists to the generator prompt.
  static void addRandomArtists(
    BuildContext context, {
    int minCount = 2,
    int maxCount = 3,
  }) {
    assert(minCount >= 1 && maxCount >= minCount);
    final tagService = context.read<TagService>();
    final gen = context.read<GenerationNotifier>();
    final existing = gen.promptController.text.toLowerCase();

    final pool = tagService.tags
        .where((t) => t.typeName.toLowerCase() == 'artist')
        .where((t) => t.tag.toLowerCase() != 'banned_artist')
        .where((t) => t.count >= 200)
        .toList()
      ..sort((a, b) => b.count.compareTo(a.count));

    if (pool.isEmpty) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        const SnackBar(content: Text('No artist tags loaded yet')),
      );
      return;
    }

    final rng = Random();
    final want = minCount + rng.nextInt(maxCount - minCount + 1);
    final candidates = pool.take(1000).toList()..shuffle(rng);
    final rendered = <String>[];

    for (final artist in candidates) {
      final piece = WeightEngine.renderEntry(
        TagChip(tag: artist.tag, kind: TagKind.artist),
      );
      if (piece == null || piece.isEmpty) continue;

      final needle = artist.tag.toLowerCase();
      final spaced = needle.replaceAll('_', ' ');
      if (existing.contains(needle) || existing.contains(spaced)) continue;
      if (rendered.any((r) => r.toLowerCase().contains(needle))) continue;

      rendered.add(piece);
      if (rendered.length >= want) break;
    }

    if (rendered.isEmpty) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        const SnackBar(content: Text('Could not pick random artists')),
      );
      return;
    }

    appendInPlace(
      context,
      rendered.join(', '),
      snackbar: 'Added ${rendered.length} random artists',
    );
  }

  static void _writePrompt(
    BuildContext context,
    String trimmed, {
    required bool append,
  }) {
    final gen = context.read<GenerationNotifier>();
    final controller = gen.promptController;

    if (append) {
      final existing = controller.text.trim();
      controller.text = existing.isEmpty ? trimmed : '$existing, $trimmed';
    } else {
      controller.text = trimmed;
    }
    controller.selection = TextSelection.collapsed(offset: controller.text.length);
  }
}

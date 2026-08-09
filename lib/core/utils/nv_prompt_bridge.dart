import 'dart:math';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../features/generation/providers/generation_notifier.dart';
import '../../features/tools/tools_hub_screen.dart';
import '../prompt/artist_mix_engine.dart';
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

  /// Replace previous artist tags with a fresh weighted mix.
  ///
  /// Keeps characters / general tags. Always emphasizes at least one artist
  /// (`1.2–1.4:: drawn by …::`, `{{…}}`, etc.).
  static void addRandomArtists(BuildContext context) {
    final tagService = context.read<TagService>();
    final gen = context.read<GenerationNotifier>();

    final artistTags = tagService.tags
        .where((t) => t.typeName.toLowerCase() == 'artist')
        .where((t) => t.tag.toLowerCase() != 'banned_artist')
        .where((t) => t.count >= 200)
        .toList()
      ..sort((a, b) => b.count.compareTo(a.count));

    if (artistTags.isEmpty) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        const SnackBar(content: Text('No artist tags loaded yet')),
      );
      return;
    }

    final characterNames = tagService.tags
        .where((t) => t.typeName.toLowerCase() == 'character')
        .map((t) => t.tag)
        .toSet();
    final artistNames = artistTags.map((t) => t.tag).toSet();
    final pool = artistTags.take(1000).map((t) => t.tag).toList();

    final next = ArtistMixEngine.replaceArtistsInPrompt(
      gen.promptController.text,
      artistPool: pool,
      artistNames: artistNames,
      characterNames: characterNames,
      random: Random(),
    );

    _writePrompt(context, next, append: false);
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      const SnackBar(content: Text('Replaced artists with a new weighted mix')),
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

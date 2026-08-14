import 'dart:math';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../features/generation/providers/generation_notifier.dart';
import '../../features/tools/tools_hub_screen.dart';
import '../prompt/artist_mix_engine.dart';
import '../services/nax_strength_service.dart';
import '../services/style_match_service.dart';
import '../services/tag_service.dart';

/// Bridges Aimdi chip/artist tools into the NAIWeaver generation prompt.
class NvPromptBridge {
  static const chipComposerToolId = 'chip_composer';
  static const artistBrowserToolId = 'artist_browser';
  static const styleFromImageToolId = 'style_from_image';
  static bool _catalogLoading = false;

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

  static Future<void> openStyleFromImage(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => const ToolsHubScreen(initialToolId: styleFromImageToolId),
      ),
    );
  }

  /// Ensure curated mix catalog + nax strength table are loaded.
  static Future<void> ensureMixCatalog() async {
    if (_catalogLoading) return;
    _catalogLoading = true;
    try {
      await Future.wait([
        ArtistMixEngine.loadCatalog(),
        NaxStrengthCatalog.load(),
        StyleFingerprintIndex.load(),
      ]);
    } finally {
      _catalogLoading = false;
    }
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

  /// Replace previous artist tags with a quality-aware mix.
  ///
  /// Keeps characters. Uses curated seed triples + nax.moe V4.5 style-pull
  /// sampling instead of shuffling top Danbooru post-count artists.
  static void addRandomArtists(BuildContext context) {
    ensureMixCatalog();
    final tagService = context.read<TagService>();
    final gen = context.read<GenerationNotifier>();

    final artistTags = tagService.tags
        .where((t) => t.typeName.toLowerCase() == 'artist')
        .where((t) {
          final n = t.tag.toLowerCase().replaceAll('_', ' ');
          return n != 'banned artist' && n != 'banned_artist';
        })
        .toList();

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
    final nax = NaxStrengthCatalog.instance;
    final candidates = artistTags.map((t) {
      final entry = t.naxScore != null
          ? null
          : nax.lookup(t.tag);
      return ArtistCandidate(
        name: t.tag,
        count: t.count,
        naxScore: t.naxScore ?? entry?.score,
        naxVotes: t.naxVotes ?? entry?.votes,
      );
    }).toList();

    final next = ArtistMixEngine.replaceArtistsInPrompt(
      gen.promptController.text,
      candidates: candidates,
      artistNames: artistNames,
      characterNames: characterNames,
      random: Random(),
    );

    _writePrompt(context, next, append: false);
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      const SnackBar(
        content: Text(
          'Applied V4.5 style-pull mix (1.1:: lead, 0.8:: supports)',
        ),
      ),
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

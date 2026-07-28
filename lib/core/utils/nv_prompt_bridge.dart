import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../features/generation/providers/generation_notifier.dart';
import '../../features/tools/tools_hub_screen.dart';

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
    final gen = context.read<GenerationNotifier>();
    final controller = gen.promptController;

    if (append) {
      final existing = controller.text.trim();
      controller.text = existing.isEmpty ? trimmed : '$existing, $trimmed';
    } else {
      controller.text = trimmed;
    }
    controller.selection = TextSelection.collapsed(offset: controller.text.length);

    Navigator.of(context).popUntil((route) => route.isFirst);
    if (snackbar != null && messenger != null) {
      messenger.showSnackBar(SnackBar(content: Text(snackbar)));
    }
  }
}

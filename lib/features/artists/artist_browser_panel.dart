import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/prompt/weight_engine.dart';
import '../../../core/services/nv_enrichment.dart';
import '../../../core/services/tag_service.dart';
import '../../../core/theme/theme_extensions.dart';
import '../../../core/utils/nv_prompt_bridge.dart';

/// Artist browser with on-demand HuggingFace NovelAI v3 preview samples.
///
/// Adds selected artists directly into the main generator prompt.
class ArtistBrowserPanel extends StatefulWidget {
  const ArtistBrowserPanel({super.key});

  @override
  State<ArtistBrowserPanel> createState() => _ArtistBrowserPanelState();
}

class _ArtistBrowserPanelState extends State<ArtistBrowserPanel> {
  final _queryController = TextEditingController();
  String _query = '';
  bool _onlinePreviews = true;
  _ArtistSort _sort = _ArtistSort.postCountDesc;

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  List<DanbooruTag> _artists(TagService tagService) {
    final q = _query.trim().toLowerCase();
    var list = tagService.tags
        .where((t) => t.typeName.toLowerCase() == 'artist')
        .where((t) => q.isEmpty || t.tag.toLowerCase().contains(q))
        .toList();
    switch (_sort) {
      case _ArtistSort.postCountDesc:
        list.sort((a, b) => b.count.compareTo(a.count));
      case _ArtistSort.nameAsc:
        list.sort((a, b) => a.tag.compareTo(b.tag));
      case _ArtistSort.random:
        list = List.of(list)..shuffle();
    }
    return list.take(200).toList();
  }

  String? _renderArtist(DanbooruTag artist) {
    return WeightEngine.renderEntry(
      TagChip(tag: artist.tag, kind: TagKind.artist),
    );
  }

  Future<void> _copyArtist(DanbooruTag artist) async {
    final rendered = _renderArtist(artist);
    if (rendered == null) return;
    await Clipboard.setData(ClipboardData(text: rendered));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Copied $rendered')),
    );
  }

  void _addToPrompt(DanbooruTag artist) {
    final rendered = _renderArtist(artist);
    if (rendered == null) return;
    NvPromptBridge.applyToMainPrompt(
      context,
      rendered,
      append: true,
      snackbar: 'Added $rendered to the main prompt',
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final tagService = context.read<TagService>();
    final artists = _artists(tagService);

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'ARTIST BROWSER',
                style: TextStyle(
                  color: t.accent,
                  letterSpacing: 2,
                  fontWeight: FontWeight.bold,
                  fontSize: t.fontSize(12),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Aimdi artist picker with HuggingFace SFW previews. Tap a card '
                'for details, or long-press to add artist:name to the generator.',
                style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(12)),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _queryController,
                decoration: const InputDecoration(
                  hintText: 'Filter artists',
                  prefixIcon: Icon(Icons.search),
                ),
                onChanged: (v) => setState(() => _query = v),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  ChoiceChip(
                    label: const Text('Works'),
                    selected: _sort == _ArtistSort.postCountDesc,
                    onSelected: (_) => setState(() => _sort = _ArtistSort.postCountDesc),
                  ),
                  ChoiceChip(
                    label: const Text('Name'),
                    selected: _sort == _ArtistSort.nameAsc,
                    onSelected: (_) => setState(() => _sort = _ArtistSort.nameAsc),
                  ),
                  ChoiceChip(
                    label: const Text('Random'),
                    selected: _sort == _ArtistSort.random,
                    onSelected: (_) => setState(() => _sort = _ArtistSort.random),
                  ),
                  FilterChip(
                    label: const Text('Online previews'),
                    selected: _onlinePreviews,
                    onSelected: (v) => setState(() => _onlinePreviews = v),
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(12),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              childAspectRatio: 0.72,
            ),
            itemCount: artists.length,
            itemBuilder: (context, index) {
              final artist = artists[index];
              return _ArtistCard(
                artist: artist,
                onlinePreviews: _onlinePreviews,
                onTap: () => _showDetail(artist),
                onLongPress: () => _addToPrompt(artist),
              );
            },
          ),
        ),
      ],
    );
  }

  void _showDetail(DanbooruTag artist) {
    final urls = ArtistPreviewUrls.candidates(artist.tag);
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        final t = context.t;
        return Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                artist.tag.replaceAll('_', ' '),
                style: TextStyle(
                  fontSize: t.fontSize(20),
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              Text('${artist.count} posts', style: TextStyle(color: t.secondaryText)),
              const SizedBox(height: 12),
              if (_onlinePreviews)
                AspectRatio(
                  aspectRatio: 832 / 1216,
                  child: _FallbackNetworkImage(urls: urls, fit: BoxFit.contain),
                ),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _addToPrompt(artist);
                },
                icon: const Icon(Icons.add),
                label: const Text('Add to generator prompt'),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _copyArtist(artist);
                },
                icon: const Icon(Icons.copy),
                label: const Text('Copy artist: tag'),
              ),
              const SizedBox(height: 12),
            ],
          ),
        );
      },
    );
  }
}

class _ArtistCard extends StatelessWidget {
  const _ArtistCard({
    required this.artist,
    required this.onlinePreviews,
    required this.onTap,
    required this.onLongPress,
  });

  final DanbooruTag artist;
  final bool onlinePreviews;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: onlinePreviews
                  ? _FallbackNetworkImage(
                      urls: ArtistPreviewUrls.candidates(artist.tag),
                      fit: BoxFit.cover,
                    )
                  : ColoredBox(
                      color: t.surfaceMid,
                      child: Icon(Icons.brush, color: t.secondaryText),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.all(8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    artist.tag.replaceAll('_', ' '),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: t.fontSize(12), fontWeight: FontWeight.w600),
                  ),
                  Text(
                    '${artist.count} posts · long-press to add',
                    style: TextStyle(fontSize: t.fontSize(10), color: t.secondaryText),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Tries each URL until one loads (HF folder fallback).
class _FallbackNetworkImage extends StatefulWidget {
  const _FallbackNetworkImage({required this.urls, required this.fit});

  final List<String> urls;
  final BoxFit fit;

  @override
  State<_FallbackNetworkImage> createState() => _FallbackNetworkImageState();
}

class _FallbackNetworkImageState extends State<_FallbackNetworkImage> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    if (_index >= widget.urls.length) {
      return const Center(child: Icon(Icons.image_not_supported_outlined));
    }
    return Image.network(
      widget.urls[_index],
      fit: widget.fit,
      gaplessPlayback: true,
      errorBuilder: (_, error, stackTrace) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && _index < widget.urls.length - 1) {
            setState(() => _index += 1);
          }
        });
        return const Center(child: CircularProgressIndicator(strokeWidth: 2));
      },
      loadingBuilder: (context, child, progress) {
        if (progress == null) return child;
        return const Center(child: CircularProgressIndicator(strokeWidth: 2));
      },
    );
  }
}

enum _ArtistSort { postCountDesc, nameAsc, random }

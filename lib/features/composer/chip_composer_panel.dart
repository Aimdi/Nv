import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/prompt/weight_engine.dart';
import '../../../core/services/nv_enrichment.dart';
import '../../../core/services/tag_service.dart';
import '../../../core/theme/theme_extensions.dart';

/// Chip-based NovelAI prompt composer (from Aimdi/Nv).
///
/// Builds a weighted prompt as reorderable chips, with offline FTS-style local
/// suggestions merged with live Danbooru autocomplete. Copy the result into
/// generation or the NovelAI PWA.
class ChipComposerPanel extends StatefulWidget {
  const ChipComposerPanel({super.key});

  @override
  State<ChipComposerPanel> createState() => _ChipComposerPanelState();
}

class _ChipComposerPanelState extends State<ChipComposerPanel> {
  final _queryController = TextEditingController();
  final _danbooru = DanbooruAutocompleteService();
  final List<TagChip> _chips = [];
  List<_Suggestion> _suggestions = const [];
  bool _underscoresToSpaces = true;
  bool _artistPrefix = true;
  bool _onlineSearch = true;
  bool _allowNsfw = false;
  String? _message;

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  String get _rendered => WeightEngine.render(
        _chips,
        options: RenderOptions(
          underscoresToSpaces: _underscoresToSpaces,
          artistPrefix: _artistPrefix,
        ),
      );

  Future<void> _onQueryChanged(String value) async {
    final tagService = context.read<TagService>();
    final local = tagService
        .getSuggestions(value, limit: 24)
        .map(
          (t) => _Suggestion(
            name: t.tag,
            displayName: t.tag.replaceAll('_', ' '),
            category: _categoryFromType(t.typeName),
            postCount: t.count,
            kind: _kindFromType(t.typeName),
            online: false,
          ),
        )
        .toList();

    setState(() => _suggestions = local);

    if (!_onlineSearch || value.trim().length < 2) return;
    final remote = await _danbooru.autocomplete(
      value,
      allowNsfwMeta: _allowNsfw,
    );
    if (!mounted || _queryController.text != value) return;

    final seen = local.map((s) => s.name.toLowerCase()).toSet();
    final merged = [
      ...local,
      ...remote
          .map(
            (item) => _Suggestion(
              name: item.tagName,
              displayName: item.tagName.replaceAll('_', ' '),
              category: item.category,
              postCount: item.postCount,
              kind: _kindFromCategory(item.category),
              online: true,
            ),
          )
          .where((s) => seen.add(s.name.toLowerCase())),
    ];
    setState(() => _suggestions = merged);
  }

  void _addSuggestion(_Suggestion suggestion) {
    if (_chips.any((c) => c.tag.toLowerCase() == suggestion.name.toLowerCase())) {
      setState(() => _message = '"${suggestion.displayName}" is already in the combo');
      return;
    }
    setState(() {
      _chips.add(TagChip(tag: suggestion.name, kind: suggestion.kind));
      _queryController.clear();
      _suggestions = const [];
      _message = null;
    });
  }

  void _addFromInput() {
    final text = _queryController.text.trim();
    if (text.isEmpty) return;
    final parsed = WeightEngine.parse(text);
    if (parsed.isEmpty) return;
    setState(() {
      _chips.addAll(parsed);
      _queryController.clear();
      _suggestions = const [];
    });
  }

  Future<void> _copy() async {
    final text = _rendered;
    if (text.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    setState(() => _message = 'Prompt copied — paste into NovelAI or the generator');
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(_message!)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(
                'CHIP COMPOSER',
                style: TextStyle(
                  color: t.accent,
                  letterSpacing: 2,
                  fontWeight: FontWeight.bold,
                  fontSize: t.fontSize(12),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Compose weighted tags as chips, then copy into the generator or NovelAI. '
                'Earlier tags have more influence.',
                style: TextStyle(color: t.textSecondary, fontSize: t.fontSize(12)),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  FilterChip(
                    label: const Text('Spaces'),
                    selected: _underscoresToSpaces,
                    onSelected: (v) => setState(() => _underscoresToSpaces = v),
                  ),
                  FilterChip(
                    label: const Text('artist:'),
                    selected: _artistPrefix,
                    onSelected: (v) => setState(() => _artistPrefix = v),
                  ),
                  FilterChip(
                    label: const Text('Online search'),
                    selected: _onlineSearch,
                    onSelected: (v) => setState(() => _onlineSearch = v),
                  ),
                  FilterChip(
                    label: const Text('NSFW ratings'),
                    selected: _allowNsfw,
                    onSelected: (v) => setState(() => _allowNsfw = v),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _queryController,
                decoration: InputDecoration(
                  hintText: 'Search tags or paste a prompt fragment',
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.add),
                    onPressed: _addFromInput,
                  ),
                ),
                onChanged: _onQueryChanged,
                onSubmitted: (_) => _addFromInput(),
              ),
              if (_suggestions.isNotEmpty) ...[
                const SizedBox(height: 8),
                ..._suggestions.take(20).map((s) {
                  return ListTile(
                    dense: true,
                    leading: Container(
                      width: 4,
                      height: 28,
                      color: _categoryColor(s.category),
                    ),
                    title: Text(s.displayName),
                    subtitle: Text(
                      '${_categoryLabel(s.category)}${s.online ? ' · online' : ''}'
                      '${s.postCount > 0 ? ' · ${s.postCount}' : ''}',
                    ),
                    onTap: () => _addSuggestion(s),
                  );
                }),
              ],
              const SizedBox(height: 12),
              if (_chips.isEmpty)
                Text(
                  'No tags yet. Search above or paste `{tag}, 1.5::artist:name::`.',
                  style: TextStyle(color: t.textSecondary),
                )
              else
                ReorderableListView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _chips.length,
                  onReorder: (oldIndex, newIndex) {
                    setState(() {
                      if (newIndex > oldIndex) newIndex -= 1;
                      final item = _chips.removeAt(oldIndex);
                      _chips.insert(newIndex, item);
                    });
                  },
                  itemBuilder: (context, index) {
                    final chip = _chips[index];
                    return Card(
                      key: ValueKey(chip.id),
                      child: ListTile(
                        title: Text(chip.tag.replaceAll('_', ' ')),
                        subtitle: Text(
                          'braces ${chip.bracketCount}'
                          '${chip.numericWeight != null ? ' · x${chip.numericWeight}' : ''}'
                          ' · ${chip.kind.name}',
                        ),
                        leading: const Icon(Icons.drag_handle),
                        trailing: Wrap(
                          spacing: 0,
                          children: [
                            IconButton(
                              tooltip: 'Weaken',
                              icon: const Icon(Icons.remove),
                              onPressed: () => setState(() {
                                _chips[index] = chip.copyWith(
                                  bracketCount: (chip.bracketCount - 1)
                                      .clamp(-TagChip.maxBracketCount, TagChip.maxBracketCount),
                                );
                              }),
                            ),
                            IconButton(
                              tooltip: 'Strengthen',
                              icon: const Icon(Icons.add),
                              onPressed: () => setState(() {
                                _chips[index] = chip.copyWith(
                                  bracketCount: (chip.bracketCount + 1)
                                      .clamp(-TagChip.maxBracketCount, TagChip.maxBracketCount),
                                );
                              }),
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete_outline),
                              onPressed: () => setState(() => _chips.removeAt(index)),
                            ),
                          ],
                        ),
                        onTap: () => _editNumeric(index),
                      ),
                    );
                  },
                ),
              const SizedBox(height: 16),
              SelectableText(
                _rendered.isEmpty ? '(empty prompt)' : _rendered,
                style: TextStyle(
                  fontFamily: 'monospace',
                  color: t.textPrimary,
                  fontSize: t.fontSize(13),
                ),
              ),
            ],
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _chips.isEmpty ? null : _copy,
                    icon: const Icon(Icons.copy),
                    label: const Text('Copy prompt'),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: _chips.isEmpty
                      ? null
                      : () => setState(() {
                            _chips.clear();
                            _message = null;
                          }),
                  child: const Text('Clear'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _editNumeric(int index) async {
    final chip = _chips[index];
    final controller = TextEditingController(
      text: chip.numericWeight?.toString() ?? '',
    );
    final value = await showDialog<Object?>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Numeric weight'),
        content: TextField(
          controller: controller,
          keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
          decoration: const InputDecoration(
            hintText: 'Leave blank for braces only',
            helperText: 'Emits w::tag::  (e.g. 1.3)',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'clear'),
            child: const Text('Clear'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(
              context,
              double.tryParse(controller.text.trim()),
            ),
            child: const Text('Apply'),
          ),
        ],
      ),
    );
    if (!mounted || value == null) return;
    setState(() {
      if (value == 'clear') {
        _chips[index] = chip.copyWith(clearNumericWeight: true);
      } else if (value is double) {
        _chips[index] = chip.copyWith(numericWeight: value);
      }
    });
  }

  static int _categoryFromType(String typeName) {
    switch (typeName.toLowerCase()) {
      case 'artist':
        return DanbooruAutocompleteItem.categoryArtist;
      case 'copyright':
        return DanbooruAutocompleteItem.categoryCopyright;
      case 'character':
        return DanbooruAutocompleteItem.categoryCharacter;
      case 'meta':
        return DanbooruAutocompleteItem.categoryMeta;
      default:
        return DanbooruAutocompleteItem.categoryGeneral;
    }
  }

  static TagKind _kindFromType(String typeName) {
    switch (typeName.toLowerCase()) {
      case 'artist':
        return TagKind.artist;
      case 'copyright':
        return TagKind.copyright;
      case 'character':
        return TagKind.character;
      case 'meta':
        return TagKind.meta;
      default:
        return TagKind.general;
    }
  }

  static TagKind _kindFromCategory(int category) {
    switch (category) {
      case DanbooruAutocompleteItem.categoryArtist:
        return TagKind.artist;
      case DanbooruAutocompleteItem.categoryCopyright:
        return TagKind.copyright;
      case DanbooruAutocompleteItem.categoryCharacter:
        return TagKind.character;
      case DanbooruAutocompleteItem.categoryMeta:
        return TagKind.meta;
      default:
        return TagKind.general;
    }
  }

  static Color _categoryColor(int category) {
    switch (category) {
      case DanbooruAutocompleteItem.categoryArtist:
        return const Color(0xFFC45C26);
      case DanbooruAutocompleteItem.categoryCopyright:
        return const Color(0xFFA12FAD);
      case DanbooruAutocompleteItem.categoryCharacter:
        return const Color(0xFF2F8F46);
      case DanbooruAutocompleteItem.categoryMeta:
        return const Color(0xFFB8860B);
      default:
        return const Color(0xFF7A8494);
    }
  }

  static String _categoryLabel(int category) {
    switch (category) {
      case DanbooruAutocompleteItem.categoryArtist:
        return 'artist';
      case DanbooruAutocompleteItem.categoryCopyright:
        return 'copyright';
      case DanbooruAutocompleteItem.categoryCharacter:
        return 'character';
      case DanbooruAutocompleteItem.categoryMeta:
        return 'meta';
      default:
        return 'general';
    }
  }
}

class _Suggestion {
  const _Suggestion({
    required this.name,
    required this.displayName,
    required this.category,
    required this.postCount,
    required this.kind,
    required this.online,
  });

  final String name;
  final String displayName;
  final int category;
  final int postCount;
  final TagKind kind;
  final bool online;
}

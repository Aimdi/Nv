import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/prompt/artist_mix_engine.dart';
import '../../core/prompt/artist_strength.dart';
import '../../core/prompt/style_knowledge.dart';
import '../../core/prompt/weight_engine.dart';
import '../../core/services/nax_strength_service.dart';
import '../../core/services/nv_enrichment.dart';
import '../../core/services/preferences_service.dart';
import '../../core/services/style_advisor_service.dart';
import '../../core/services/style_match_service.dart';
import '../../core/theme/theme_extensions.dart';
import '../../core/utils/app_snackbar.dart';
import '../../core/utils/file_picker_helper.dart';
import '../../core/utils/nv_prompt_bridge.dart';
import '../gallery/providers/gallery_notifier.dart';
import '../generation/providers/generation_notifier.dart';

/// Pick an image → read the rendering, then plan a lead / mixer / accent.
class StyleFromImagePanel extends StatefulWidget {
  const StyleFromImagePanel({super.key, this.initialBytes});

  final Uint8List? initialBytes;

  @override
  State<StyleFromImagePanel> createState() => _StyleFromImagePanelState();
}

class _StyleFromImagePanelState extends State<StyleFromImagePanel> {
  final _service = StyleMatchService();
  final _advisor = StyleAdvisorService();
  Uint8List? _bytes;
  StyleMatchReport? _report;
  StyleAdvice? _advice;
  Object? _error;
  bool _busy = false;
  bool _asking = false;

  @override
  void initState() {
    super.initState();
    if (widget.initialBytes != null) {
      _bytes = widget.initialBytes;
      WidgetsBinding.instance.addPostFrameCallback((_) => _analyze());
    }
  }

  Future<void> _setBytes(Uint8List bytes) async {
    setState(() {
      _bytes = bytes;
      _report = null;
      _advice = null;
      _error = null;
    });
    await _analyze();
  }

  Future<void> _analyze() async {
    final bytes = _bytes;
    if (bytes == null || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final report = await _service.match(bytes);
      if (!mounted) return;
      setState(() {
        _report = report;
        _advice = null;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _copyBrief() async {
    final report = _report;
    if (report == null) return;
    final json = const JsonEncoder.withIndent('  ').convert(
      StyleKnowledge.briefFromReport(report),
    );
    await Clipboard.setData(ClipboardData(text: json));
    if (!mounted) return;
    showAppSnackBar(context, 'Copied Nv knowledge brief — paste it into Grok');
  }

  Future<void> _askGrok() async {
    final report = _report;
    final bytes = _bytes;
    if (report == null || _asking) return;
    final prefs = context.read<PreferencesService>();
    final key = await prefs.getXaiApiKey();
    if (key.trim().isEmpty) {
      if (!mounted) return;
      showErrorSnackBar(
        context,
        'Set an xAI key in Settings, or copy the knowledge brief into Grok.',
      );
      return;
    }
    setState(() => _asking = true);
    try {
      final advice = await _advisor.advise(
        report: report,
        apiKey: key,
        imageBytes: bytes,
      );
      if (!mounted) return;
      setState(() => _advice = advice);
    } catch (error) {
      if (!mounted) return;
      showErrorSnackBar(context, 'Grok failed: $error');
    } finally {
      if (mounted) setState(() => _asking = false);
    }
  }

  Future<void> _pickFile() async {
    final result = await pickImageFiles(withData: true);
    if (result == null || result.files.isEmpty) return;
    final file = result.files.single;
    if (file.bytes != null) {
      await _setBytes(file.bytes!);
      return;
    }
    if (file.path != null) {
      await _setBytes(await File(file.path!).readAsBytes());
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final gen = context.watch<GenerationNotifier>();
    final gallery = context.watch<GalleryNotifier>();
    final hasCurrent = gen.state.generatedImage != null;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: [
        Text(
          'STYLE FROM IMAGE',
          style: TextStyle(
            color: t.accent,
            letterSpacing: 2,
            fontWeight: FontWeight.bold,
            fontSize: t.fontSize(12),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'It reads ink vs paint, saturation, and contrast (not subject color), '
          'then plans a lead / mixer / accent. If the lead sits in a '
          'community-tested triple, that mix wins over invented neighbors. '
          'Source IDs (NovelAI PNG or Danbooru) still beat guesses.',
          style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(12)),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (hasCurrent)
              ActionChip(
                avatar: const Icon(Icons.flash_on, size: 16),
                label: const Text('Current generation'),
                onPressed: () => _setBytes(gen.state.generatedImage!),
              ),
            ActionChip(
              avatar: const Icon(Icons.photo_library_outlined, size: 16),
              label: const Text('Pick image'),
              onPressed: _pickFile,
            ),
            if (gallery.items.isNotEmpty)
              ActionChip(
                avatar: const Icon(Icons.collections, size: 16),
                label: const Text('Latest gallery'),
                onPressed: () async {
                  final item = gallery.items.first;
                  if (await item.file.exists()) {
                    await _setBytes(await item.file.readAsBytes());
                  }
                },
              ),
            if (_bytes != null)
              ActionChip(
                avatar: const Icon(Icons.refresh, size: 16),
                label: const Text('Re-analyze'),
                onPressed: _busy ? null : _analyze,
              ),
          ],
        ),
        const SizedBox(height: 16),
        if (_bytes != null)
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: AspectRatio(
              aspectRatio: 3 / 4,
              child: Image.memory(_bytes!, fit: BoxFit.contain),
            ),
          ),
        if (_busy) ...[
          const SizedBox(height: 24),
          const Center(child: CircularProgressIndicator()),
          const SizedBox(height: 8),
          Text(
            'Reading the image, then planning a mix…',
            textAlign: TextAlign.center,
            style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(12)),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text('Match failed: $_error', style: TextStyle(color: t.accent)),
        ],
        if (_report != null) ...[
          const SizedBox(height: 20),
          _ReportView(
            report: _report!,
            advice: _advice,
            asking: _asking,
            onAskGrok: _askGrok,
            onCopyBrief: _copyBrief,
          ),
        ],
      ],
    );
  }
}

class _ReportView extends StatelessWidget {
  const _ReportView({
    required this.report,
    this.advice,
    this.asking = false,
    this.onAskGrok,
    this.onCopyBrief,
  });

  final StyleMatchReport report;
  final StyleAdvice? advice;
  final bool asking;
  final VoidCallback? onAskGrok;
  final VoidCallback? onCopyBrief;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final plan = report.plan;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (plan != null) ...[
          Text('HOW IT READ THIS', style: _sectionStyle(t)),
          const SizedBox(height: 6),
          Text(
            plan.brief.summary,
            style: TextStyle(fontSize: t.fontSize(14), fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 6),
          ...plan.brief.observations.map(
            (line) => Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                line,
                style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(12)),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Text('WHY THIS MIX', style: _sectionStyle(t)),
          const SizedBox(height: 6),
          ...plan.picks.map((pick) {
            final role = switch (pick.role) {
              MixRole.lead => 'Lead',
              MixRole.support => 'Support',
              MixRole.accent => 'Accent',
            };
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                '$role ${ArtistMixEngine.promptName(pick.name)} — ${pick.reason}',
                style: TextStyle(fontSize: t.fontSize(12)),
              ),
            );
          }),
          const SizedBox(height: 16),
        ],
        if (report.mix.isNotEmpty) ...[
          Text('SUGGESTED MIX', style: _sectionStyle(t)),
          const SizedBox(height: 8),
          SelectableText(
            report.mix,
            style: TextStyle(fontSize: t.fontSize(13), fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                onPressed: onCopyBrief,
                icon: const Icon(Icons.copy, size: 16),
                label: const Text('Copy knowledge brief'),
              ),
              FilledButton.tonalIcon(
                onPressed: asking ? null : onAskGrok,
                icon: asking
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.psychology, size: 16),
                label: Text(asking ? 'Asking Grok…' : 'Ask Grok'),
              ),
            ],
          ),
          if (advice != null) ...[
            const SizedBox(height: 16),
            Text('GROK (GROUNDED IN NV)', style: _sectionStyle(t)),
            const SizedBox(height: 6),
            Text(
              'Model ${advice!.model}. Names were checked against nax / triples / the local plan.',
              style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
            ),
            const SizedBox(height: 6),
            ...advice!.picks.map(
              (pick) => Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Text(
                  '${pick.role.name} ${ArtistMixEngine.promptName(pick.name)} — ${pick.reason}',
                  style: TextStyle(fontSize: t.fontSize(12)),
                ),
              ),
            ),
            if (advice!.mix.isNotEmpty) ...[
              const SizedBox(height: 6),
              SelectableText(
                advice!.mix,
                style: TextStyle(fontSize: t.fontSize(13), fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              FilledButton.icon(
                onPressed: () => NvPromptBridge.applyToMainPrompt(
                  context,
                  advice!.mix,
                  snackbar: 'Applied Grok artist mix',
                ),
                icon: const Icon(Icons.auto_awesome),
                label: const Text('Apply Grok mix'),
              ),
            ],
            ...advice!.notes.map(
              (note) => Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  note,
                  style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
                ),
              ),
            ),
          ],
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: () => NvPromptBridge.applyToMainPrompt(
              context,
              report.mix,
              snackbar: 'Applied planned artist mix',
            ),
            icon: const Icon(Icons.auto_awesome),
            label: const Text('Apply mix to generator'),
          ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => NvPromptBridge.appendInPlace(
              context,
              report.mix,
              snackbar: 'Appended planned artist mix',
            ),
            icon: const Icon(Icons.add),
            label: const Text('Append mix'),
          ),
          const SizedBox(height: 20),
        ],
        if (report.sourceHits.isNotEmpty) ...[
          Text('SOURCE / METADATA HITS', style: _sectionStyle(t)),
          const SizedBox(height: 4),
          Text(
            'These came from the file itself or a reverse-image match. '
            'Much more reliable than visual look-alikes.',
            style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
          ),
          const SizedBox(height: 8),
          ...report.sourceHits.map((h) => _HitTile(hit: h)),
          const SizedBox(height: 16),
        ],
        if (report.visualHits.isNotEmpty) ...[
          Text('ALSO CONSIDERED', style: _sectionStyle(t)),
          const SizedBox(height: 4),
          Text(
            'Nearby V4.5 renderings that were not used in the mix. '
            'Similar line/paint, not proof of authorship.',
            style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
          ),
          const SizedBox(height: 8),
          ...report.visualHits.take(8).map((h) => _HitTile(hit: h)),
          const SizedBox(height: 16),
        ],
        if (report.notes.isNotEmpty)
          ...report.notes.map(
            (note) => Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Text(
                note,
                style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
              ),
            ),
          ),
        if (report.isEmpty)
          Text(
            'No artist signal. Try a NovelAI PNG, a Danbooru image, or a '
            'clearer single-subject crop.',
            style: TextStyle(color: t.secondaryText),
          ),
      ],
    );
  }

  TextStyle _sectionStyle(dynamic t) => TextStyle(
        color: t.accent,
        letterSpacing: 1.4,
        fontWeight: FontWeight.bold,
        fontSize: t.fontSize(11),
      );
}

class _HitTile extends StatelessWidget {
  const _HitTile({required this.hit});

  final StyleMatchHit hit;

  @override
  Widget build(BuildContext context) {
    final t = context.t;
    final label = ArtistMixEngine.promptName(hit.name);
    final source = switch (hit.source) {
      StyleMatchSource.metadata => 'metadata',
      StyleMatchSource.iqdb => 'iqdb ${(hit.score * 100).round()}%',
      StyleMatchSource.visual => 'visual ${(hit.score * 100).round()}%',
    };
    final strength = hit.strength == ArtistStrength.unknown
        ? ''
        : ' · ${hit.strength.shortLabel}';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: SizedBox(
        width: 48,
        height: 64,
        child: Image.network(
          ArtistPreviewUrls.primary(NaxStrengthCatalog.normalizeTag(hit.name)),
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Icon(Icons.brush, color: t.secondaryText),
        ),
      ),
      title: Text(label),
      subtitle: Text(
        '$source$strength${hit.detail != null ? ' · ${hit.detail}' : ''}',
        style: TextStyle(color: t.secondaryText, fontSize: t.fontSize(11)),
      ),
      trailing: IconButton(
        icon: const Icon(Icons.add),
        tooltip: 'Add as primary',
        onPressed: () {
          final rendered = WeightEngine.renderEntry(
            TagChip(
              tag: hit.name,
              kind: TagKind.artist,
              numericWeight: hit.strength.primaryEmphasis,
            ),
          );
          if (rendered == null) return;
          NvPromptBridge.appendInPlace(
            context,
            rendered,
            snackbar: 'Added $rendered',
          );
        },
      ),
    );
  }
}

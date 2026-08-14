import '../services/nax_strength_service.dart';
import '../services/style_match_service.dart';
import 'artist_mix_engine.dart';
import 'style_fingerprint.dart';
import 'style_mix_planner.dart';

/// What Nv already knows about V4.5 artist mixes — the contract any
/// external helper (Grok, a script, another app) must follow.
class StyleKnowledge {
  StyleKnowledge._();

  static const version = '1.1.0';

  static const rules = <String>[
    'Rank artists by nax.moe V4.5 style-pull (Strong/Solid/Mixed/Weak), not Danbooru post count.',
    'Never lead with a Weak V4.5 tag. Prefer Solid+ for the lead.',
    'Emit NovelAI numeric emphasis only: 1.1::artist:name:: lead, 0.8:: support, 0.7:: accent. No {} or [].',
    'Source IDs (NovelAI PNG metadata or Danbooru IQDB) beat visual guesses.',
    'If the lead sits in a community-tested seed triple for the same bucket, use that triple.',
    'Support should be a known mixer/glue or same-bucket artist, not a clone of the lead.',
    'Accent is complementary (ink vs paint, or sat), not the 3rd-nearest neighbor.',
    'Visual look-alikes are similar rendering, not proof of authorship.',
    'Do not invent popular-but-untested artists. Stay inside the provided catalog / source hits / glue / triples.',
    'Ignore subject color (hair, hoodie, background). Match ink, saturation, and contrast.',
  ];

  static Map<String, dynamic> catalogPayload() {
    final cat = ArtistMixEngine.catalog;
    return {
      'app': 'Nv',
      'version': version,
      'rules': rules,
      'emphasis': {
        'lead': 1.1,
        'support': 0.8,
        'accent': 0.7,
        'form': 'w::artist:name::',
      },
      'nax_artists': NaxStrengthCatalog.instance.size,
      'glue': cat.glue.map(ArtistMixEngine.promptName).toList(),
      'buckets': {
        for (final e in cat.buckets.entries)
          e.key: e.value.map(ArtistMixEngine.promptName).toList(),
      },
      'triples': [
        for (final t in cat.triples)
          {
            'style': t.style,
            'artists': t.artists.map(ArtistMixEngine.promptName).toList(),
          },
      ],
    };
  }

  static Map<String, dynamic> artistPayload(String tag, NaxStrengthEntry entry) {
    return {
      'tag': tag,
      'name': ArtistMixEngine.promptName(tag),
      'nax_score': entry.score,
      'nax_votes': entry.votes,
      'strength': entry.strength.shortLabel,
      'rank': entry.rankScore.isFinite
          ? double.parse(entry.rankScore.toStringAsFixed(3))
          : null,
    };
  }

  static Map<String, dynamic> briefFromReport(StyleMatchReport report) {
    final plan = report.plan;
    final bucket = plan?.brief.bucketHint;
    final cat = ArtistMixEngine.catalog;
    final triples = bucket == null
        ? const <ArtistMixTriple>[]
        : cat.triples.where((t) => t.style == bucket).toList();
    return {
      'app': 'Nv',
      'version': version,
      'rules': rules,
      'read': plan == null
          ? null
          : {
              'family': plan.brief.family,
              'bucket': plan.brief.bucketHint,
              'summary': plan.brief.summary,
              'observations': plan.brief.observations,
              'edge': _r(plan.brief.edge),
              'fine': _r(plan.brief.fine),
              'strong': _r(plan.brief.strong),
              'sat': _r(plan.brief.satMean),
              'contrast': _r(plan.brief.contrast),
            },
      'local_plan': {
        'mix': report.mix,
        'picks': [
          for (final p in plan?.picks ?? const <PlannedPick>[])
            {
              'name': ArtistMixEngine.promptName(p.name),
              'role': p.role.name,
              'reason': p.reason,
              'strength': p.hit.strength.shortLabel,
            },
        ],
        'steps': plan?.steps ?? report.notes,
      },
      'source_hits': [
        for (final h in report.sourceHits) _hitJson(h),
      ],
      'also_considered': [
        for (final h in report.visualHits.take(8)) _hitJson(h),
      ],
      'relevant_triples': [
        for (final t in triples.take(8))
          {
            'style': t.style,
            'artists': t.artists.map(ArtistMixEngine.promptName).toList(),
          },
      ],
      'glue': cat.glue.map(ArtistMixEngine.promptName).toList(),
    };
  }

  static Map<String, dynamic> _hitJson(StyleMatchHit hit) {
    return {
      'name': ArtistMixEngine.promptName(hit.name),
      'source': hit.source.name,
      'score': _r(hit.score),
      'strength': hit.strength.shortLabel,
      'nax_score': hit.naxScore,
      'nax_votes': hit.naxVotes,
      'detail': hit.detail,
    };
  }

  static double _r(double n) => double.parse(n.toStringAsFixed(3));
}

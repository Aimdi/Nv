# NAI Companion

An offline NovelAI prompt-composition companion for Android. Compose prompts
with correct NovelAI weighting syntax, keep a local prompt/combo library, and
browse 15,000 artist tags with instant FTS search — fully offline, then
copy → paste into the NovelAI mobile web app/PWA.

Native Kotlin + Jetpack Compose. No account, no network calls, no tracking.

## Features

**1. Combo builder + clipboard**
- Ordered tag list — order matters in NovelAI (earlier tags have higher priority)
- Per-tag weight controls in a bottom sheet:
  - `{tag}` / `[tag]` brace-bracket stepper (×1.05 per pair, nestable)
  - `x.x::tag::` numeric emphasis slider (negative weights for V4.5+)
  - enable/disable without deleting
- Danbooru underscores are rendered as spaces, per NovelAI guidance
- Long-press drag-to-reorder plus move up/down fallbacks
- One-tap **Copy** and **Copy & open NovelAI** (copies, then launches the
  NovelAI image page in the browser/PWA)
- Artist-tag autocomplete while typing (from the bundled catalog)
- Optional `artist:` prefix for V4/V4.5-style artist tags

**2. Prompt library**
- Saved prompts and saved combos in local Room/SQLite with FTS4 search
- Favorites, load-into-builder, copy, edit, delete
- **JSON export/import** via the Storage Access Framework (versioned schema,
  doubles as backup; artist favorites export by tag name so backups survive
  catalog rebuilds)

**3. Tag browser**
- 15,000 Danbooru artist tags (all with >94 posts) seeded on first launch
- FTS4 prefix search as you type, sort by post count or name, favorites filter
- Long-press to copy the raw tag, one tap for details + "add to combo"
- Preview images are phase 4 (download-on-first-run WebP pack); the schema
  (`thumbnailPath`) and the screen structure are already in place

## Building

Requires Android SDK (platform 35, build-tools 35) and JDK 17+.

```bash
# sdk.dir must point at your SDK (local.properties) or ANDROID_HOME be set
./gradlew :app:assembleDebug      # build APK
./gradlew :app:testDebugUnitTest  # run JVM unit tests (renderer/parser/backup)
```

Install the debug APK with `adb install app/build/outputs/apk/debug/app-debug.apk`.

Release APKs are signed and attached to GitHub Releases; add the repo to
[Obtainium](https://github.com/ImranR98/Obtainium) for auto-updates.

## Data & licenses

- `app/src/main/assets/artists.json` — from
  [deus-ex-machina/novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison),
  Apache-2.0. Re-fetch with `python3 tools/build_tag_catalog.py`.
- Planned phase-4 preview packs: the same dataset's NAI-v3 samples
  (Apache-2.0) and ThetaCursed's Illustrious/NoobAI Style Explorer (MIT),
  re-compressed to ~300px WebP thumbnails.
- Preview style-match caveat: V3-era and Illustrious/NoobAI previews do not
  exactly match NovelAI V4.5 behavior; sources are labeled in-app.

## Architecture

Single-activity Compose app with three tabs (Builder / Library / Tags).

- `render/` — the NovelAI syntax renderer + parser (the correctness-critical
  core, covered by JVM unit tests)
- `data/db/` — Room database: `prompts` (+FTS4), `combos`, read-only
  `artist_tags` catalog (+FTS4) seeded from bundled JSON, `artist_favorites`
- `data/backup/` — versioned JSON export/import schema
- `ui/` — Compose screens; the Builder view-model is activity-scoped so the
  combo survives tab switches

## Roadmap

- [x] Phase 1 — combo builder + clipboard
- [x] Phase 2 — prompt library + JSON export/import
- [x] Phase 3 — tag catalog + FTS4 search
- [ ] Phase 4 — WebP preview pack, download-on-first-run, thumbnail grid,
      detail sheet with large preview, swipe discovery mode
- [ ] Phase 5 — signed release on GitHub Releases + Obtainium, optional
      expansion packs

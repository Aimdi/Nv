# NAI Prompt Companion

An **offline NovelAI prompt-composition companion** for Android, built in Kotlin + Jetpack
Compose. NovelAI on mobile is a PWA/website, so the real workflow is:

> compose tags here → one-tap copy → paste into the NovelAI PWA

Everything works fully offline. The only network feature is an *optional* one-time download
of the preview image pack.

## Features

### 1. Combo builder (core)
- Chip row of tags with **long-press drag-to-reorder** (order matters in NovelAI — earlier
  tags have higher priority).
- Tap a chip → bottom sheet with a **brace/bracket stepper** (`{tag}` = ×1.05, `[tag]` = ÷1.05),
  a **numeric emphasis slider** (`1.3::tag::`, including negative weights for V4.5+),
  enable/disable, move and delete.
- Live preview renders correct NovelAI syntax: underscore→space conversion, `artist:` prefixes,
  `w::tag::` sections.
- One-tap **Copy**, **Copy + open NovelAI**, and Android share-sheet actions.
- **Parse clipboard** to turn an existing prompt back into editable chips.
- Autocomplete suggestions from the 15,000-tag artist catalog as you type.

### 2. Prompt library
- Room/SQLite storage with **FTS4 full-text search** over prompt titles and bodies.
- Saved combos (ordered tag lists), favorite stars, "load into builder".
- **JSON export/import** via the Storage Access Framework (versioned schema, doubles as backup).

### 3. Artist tag browser with previews
- Bundled catalog of **15,000 Danbooru artist tags** with post counts
  (`deus-ex-machina/novelai-anime-v3-artist-comparison`, Apache-2.0), seeded into a Room
  database with an FTS4 prefix-search table on first run.
- `LazyVerticalGrid` of previews with long-press quick actions (add to combo / copy / favorite),
  detail bottom sheet, and a full-screen **swipe mode** for rapid style discovery.
- Preview images are **not in the APK**: they are an optional download-on-first-run WebP
  thumbnail pack (~150–350 MB) stored in app-private storage — immune to the browser
  quota/eviction problems that rule out a PWA for this use case.
- A handful of bundled placeholder previews demo the UX before any pack is installed.

## Building

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35).

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # signed release APK
./gradlew :app:testDebugUnitTest      # renderer/parser/backup unit tests
```

Release signing defaults to the debug keystore so `assembleRelease` works out of the box.
For real distribution, export:

```bash
export NAI_KEYSTORE=/path/to/keystore.jks
export NAI_KEYSTORE_PASSWORD=...
export NAI_KEY_ALIAS=...
export NAI_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## The preview image pack

Build a pack from any directory of `<artist_tag>.<ext>` images (e.g. the dataset's
`images.zip` contents or ThetaCursed's Illustrious/NoobAI explorer release):

```bash
python3 tools/build_imagepack.py --input /path/to/raw_images --output imagepack --version 1
```

This produces `imagepack/manifest.json`, `thumb/*.webp` (~320 px, q75) and
`detail/*.webp` (~768 px, q80) with per-file SHA-256 integrity hashes. Host the directory on
any static host (e.g. a GitHub Release asset bundle), then either:

- bake the manifest URL into the build: `./gradlew assembleRelease -PNAI_IMAGEPACK_URL=https://…/manifest.json`, or
- paste the URL in the app: **Tags tab → ⬇ icon → manifest.json URL → Download**.

Downloads are resumable per file, integrity-checked, and cancellable.

## Distribution (personal use)

Cut a signed release APK and attach it to a GitHub Release, then add the repo to
[Obtainium](https://github.com/ImranR98/Obtainium) on your phone for Play-Store-like
auto-updates. The image pack can be versioned independently as a separate release asset.

## Data & attribution

- `app/src/main/assets/seed/artists.json` —
  [deus-ex-machina/novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison),
  **Apache-2.0**. 15,000 Danbooru artist tags (>94 posts each) with NovelAI v3 sample images.
  Note these are **V3-era** previews; NovelAI V4.5 reproduces some artists differently —
  preview sources are labeled in the UI (`nai-v3`, `illustrious-noobai`, …).
- Compatible with ThetaCursed's
  [Illustrious/NoobAI Style Explorer](https://github.com/ThetaCursed/Illustrious-NoobAI-Style-Explorer)
  data (**MIT**) via `tools/build_imagepack.py`.
- Re-fetch the seed any time with `tools/fetch_seed_data.sh`.

This app is an offline composer only: it never talks to NovelAI (which has no public API)
and generates nothing.

## Project layout

```
app/src/main/java/com/nai/promptcompanion/
├── novelai/NovelaiSyntax.kt     # NovelAI renderer + parser (heavily unit-tested)
├── data/
│   ├── user/                    # Room DB: prompts (FTS4), combos, favorite tags
│   ├── catalog/                 # Room DB: artist tags + FTS4, first-run JSON seeder
│   ├── backup/                  # versioned JSON export/import via SAF
│   ├── imagepack/               # download-on-first-run WebP pack manager
│   └── prefs/                   # DataStore settings (builder draft, pack URL)
└── ui/
    ├── builder/                 # chip row + drag reorder + weight sheet + copy
    ├── tags/                    # grid browser, detail sheet, swipe mode, pack UI
    └── library/                 # prompts + combos + export/import
```

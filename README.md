# Prompt Companion

Offline NovelAI prompt-composition companion for Android.

Compose tags with correct NovelAI weighting syntax, save combos/prompts locally, browse a bundled artist catalog (FTS search), and optionally download a WebP preview pack into app-private storage.

## Why native?

NovelAI on mobile is a PWA/website. The real workflow is **compose → copy → paste into NovelAI**. A native APK gives reliable clipboard/share integration and stores a large offline thumbnail library without Chrome IndexedDB eviction.

## Features (MVP)

1. **Combo builder** — chip row, weight stepper (`{}`/`[]`), numeric `x.x::tag::` slider, drag reorder, live prompt preview, one-tap copy / share / copy+open NovelAI
2. **Prompt library** — Room-backed prompts & combos, favorites, FTS search, JSON export/import via Storage Access Framework
3. **Artist browser** — ~15k artists from `deus-ex-machina/novelai-anime-v3-artist-comparison` (`artists.json`, Apache-2.0) in a Room + FTS4 catalog; optional download-on-first-run WebP pack (not in the APK)

## Build

```bash
export ANDROID_HOME=~/android-sdk   # or your SDK path
./gradlew :app:assembleDebug
./gradlew test
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install / updates

Sideload the signed release APK from GitHub Releases. Track updates with [Obtainium](https://github.com/ImranR98/Obtainium).

## Preview pack

Thumbnails are **not** bundled. In **Settings**, set a ZIP URL of `*.webp` files named like `{sanitized_artist_name}.webp` and download into app storage (~150–350 MB target after recompressing source previews to ~300 px WebP).

Default pack URL is a placeholder until a release asset is published.

## Data attributions

- [novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison) — Apache-2.0 (NAI v3 artist list)
- Optional future pack: [Illustrious-NoobAI-Style-Explorer](https://github.com/ThetaCursed/Illustrious-NoobAI-Style-Explorer) — MIT (label source separately; SDXL styles ≠ NAI V4.5)

## Scope

Offline composer only — no NovelAI API calls.

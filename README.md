# NaiComposer

Offline NovelAI prompt-composition companion for Android.

Compose tags with NovelAI weighting (`{}` / `[]` / `1.5::tag::`), save prompts & combos, browse ~15k artist tags with optional WebP preview pack. Copy → paste into the NovelAI PWA.

## Features (MVP)

1. **Combo builder** — chip row, brace/bracket stepper, numeric weights, drag-reorder, one-tap copy / copy+open NovelAI / share
2. **Prompt library** — Room-backed prompts & combos, FTS search, JSON export/import via SAF
3. **Artist browser** — seeded from [`deus-ex-machina/novelai-anime-v3-artist-comparison`](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison) (`artists.json`, Apache-2.0), FTS4 search; optional download-on-first-run preview pack

## Build

```bash
# Requires JDK 17+ and Android SDK (platform 35)
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test
```

## Preview pack

Previews are **not** in the APK. Build a phone-sized WebP pack from a source image folder:

```bash
pip install Pillow
python scripts/build_preview_pack.py --input /path/to/images --output ./preview_pack_out
# Upload preview_pack_out/preview_thumbs.zip as a GitHub Release asset
# Point Settings → Pack URL at that asset
```

Target ~300 px thumbs / ~768 px detail, WebP q≈75. Expect ~150–350 MB for 16k artists.

## Distribution

Signed release APK on GitHub Releases + [Obtainium](https://github.com/ImranR98/Obtainium) for sideload updates.

## License / attribution

- App code: see repository license
- Artist list: Apache-2.0, deus-ex-machina/novelai-anime-v3-artist-comparison
- Optional Illustrious/NoobAI previews: MIT, ThetaCursed/Illustrious-NoobAI-Style-Explorer (label source in UI)

This app does **not** call the NovelAI API.

# Nv

One NovelAI app for phone and desktop — **generation + offline prompt tools**, not two separate clients.

Nv unifies:

1. **[NAIWeaver](https://github.com/ststoryweaver/NAIWeaver)** (MIT) — full Flutter frontend for NovelAI image/text APIs, gallery, characters, canvas, ML tools, and more  
2. **Aimdi prompt tools** — chip composer, HuggingFace artist browser, live Danbooru autocomplete enrichment

Android application id: `com.aimdi.nv`

## What you get

### From NAIWeaver
- NovelAI V4.5 image generation (txt2img, img2img, inpaint, vibe transfer, director reference, multi-character)
- Text generation, characters/wardrobe/photoshoot, cascade scenes
- Gallery, packs (`.vpack`), wildcards, presets, styles, themes
- On-device ML (BG remove / upscale / SAM), director tools, EN/JA/ZH

### From Aimdi/Nv (Tools hub)
- **Chip Composer** — reorderable weighted chips (`{}` / `[]` / `w::tag::`, `artist:`), local + live Danbooru suggestions, one-tap copy  
- **Artist Browser** — offline artist tags with on-demand SFW HuggingFace previews (`novelai-anime-v3-artist-comparison`)

## Requirements

- NovelAI API key (`pst-…`) for generation features  
- Flutter SDK ^3.10 (stable)  
- Android SDK 36+ for Android builds

Chip Composer and Artist Browser work offline for composition/browsing; previews/search enrichment need network when enabled.

## Quick start

```bash
flutter pub get
flutter run                 # connected device / emulator
flutter build apk --release # Android APK
flutter test
```

## Configuration

1. Launch Nv  
2. **Tools → Settings** → paste your NovelAI API key  
3. Generate on the home screen, or open **Tools → Chip Composer / Artist Browser** for prompt work

## Attribution

- Core client: © NAIWeaver Contributors — MIT (`LICENSE`, `NOTICE`)  
- Chip composer / HF artist browser integration: Aimdi/Nv  
- Artist preview samples: Apache-2.0, [deus-ex-machina/novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison)  
- Wiki tag descriptions (if enabled): see `Tags/LICENSE-WIKI.txt` (CC-BY-SA-4.0)

## Project docs

Upstream NAIWeaver docs remain useful: `FEATURES.md`, `ARCHITECTURE.md`, `API_DOCUMENTATION.md`, `CONTRIBUTING.md`.

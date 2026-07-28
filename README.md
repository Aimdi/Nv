# Nv

Offline-first NovelAI prompt composer for Android.

Compose weighted tags on your phone, copy the prompt, and paste it into the NovelAI PWA. **Nv never calls a NovelAI generation API** — that keeps the workflow ToS-safe.

## Features

1. **Chip builder** — NovelAI weighting (`{}` / `[]` / `1.5::tag::`), `artist:` prefix on V4+, underscore→space, drag-reorder, one-tap copy + Snackbar, optional open-NovelAI
2. **Hybrid search** — offline Room FTS over a bundled ~17k artist catalog, merged with live [Danbooru autocomplete](https://danbooru.donmai.us/) (debounced 250 ms, cached, HTTP 429 backoff)
3. **Artist browser** — on-demand SFW previews from the HuggingFace [novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison) dataset via Coil (disk-cached by artist name), with optional offline preview packs
4. **Prompt library** — favorites, FTS, versioned JSON export/import
5. **Settings** — model target (V3 / V4 / V4.5), online enrichment toggles, conservative NSFW filter (default off)

## Stack

Kotlin, Jetpack Compose, Room (+ FTS4), DataStore, Retrofit + OkHttp, Coil. Dependency graph is a small hand-rolled container (same shape as Hilt modules).

Application id: `com.aimdi.nv`

## Install

Download **[`nv-v0.2.0.apk`](https://github.com/Aimdi/Nv/releases/download/v0.2.0/nv-v0.2.0.apk)** from [Releases](https://github.com/Aimdi/Nv/releases) and sideload it. For updates, add this repository in [Obtainium](https://github.com/ImranR98/Obtainium).

## Build

Needs JDK 17+ and the Android SDK (compile/target 35, min 26).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Weighting engine

`WeightEngine` / `NovelAiPromptRenderer` implement NovelAI’s documented rules:

| Syntax | Effect |
| --- | --- |
| `{tag}` / `{{tag}}` | ×1.05 / ×1.1025 attention |
| `[tag]` / `[[tag]]` | ÷1.05 per level |
| `1.5::tag::` | numeric emphasis (negatives on V4.5+) |
| `artist:name` | V4+ artist style flag |
| underscores → spaces | default on export |

## Online vs offline

| Concern | Behaviour |
| --- | --- |
| Generation | Always manual (clipboard → NovelAI) |
| Tag search | Offline catalog first; Danbooru when “Live Danbooru tag search” is on |
| Artist images | HuggingFace CDN on demand (cached); optional zip packs for full offline |
| NSFW | `rating:*` suggestions other than general hidden unless explicitly enabled |

## Preview packs

Optional offline WebP packs can still be built and installed from Settings → Packs. See `tools/build_pack.py`.

## License / attribution

- App code: see repository license
- Artist list & SFW samples: Apache-2.0, deus-ex-machina/novelai-anime-v3-artist-comparison
- Danbooru autocomplete: public read API (rate-limit politely)

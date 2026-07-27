# NAI Companion

An offline Android app for composing NovelAI image prompts. It builds prompts with the correct
emphasis syntax, keeps a searchable library of what you have written, and lets you browse a
catalog of 17,228 artist tags with optional preview images.

It never contacts NovelAI and cannot generate images. NovelAI on mobile is a website/PWA, so the
workflow this app is built around is: compose here, copy, paste into the NovelAI prompt box.

## Features

**Combo builder.** Add tags by searching the bundled catalog, typing them, or pasting an existing
prompt. Each tag carries its own emphasis, and the rendered NovelAI string updates live above a
one-tap copy button.

- `{tag}` / `[tag]` bracket nesting, stepped one x1.05 level at a time
- `1.5::tag::` numeric emphasis on a slider, including negative weights on V4.5
- `artist:` prefix applied automatically to artist tags on V4 and newer
- underscores converted to spaces, which NovelAI responds to noticeably differently
- drag-to-reorder, because tags earlier in a prompt are weighted more heavily
- warnings for duplicate tags, and for syntax the selected model does not support

**Library.** Saved prompts and saved combos, with full-text search, favourites, folders, and
versioned JSON export/import through the system file picker so backups outlive the app.

**Tag browser.** The bundled catalog is searchable offline with no images installed. Install a
preview pack and the grid fills with thumbnails; there is also a full-screen swipe mode for
working through unfamiliar artists quickly.

**Sharing.** Copy to clipboard, share-sheet target, an optional "copy and open NovelAI" button,
and the app registers as a share/process-text target so a prompt from anywhere lands in the
builder.

## Installing

Download **[`nai-companion-v0.1.0.apk`](https://github.com/Aimdi/Nv/releases/download/v0.1.0/nai-companion-v0.1.0.apk)**
from [Releases](https://github.com/Aimdi/Nv/releases) and sideload it. For updates, add this
repository in [Obtainium](https://github.com/ImranR98/Obtainium).

The release APK is around 5.4 MB, including the 3.3 MB tag catalog. Preview images are downloaded
separately, on request.

## Building

Needs JDK 17+ and the Android SDK (compile/target 35, min 26).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # 137 unit tests
./gradlew lintDebug
./gradlew assembleRelease      # falls back to the debug key when unsigned
```

For a signed release, either create `keystore.properties` at the repo root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

or set `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` in the environment. The release workflow uses the environment form, reading
the keystore from the `ANDROID_KEYSTORE_BASE64` secret.

Obtainium identifies apps partly by signature, so **every release must use the same signing key**.
`v0.1.0` was signed with a dedicated release keystore (alias `nai-companion`). Store that keystore
as GitHub Actions secrets before cutting the next release:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of the `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `nai-companion` |
| `ANDROID_KEY_PASSWORD` | key password |

Then tag a new version (`git tag v0.1.1 && git push origin v0.1.1`) and the release workflow will
publish a signed APK.

## Architecture

Kotlin, Jetpack Compose, Room, and a hand-rolled dependency container (the graph is small enough
that a DI framework would only add build time).

```
core/prompt      TagEntry model, renderer and parser. No Android dependencies, heavily tested.
data/catalog     Read-only artist catalog: Room entities, FTS4 search, SQL query builder.
data/user        Prompts, combos and favourites, plus FTS4 search over the library.
data/packs       Preview-pack download, verification, extraction and lookup.
data/backup      Versioned JSON export/import.
data/settings    DataStore-backed preferences and builder draft.
ui/…             Compose screens and view models, one package per screen.
tools/           Python scripts that build the catalog asset and the preview packs.
```

Two Room databases rather than one. The catalog is read-only and ships as an asset, so a future
release can replace it wholesale without touching saved prompts, combos or favourites; those live
in a separate user database. Favourites reference catalog rows by tag name, since a cross-database
join is not possible.

### Prompt rendering

Bracket nesting and numeric emphasis are independent and compose, so a tag with both renders as
`1.3::{{ocean}}::`, which the parser reads back into the same tag. The renderer and parser are
covered by 50 tests including round-trip stability.

## Data

The catalog merges two permissively licensed datasets into one row per artist:

| Source | License | Artists |
| --- | --- | --- |
| [`deus-ex-machina/novelai-anime-v3-artist-comparison`](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison) | Apache-2.0 | 14,999 |
| [`ThetaCursed/Illustrious-NoobAI-Style-Explorer`](https://github.com/ThetaCursed/Illustrious-NoobAI-Style-Explorer) | MIT | 16,006 |

13,777 artists appear in both, giving 17,228 unique rows. Each row records every dataset that
covers it, so filtering by source stays accurate.

Preview images are **not** from the artists themselves: they are model-generated samples of what
each tag produces, and different NovelAI versions render the same tag differently. The NAI v3
samples match this app's purpose most closely; the Illustrious/NoobAI samples are SDXL and will
differ. The browser labels the source of every row.

### Regenerating the catalog

```bash
curl -LO https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison/resolve/main/artists.json
curl -LO https://raw.githubusercontent.com/ThetaCursed/Illustrious-NoobAI-Style-Explorer/main/app/data.js

python3 tools/build_catalog.py \
    --nai artists.json \
    --illustrious data.js \
    --out app/src/main/assets/catalog/catalog.db
```

The script reads the schema JSON that Room's annotation processor exports and replays its exact
DDL, indices and identity hash, so the generated file passes Room's validation on first open.
Change a catalog entity and you must rebuild the asset; `CatalogDatabaseTest` opens the committed
asset through Room and fails if the two have drifted.

### Building a preview pack

Packs are zips of WebP images named after the canonical Danbooru tag, so any pack can supply the
image for any catalog row. Get the source images first:

```bash
# NAI v3 previews (~3.7 GB of JPEGs), named after the tag already
huggingface-cli download deus-ex-machina/novelai-anime-v3-artist-comparison \
    --repo-type dataset --include 'images/*' --local-dir work/nai

# Illustrious previews, named by numeric id; data.js maps them back to tags
git clone --depth 1 https://github.com/ThetaCursed/Illustrious-NoobAI-Style-Explorer work/ill
```

```bash
python3 tools/build_pack.py \
    --input work/nai/images \
    --out dist/nai-v3-previews.zip \
    --id nai-v3-previews --name "NAI v3 artist previews" --source nai-v3 \
    --license Apache-2.0 --attribution "deus-ex-machina/novelai-anime-v3-artist-comparison" \
    --catalog app/src/main/assets/catalog/catalog.db \
    --manifest dist/packs.json

# The Illustrious set needs its id-to-name map
python3 tools/build_pack.py \
    --input work/ill/images --name-map work/ill/app/data.js \
    --out dist/illustrious-previews.zip \
    --id illustrious-previews --name "Illustrious artist previews" --source illustrious \
    --license MIT --attribution "ThetaCursed/Illustrious-NoobAI-Style-Explorer" \
    --catalog app/src/main/assets/catalog/catalog.db \
    --manifest dist/packs.json
```

Upload the zips and `packs.json` to a release, and point Settings → Preview packs at the manifest
URL. You can skip hosting entirely: copy a zip to the phone and use "Import from a file".

#### Pack sizes

Measured on the NAI v3 previews (832x1216 JPEGs averaging 253 kB), extrapolated to all 17,228
catalog rows:

| Long edge | Quality | Avg/image | Whole catalog |
| --- | --- | --- | --- |
| 256 px | 70 | 10.3 kB | 178 MB |
| 320 px | 75 | 15.3 kB | 263 MB |
| 384 px | 75 | 19.9 kB | 343 MB |
| 512 px | 80 | 34.8 kB | 600 MB |
| 768 px | 80 | 60.3 kB | 1039 MB |

Thumbnails at the 320 px default are the sweet spot. Detail images are large enough that they do
not belong in the same pack, so `--full-px` defaults to 0; build them as a separate pack if you
want them, and the detail sheet falls back to the thumbnail when they are absent.

Packs land in app-private storage, which is not subject to the quota and LRU-eviction rules that
make a browser cache unsuitable for this much data. Downloads resume after interruption and are
checked against a SHA-256 before being unpacked.

## Testing

137 unit tests, all runnable on the JVM with `./gradlew testDebugUnitTest`. Robolectric is used
where a real SQLite database matters, so the catalog, user database, backup and pack code are
exercised against real storage rather than mocks.

No emulator was available while this was written (the build machine had no KVM), so on-device
gesture behaviour — drag-to-reorder, the swipe pager, bottom sheets — is unverified. The prompt
syntax, database, search, backup and pack-install layers are covered directly.

## Licence and scope

The app is a personal-use offline reference tool. Danbooru artist tags and AI-generated previews
carry the artist-consent concerns that surround this space generally; be thoughtful about
redistributing large preview packs publicly. Attribute the upstream datasets if you do.

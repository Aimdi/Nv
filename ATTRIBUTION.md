# Third-party data attribution

## Artist catalog (bundled)

- **Source:** [deus-ex-machina/novelai-anime-v3-artist-comparison](https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison)
- **Artifact:** `artists.json` (~15,000 Danbooru artist tags with post counts; NAI v3 sample set)
- **License:** Apache License 2.0
- **Notes:** Previews in that dataset are NovelAI **V3**-era. V4.5 may render some artists differently. This app ships the tag list only; preview images are an optional separate download.

## Optional preview packs (not bundled)

- ThetaCursed / Illustrious-NoobAI-Style-Explorer — MIT licensed WebP style previews (SDXL Illustrious/NoobAI, not NovelAI). Label source in UI when used.
- Compress with `scripts/build_preview_pack.py` before hosting as a Release asset.

## Disclaimer

NaiComposer is an offline prompt composition tool. It does not call the NovelAI API and is not affiliated with NovelAI / Anlatan.

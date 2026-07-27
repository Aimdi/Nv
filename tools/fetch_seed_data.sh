#!/usr/bin/env bash
# Re-fetches the bundled artist-tag seed dataset.
#
# Source: deus-ex-machina/novelai-anime-v3-artist-comparison (Apache-2.0)
# 15,000 Danbooru artist tags (>94 posts each) with NovelAI v3 sample images.
# https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison
set -euo pipefail

DEST="$(dirname "$0")/../app/src/main/assets/seed/artists.json"
curl -fSL -o "$DEST" \
  "https://huggingface.co/datasets/deus-ex-machina/novelai-anime-v3-artist-comparison/resolve/main/artists.json"
echo "updated $DEST ($(stat -c%s "$DEST") bytes)"

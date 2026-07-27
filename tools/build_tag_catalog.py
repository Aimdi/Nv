#!/usr/bin/env python3
"""Fetch and rebuild the bundled artist-tag catalog.

Downloads artists.json from the Apache-2.0 dataset
`deus-ex-machina/novelai-anime-v3-artist-comparison` (all Danbooru artist
tags with >94 posts, 15,000 NAI-v3-generated preview samples) and writes it
to app/src/main/assets/artists.json, which the app seeds into its Room
database (with an FTS4 index) on first launch.

Phase 4 (preview images) will extend this script to:
  1. download the preview sets (this dataset's images.zip and/or
     ThetaCursed's MIT-licensed Illustrious/NoobAI explorer WebPs),
  2. re-encode them to ~300px WebP thumbnails (~75 quality) plus ~768px
     detail images,
  3. emit a versioned image pack for download-on-first-run hosting.

Usage:
    python3 tools/build_tag_catalog.py [--out app/src/main/assets/artists.json]
"""

import argparse
import json
import sys
import urllib.request

ARTISTS_URL = (
    "https://huggingface.co/datasets/deus-ex-machina/"
    "novelai-anime-v3-artist-comparison/resolve/main/artists.json"
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="app/src/main/assets/artists.json")
    parser.add_argument("--url", default=ARTISTS_URL)
    args = parser.parse_args()

    print(f"Fetching {args.url}")
    with urllib.request.urlopen(args.url, timeout=60) as response:
        data = json.loads(response.read().decode("utf-8"))

    if not isinstance(data, list) or not all(
        {"id", "name", "post_count"} <= set(item) for item in data
    ):
        print("error: unexpected schema; expected [{id, name, post_count}, ...]")
        return 1

    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))

    print(f"Wrote {len(data)} artist tags to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

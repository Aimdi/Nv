#!/usr/bin/env python3
"""Build a downloadable preview-image pack for NAI Prompt Companion.

Input:  a directory of preview images named `<artist_tag>.<ext>` (e.g. the
        deus-ex-machina novelai-anime-v3-artist-comparison `images.zip`
        contents, or ThetaCursed's Illustrious/NoobAI explorer release).
Output: an `imagepack/` directory with

    manifest.json           version + per-file size/sha256
    thumb/<artist_tag>.webp ~320 px, quality 75   (grid)
    detail/<artist_tag>.webp ~768 px, quality 80  (detail sheet / swipe mode)

Host the whole directory on any static host (e.g. attach a zip to a GitHub
Release, or serve the raw files) and point the app at `.../manifest.json`.
A 320 px WebP thumbnail is ~10–25 KB, so 16,000 artists land in the
~150–350 MB range.

Usage:
    python3 tools/build_imagepack.py --input /path/to/raw_images \
        --output imagepack --version 1 --name "nai-v3-artists"
"""

import argparse
import hashlib
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor

from PIL import Image

THUMB_EDGE = 320
DETAIL_EDGE = 768
THUMB_QUALITY = 75
DETAIL_QUALITY = 80
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp"}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            digest.update(chunk)
    return digest.hexdigest()


def reencode(src, dst, long_edge, quality):
    img = Image.open(src).convert("RGB")
    img.thumbnail((long_edge, long_edge), Image.LANCZOS)
    img.save(dst, "WEBP", quality=quality, method=6)


def process_one(args):
    src, tag, out_dir = args
    thumb_path = os.path.join(out_dir, "thumb", f"{tag}.webp")
    detail_path = os.path.join(out_dir, "detail", f"{tag}.webp")
    try:
        reencode(src, thumb_path, THUMB_EDGE, THUMB_QUALITY)
        reencode(src, detail_path, DETAIL_EDGE, DETAIL_QUALITY)
        return [
            {
                "path": f"thumb/{tag}.webp",
                "size": os.path.getsize(thumb_path),
                "sha256": sha256(thumb_path),
            },
            {
                "path": f"detail/{tag}.webp",
                "size": os.path.getsize(detail_path),
                "sha256": sha256(detail_path),
            },
        ]
    except Exception as e:  # noqa: BLE001 - report and continue with the rest
        print(f"skip {tag}: {e}", file=sys.stderr)
        return []


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="directory of raw preview images")
    parser.add_argument("--output", default="imagepack", help="output pack directory")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--name", default="nai-v3-artists")
    parser.add_argument("--jobs", type=int, default=os.cpu_count() or 4)
    args = parser.parse_args()

    os.makedirs(os.path.join(args.output, "thumb"), exist_ok=True)
    os.makedirs(os.path.join(args.output, "detail"), exist_ok=True)

    jobs = []
    for entry in sorted(os.listdir(args.input)):
        stem, ext = os.path.splitext(entry)
        if ext.lower() in IMAGE_EXTS and stem:
            jobs.append((os.path.join(args.input, entry), stem, args.output))

    print(f"re-encoding {len(jobs)} images with {args.jobs} workers…")
    files = []
    with ProcessPoolExecutor(max_workers=args.jobs) as pool:
        for result in pool.map(process_one, jobs, chunksize=16):
            files.extend(result)

    manifest = {"version": args.version, "name": args.name, "files": files}
    manifest_path = os.path.join(args.output, "manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, separators=(",", ":"))

    total = sum(f["size"] for f in files)
    print(f"pack v{args.version}: {len(files)} files, {total / 1e6:.1f} MB total")
    print(f"manifest: {manifest_path}")


if __name__ == "__main__":
    main()

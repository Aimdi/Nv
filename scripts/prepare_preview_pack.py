#!/usr/bin/env python3
"""Re-encode artist preview images to phone-sized WebP thumbnails.

Example:
  python scripts/prepare_preview_pack.py \
      --input /path/to/source_images \
      --output /tmp/previews-300px \
      --size 300 --quality 75

Then zip the output folder and host it as a GitHub Release asset for
in-app download-on-first-run.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def sanitize(name: str) -> str:
    return re.sub(r"[^a-z0-9._-]+", "_", name.lower()).strip("_")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--size", type=int, default=300, help="Long-edge pixels")
    parser.add_argument("--quality", type=int, default=75)
    parser.add_argument("--detail-size", type=int, default=768)
    args = parser.parse_args()

    try:
        from PIL import Image
    except ImportError:
        print("Install Pillow: pip install pillow", file=sys.stderr)
        return 1

    args.output.mkdir(parents=True, exist_ok=True)
    exts = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
    files = [p for p in args.input.rglob("*") if p.suffix.lower() in exts]
    if not files:
        print(f"No images under {args.input}", file=sys.stderr)
        return 1

    for src in files:
        stem = sanitize(src.stem)
        try:
            with Image.open(src) as im:
                im = im.convert("RGB")
                thumb = im.copy()
                thumb.thumbnail((args.size, args.size))
                thumb.save(args.output / f"{stem}.webp", "WEBP", quality=args.quality, method=6)
                detail = im.copy()
                detail.thumbnail((args.detail_size, args.detail_size))
                detail.save(
                    args.output / f"{stem}_detail.webp",
                    "WEBP",
                    quality=min(args.quality + 5, 90),
                    method=6,
                )
        except Exception as exc:  # noqa: BLE001
            print(f"skip {src}: {exc}", file=sys.stderr)

    print(f"Wrote WebP files to {args.output}")
    print("Zip that folder and publish as a Release asset for Settings → Download pack.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

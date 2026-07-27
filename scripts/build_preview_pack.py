#!/usr/bin/env python3
"""Re-encode artist preview images to phone-sized WebP thumbnails.

Usage:
  python scripts/build_preview_pack.py --input /path/to/images --output ./preview_pack_out

Expects input files named after artist tags (e.g. ebifurya.webp / ebifurya.png).
Writes:
  thumbs/<artist>.webp   (~300px long edge, q=75)
  detail/<artist>.webp   (~768px long edge, q=80)
  manifest.json
  preview_thumbs.zip
"""

from __future__ import annotations

import argparse
import json
import zipfile
from pathlib import Path

try:
    from PIL import Image
except ImportError as e:
    raise SystemExit("Pillow required: pip install Pillow") from e


def sanitize(name: str) -> str:
    import re
    s = name.lower()
    s = re.sub(r"[^a-z0-9._-]+", "_", s).strip("_")
    return s


def resize_long_edge(im: Image.Image, long_edge: int) -> Image.Image:
    w, h = im.size
    if max(w, h) <= long_edge:
        return im
    if w >= h:
        nh = int(h * long_edge / w)
        return im.resize((long_edge, max(nh, 1)), Image.Resampling.LANCZOS)
    nw = int(w * long_edge / h)
    return im.resize((max(nw, 1), long_edge), Image.Resampling.LANCZOS)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    ap.add_argument("--thumb-size", type=int, default=300)
    ap.add_argument("--detail-size", type=int, default=768)
    ap.add_argument("--limit", type=int, default=0, help="Optional max images for a test pack")
    args = ap.parse_args()

    thumbs = args.output / "thumbs"
    detail = args.output / "detail"
    thumbs.mkdir(parents=True, exist_ok=True)
    detail.mkdir(parents=True, exist_ok=True)

    exts = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
    files = sorted(p for p in args.input.rglob("*") if p.suffix.lower() in exts)
    if args.limit:
        files = files[: args.limit]

    manifest = []
    for i, src in enumerate(files, 1):
        stem = sanitize(src.stem)
        try:
            with Image.open(src) as im:
                im = im.convert("RGB")
                t = resize_long_edge(im, args.thumb_size)
                d = resize_long_edge(im, args.detail_size)
                t_path = thumbs / f"{stem}.webp"
                d_path = detail / f"{stem}.webp"
                t.save(t_path, "WEBP", quality=75, method=4)
                d.save(d_path, "WEBP", quality=80, method=4)
                manifest.append({
                    "name": src.stem,
                    "file": f"{stem}.webp",
                    "thumbBytes": t_path.stat().st_size,
                    "detailBytes": d_path.stat().st_size,
                })
        except Exception as e:
            print(f"skip {src}: {e}")
        if i % 100 == 0:
            print(f"… {i}/{len(files)}")

    (args.output / "manifest.json").write_text(json.dumps({
        "version": 1,
        "count": len(manifest),
        "thumbSize": args.thumb_size,
        "detailSize": args.detail_size,
        "items": manifest,
    }, indent=2))

    zip_path = args.output / "preview_thumbs.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_STORED) as zf:
        for p in thumbs.glob("*.webp"):
            zf.write(p, f"thumbs/{p.name}")
        for p in detail.glob("*.webp"):
            zf.write(p, f"detail/{p.name}")
        zf.write(args.output / "manifest.json", "manifest.json")

    total = sum(m["thumbBytes"] + m["detailBytes"] for m in manifest)
    print(f"Wrote {len(manifest)} images, ~{total/1e6:.1f} MB raw, zip={zip_path}")


if __name__ == "__main__":
    main()

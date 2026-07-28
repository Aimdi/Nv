#!/usr/bin/env python3
"""Re-encode a directory of preview images into a downloadable pack.

The raw upstream previews are far too large to ship to a phone: the NAI v3 set is roughly 15,000
JPEGs at ~250 kB each, about 3.7 GB. This script re-encodes them to WebP at phone-sized
resolutions, which is what brings a pack down to a few hundred megabytes.

Measured on a sample of the NAI v3 previews (832x1216 JPEGs averaging 253 kB), for the 17,228
artists in the bundled catalog:

    long edge  quality  avg/image  whole catalog
      256 px      70      10.3 kB     178 MB
      320 px      75      15.3 kB     263 MB
      384 px      75      19.9 kB     343 MB
      512 px      80      34.8 kB     600 MB
      768 px      80      60.3 kB    1039 MB

So thumbnails alone fit comfortably in a couple of hundred megabytes, but detail images do not
belong in the same pack. ``--full-px`` therefore defaults to 0; build detail images as a second
pack with its own id when you want them. The app falls back to the thumbnail in the detail sheet
whenever no detail image is installed.

Sizes written per artist:

  * ``thumbs/`` at ``--thumb-px`` on the long edge, for the browser grid.
  * ``full/``   at ``--full-px``  on the long edge, for the detail sheet (off by default).

Output files are named after the canonical Danbooru tag so that any pack can supply the image for
any catalog row, regardless of which dataset the row's metadata came from. The NAI dataset already
names its files that way; ThetaCursed's are named by numeric id, so pass its ``app/data.js`` as
``--name-map`` to translate them.

Usage:
    python3 tools/build_pack.py \
        --input work/images \
        --out dist/nai-v3-previews.zip \
        --id nai-v3-previews --name "NAI v3 artist previews" --source nai-v3 \
        --license Apache-2.0 --attribution "deus-ex-machina/novelai-anime-v3-artist-comparison"
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass

from PIL import Image

# Shared with the catalog builder on purpose: if the two ever normalised names differently, every
# pack filename would miss its catalog row and no image would ever appear.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_catalog import normalize_tag  # noqa: E402

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
PACK_METADATA_FILE = "pack.json"


def load_name_map(path: str) -> dict[str, str]:
    """Map ThetaCursed's numeric image ids to canonical tag names using ``app/data.js``."""
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    records = json.loads(text[text.index("[") : text.rindex("]") + 1])
    return {str(record["id"]): normalize_tag(record["name"]) for record in records}


@dataclass
class Job:
    source_path: str
    output_name: str
    thumb_px: int
    full_px: int
    thumb_quality: int
    full_quality: int
    thumb_dir: str
    full_dir: str


def encode_one(job: Job) -> tuple[str, int, int] | None:
    """Returns (output_name, thumb_bytes, full_bytes), or None when the image cannot be read."""
    try:
        with Image.open(job.source_path) as image:
            image = image.convert("RGB")
            thumb_bytes = _write_resized(
                image, job.thumb_px, job.thumb_quality, os.path.join(job.thumb_dir, job.output_name)
            )
            full_bytes = 0
            if job.full_px > 0:
                full_bytes = _write_resized(
                    image,
                    job.full_px,
                    job.full_quality,
                    os.path.join(job.full_dir, job.output_name),
                )
        return job.output_name, thumb_bytes, full_bytes
    except Exception as error:  # noqa: BLE001 - a single bad file must not stop the batch
        print(f"  skipped {job.source_path}: {error}", file=sys.stderr)
        return None


def _write_resized(image: Image.Image, long_edge: int, quality: int, out_path: str) -> int:
    resized = image.copy()
    # thumbnail() preserves aspect ratio and never upscales.
    resized.thumbnail((long_edge, long_edge), Image.Resampling.LANCZOS)
    resized.save(out_path, "WEBP", quality=quality, method=6)
    return os.path.getsize(out_path)


def collect_jobs(args, name_map: dict[str, str] | None, staging: str) -> list[Job]:
    thumb_dir = os.path.join(staging, "thumbs")
    full_dir = os.path.join(staging, "full")
    os.makedirs(thumb_dir, exist_ok=True)
    if args.full_px > 0:
        os.makedirs(full_dir, exist_ok=True)

    jobs: list[Job] = []
    seen: set[str] = set()
    for root, _, files in os.walk(args.input):
        for file_name in sorted(files):
            stem, extension = os.path.splitext(file_name)
            if extension.lower() not in IMAGE_SUFFIXES:
                continue
            if name_map is not None:
                tag = name_map.get(stem)
                if tag is None:
                    continue
            else:
                tag = normalize_tag(stem)
            output_name = f"{tag}.webp"
            if output_name in seen:
                continue
            seen.add(output_name)
            jobs.append(
                Job(
                    source_path=os.path.join(root, file_name),
                    output_name=output_name,
                    thumb_px=args.thumb_px,
                    full_px=args.full_px,
                    thumb_quality=args.thumb_quality,
                    full_quality=args.full_quality,
                    thumb_dir=thumb_dir,
                    full_dir=full_dir,
                )
            )
    return jobs


def report_catalog_coverage(catalog_path: str, produced: set[str]) -> None:
    """Warn early if pack filenames do not line up with the catalog's ``preview`` column."""
    import sqlite3

    connection = sqlite3.connect(catalog_path)
    previews = {row[0] for row in connection.execute("SELECT preview FROM artists") if row[0]}
    connection.close()

    matched = produced & previews
    print(
        f"Catalog coverage: {len(matched)}/{len(previews)} rows illustrated "
        f"({100 * len(matched) / max(len(previews), 1):.1f}%)"
    )
    orphans = produced - previews
    if orphans:
        print(
            f"  {len(orphans)} images have no catalog row, e.g. {sorted(orphans)[:3]}",
            file=sys.stderr,
        )


def sha256_of(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="Directory of source preview images")
    parser.add_argument("--out", required=True, help="Path of the zip to write")
    parser.add_argument("--id", required=True, help="Stable pack id")
    parser.add_argument("--name", required=True, help="Human-readable pack name")
    parser.add_argument("--source", required=True, help="Catalog source this pack illustrates")
    parser.add_argument("--description", default="")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--license", default="")
    parser.add_argument("--attribution", default="")
    parser.add_argument("--url", default="", help="Where the zip will be hosted")
    parser.add_argument("--name-map", help="app/data.js, to map numeric filenames to tag names")
    parser.add_argument("--thumb-px", type=int, default=320)
    parser.add_argument(
        "--full-px",
        type=int,
        default=0,
        help="Long edge for detail images; 0 (the default) writes thumbnails only, because "
        "detail images roughly quadruple the pack size",
    )
    parser.add_argument("--thumb-quality", type=int, default=75)
    parser.add_argument("--full-quality", type=int, default=80)
    parser.add_argument("--workers", type=int, default=os.cpu_count() or 4)
    parser.add_argument("--limit", type=int, default=0, help="Only encode N images (for testing)")
    parser.add_argument(
        "--catalog",
        help="catalog.db to cross-check against; reports how many rows this pack will illustrate",
    )
    parser.add_argument(
        "--manifest",
        help="Write or update a packs.json manifest with this pack's entry",
    )
    args = parser.parse_args()

    name_map = load_name_map(args.name_map) if args.name_map else None
    staging = os.path.splitext(args.out)[0] + ".staging"
    if os.path.exists(staging):
        import shutil

        shutil.rmtree(staging)
    os.makedirs(staging, exist_ok=True)

    jobs = collect_jobs(args, name_map, staging)
    if args.limit > 0:
        jobs = jobs[: args.limit]
    if not jobs:
        print("No source images found", file=sys.stderr)
        return 1
    print(f"Encoding {len(jobs)} images with {args.workers} workers...")

    results = []
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        for index, result in enumerate(pool.map(encode_one, jobs, chunksize=16), start=1):
            if result is not None:
                results.append(result)
            if index % 500 == 0:
                print(f"  {index}/{len(jobs)}")

    thumb_bytes = sum(r[1] for r in results)
    full_bytes = sum(r[2] for r in results)
    print(
        f"Encoded {len(results)} images: "
        f"thumbs {thumb_bytes / 1_000_000:.1f} MB, full {full_bytes / 1_000_000:.1f} MB"
    )
    if results:
        print(f"Average thumbnail: {thumb_bytes / len(results) / 1000:.1f} kB")

    if args.catalog:
        report_catalog_coverage(args.catalog, {r[0] for r in results})

    descriptor = {
        "id": args.id,
        "name": args.name,
        "description": args.description,
        "source": args.source,
        "version": args.version,
        "url": args.url,
        "imageCount": len(results),
        "thumbnailPx": args.thumb_px,
        "license": args.license,
        "attribution": args.attribution,
    }
    with open(os.path.join(staging, PACK_METADATA_FILE), "w", encoding="utf-8") as handle:
        json.dump(descriptor, handle, indent=2)

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    # The images are already compressed, so storing them avoids a pointless second pass.
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_STORED) as archive:
        for root, _, files in os.walk(staging):
            for file_name in sorted(files):
                absolute = os.path.join(root, file_name)
                archive.write(absolute, os.path.relpath(absolute, staging))

    size = os.path.getsize(args.out)
    digest = sha256_of(args.out)
    descriptor["sizeBytes"] = size
    descriptor["sha256"] = digest
    print(f"Wrote {args.out} ({size / 1_000_000:.1f} MB)")
    print(f"  sha256 {digest}")

    if args.manifest:
        manifest = {"schemaVersion": 1, "packs": []}
        if os.path.exists(args.manifest):
            with open(args.manifest, encoding="utf-8") as handle:
                manifest = json.load(handle)
        manifest["packs"] = [p for p in manifest.get("packs", []) if p.get("id") != args.id]
        manifest["packs"].append(descriptor)
        manifest["packs"].sort(key=lambda p: p["id"])
        with open(args.manifest, "w", encoding="utf-8") as handle:
            json.dump(manifest, handle, indent=2)
        print(f"Updated {args.manifest}")

    return 0


if __name__ == "__main__":
    sys.exit(main())

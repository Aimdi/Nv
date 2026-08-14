#!/usr/bin/env python3
"""Build compact *style-only* fingerprints for nax.moe V4.5 artist previews.

Matches lib/core/prompt/style_fingerprint.dart.

Ranking is z-distance on ink / texture / sat / contrast. Hue and mean RGB
are omitted: nax V4.5 constrained previews all show the same orange-hoodie
girl, and a sat/val histogram of that scene collapses to cosine ≈ 0.99.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from PIL import Image

SIZE = 96
EDGE_SCALE = 0.015
FINE_SCALE = 0.006
STRONG_SCALE = 0.07
SAT_SCALE = 0.09
SAT_VAR_SCALE = 0.04
CONTRAST_SCALE = 0.07
DIMS = 6
GALLERY = "danbooru-artist-tags-v4.5"
CDN = f"https://cdn.zele.st/data/NAX/Images/{GALLERY}"


def rgb_to_hsv(r: float, g: float, b: float) -> tuple[float, float, float]:
    mx = max(r, g, b)
    mn = min(r, g, b)
    delta = mx - mn
    h = 0.0
    if delta > 1e-9:
        if mx == r:
            h = 60.0 * (((g - b) / delta) % 6.0)
        elif mx == g:
            h = 60.0 * (((b - r) / delta) + 2.0)
        else:
            h = 60.0 * (((r - g) / delta) + 4.0)
    if h < 0:
        h += 360.0
    s = 0.0 if mx <= 1e-9 else delta / mx
    return h, s, mx


def l2_normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec))
    if norm < 1e-12:
        return vec
    return [x / norm for x in vec]


def fingerprint(path: Path) -> dict[str, object]:
    with Image.open(path) as im:
        rgb = im.convert("RGB").resize((SIZE, SIZE), Image.Resampling.BOX)
        pixels = list(rgb.getdata())
    w = h = SIZE
    gray = []
    sum_s = sum_s2 = sum_v = sum_v2 = sum_chroma = 0.0
    for r8, g8, b8 in pixels:
        r, g, b = r8 / 255.0, g8 / 255.0, b8 / 255.0
        _h, s, v = rgb_to_hsv(r, g, b)
        sum_s += s
        sum_s2 += s * s
        sum_v += v
        sum_v2 += v * v
        sum_chroma += s * v
        gray.append(0.299 * r + 0.587 * g + 0.114 * b)
    n = float(len(pixels))
    edge = 0.0
    strong = 0.0
    for y in range(h - 1):
        for x in range(w - 1):
            i = y * w + x
            dx = gray[i + 1] - gray[i]
            dy = gray[i + w] - gray[i]
            mag = math.sqrt(dx * dx + dy * dy)
            edge += mag
            if mag > 0.12:
                strong += 1
    edge_count = (w - 1) * (h - 1)
    fine = 0.0
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            i = y * w + x
            blur = (gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] + gray[i] * 4) / 8
            fine += abs(gray[i] - blur)
    fine_count = (w - 2) * (h - 2)
    sat_mean = sum_s / n
    sat_var = max(0.0, sum_s2 / n - sat_mean * sat_mean)
    mean_v = sum_v / n
    contrast = math.sqrt(max(0.0, sum_v2 / n - mean_v * mean_v))
    edge_mean = edge / edge_count
    strong_ratio = strong / edge_count
    fine_mean = fine / fine_count
    vec = l2_normalize(
        [
            edge_mean / EDGE_SCALE,
            fine_mean / FINE_SCALE,
            strong_ratio / STRONG_SCALE,
            sat_mean / SAT_SCALE,
            sat_var / SAT_VAR_SCALE,
            contrast / CONTRAST_SCALE,
        ]
    )
    return {
        "v": vec,
        "edge": edge_mean,
        "fine": fine_mean,
        "strong": strong_ratio,
        "sat": sat_mean,
        "satVar": sat_var,
        "contrast": contrast,
    }


def normalize_tag(tag: str) -> str:
    return (
        tag.strip()
        .replace("\\(", "(")
        .replace("\\)", ")")
        .replace(" ", "_")
        .lower()
    )


def load_filenames(nax_path: Path) -> dict[str, str]:
    payload = json.loads(nax_path.read_text(encoding="utf-8"))
    galleries = payload.get("galleries", payload)
    gallery = galleries.get(GALLERY) or {}
    tags = gallery["tags"] if isinstance(gallery, dict) else gallery
    out = {}
    for record in tags:
        name = normalize_tag(record["tag"])
        filename = record.get("filename") or f"{record['tag']}.jpg"
        out[name] = filename
    return out


def fetch(url: str, dest: Path) -> bool:
    if dest.exists() and dest.stat().st_size > 1000:
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "NvFingerprintBuilder/1.0.9"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            dest.write_bytes(resp.read())
        return dest.stat().st_size > 1000
    except Exception:
        return False


def candidate_urls(filename: str, tag: str) -> list[str]:
    urls = []
    urls.append(f"{CDN}/{filename}")
    urls.append(f"{CDN}/{urllib.parse.quote(filename, safe='')}")
    raw = tag.replace("_", " ") + ".jpg"
    urls.append(f"{CDN}/{urllib.parse.quote(raw, safe='')}")
    urls.append(f"{CDN}/{urllib.parse.quote(urllib.parse.quote(raw, safe=''), safe='')}")
    seen = set()
    unique = []
    for url in urls:
        if url not in seen:
            seen.add(url)
            unique.append(url)
    return unique


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--nax", default="tools/work/nax_tags.json")
    parser.add_argument("--strength", default="assets/artist_mix/nax_v45_strength.json")
    parser.add_argument("--cache", default="tools/work/nax_previews")
    parser.add_argument("--out", default="assets/artist_mix/style_fingerprints_v45.json")
    parser.add_argument("--limit", type=int, default=450)
    parser.add_argument("--min-score", type=int, default=5)
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args()

    strength = json.loads(Path(args.strength).read_text(encoding="utf-8"))["artists"]
    filenames = load_filenames(Path(args.nax)) if Path(args.nax).exists() else {}

    ranked = []
    for tag, rec in strength.items():
        votes = rec.get("v") or 0
        score = rec.get("s") or 0
        if votes < 3 or score < args.min_score:
            continue
        rank = score * votes / (votes + 10.0)
        ranked.append((rank, tag, rec))
    ranked.sort(reverse=True)
    ranked = ranked[: args.limit]
    print(f"selected {len(ranked)} artists", file=sys.stderr)

    cache = Path(args.cache)
    jobs = []
    for _, tag, rec in ranked:
        filename = filenames.get(tag, f"{tag.replace('_', ' ')}.jpg")
        dest = cache / f"{tag}.jpg"
        jobs.append((tag, rec, filename, dest))

    def download_one(job):
        tag, rec, filename, dest = job
        if dest.exists() and dest.stat().st_size > 1000:
            return tag, dest, True
        for url in candidate_urls(filename, tag):
            if fetch(url, dest):
                return tag, dest, True
        return tag, dest, False

    ok = 0
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futs = [pool.submit(download_one, job) for job in jobs]
        for i, fut in enumerate(as_completed(futs), 1):
            tag, dest, success = fut.result()
            if success:
                ok += 1
            if i % 50 == 0:
                print(f"downloaded {i}/{len(jobs)} ({ok} ok)", file=sys.stderr)

    artists = []
    for rank, tag, rec in ranked:
        dest = cache / f"{tag}.jpg"
        if not dest.exists() or dest.stat().st_size < 1000:
            continue
        try:
            fp = fingerprint(dest)
        except Exception as exc:
            print(f"skip {tag}: {exc}", file=sys.stderr)
            continue
        artists.append(
            {
                "tag": tag,
                "s": rec.get("s"),
                "votes": rec.get("v"),
                "edge": round(float(fp["edge"]), 6),
                "fine": round(float(fp["fine"]), 6),
                "strong": round(float(fp["strong"]), 6),
                "sat": round(float(fp["sat"]), 6),
                "satVar": round(float(fp["satVar"]), 6),
                "contrast": round(float(fp["contrast"]), 6),
                "v": [round(x, 6) for x in fp["v"]],
            }
        )

    out = {
        "version": 3,
        "source": "nax.moe V4.5 constrained artist previews (ink/sat/contrast)",
        "gallery": GALLERY,
        "dims": DIMS,
        "artists": artists,
    }
    dest = Path(args.out)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")
    print(f"wrote {len(artists)} fingerprints → {dest} ({dest.stat().st_size} bytes)")
    return 0 if artists else 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Build compact visual fingerprints for nax.moe V4.5 artist previews.

Matches lib/core/prompt/style_fingerprint.dart so query-time Dart cosine
search uses the same space.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from PIL import Image

SIZE = 48
HUE_BINS = 12
SAT_BINS = 6
VAL_BINS = 6
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


def bin_index(t: float, bins: int) -> int:
    i = int(min(max(t, 0.0), 0.999999) * bins)
    return min(max(i, 0), bins - 1)


def l2_normalize(vec: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in vec))
    if norm < 1e-12:
        return vec
    return [x / norm for x in vec]


def fingerprint(path: Path) -> list[float]:
    with Image.open(path) as im:
        rgb = im.convert("RGB").resize((SIZE, SIZE), Image.Resampling.BOX)
        pixels = list(rgb.getdata())
    hue = [0.0] * HUE_BINS
    sat = [0.0] * SAT_BINS
    val = [0.0] * VAL_BINS
    sum_r = sum_g = sum_b = sum_s = sum_v = sum_v2 = 0.0
    gray = []
    for r8, g8, b8 in pixels:
        r, g, b = r8 / 255.0, g8 / 255.0, b8 / 255.0
        h, s, v = rgb_to_hsv(r, g, b)
        hue[bin_index(h / 360.0, HUE_BINS)] += 1
        sat[bin_index(s, SAT_BINS)] += 1
        val[bin_index(v, VAL_BINS)] += 1
        sum_r += r
        sum_g += g
        sum_b += b
        sum_s += s
        sum_v += v
        sum_v2 += v * v
        gray.append(0.299 * r + 0.587 * g + 0.114 * b)
    n = float(len(pixels))
    edge = 0.0
    w = h = SIZE
    for y in range(h - 1):
        for x in range(w - 1):
            i = y * w + x
            dx = gray[i + 1] - gray[i]
            dy = gray[i + w] - gray[i]
            edge += math.sqrt(dx * dx + dy * dy)
    edge_count = (w - 1) * (h - 1)
    mean_v = sum_v / n
    var_v = max(0.0, sum_v2 / n - mean_v * mean_v)
    raw = (
        [x / n for x in hue]
        + [x / n for x in sat]
        + [x / n for x in val]
        + [sum_r / n, sum_g / n, sum_b / n, sum_s / n, math.sqrt(var_v), edge / edge_count]
    )
    return l2_normalize(raw)


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
    req = urllib.request.Request(url, headers={"User-Agent": "NvFingerprintBuilder/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            dest.write_bytes(resp.read())
        return dest.stat().st_size > 1000
    except Exception:
        return False


def candidate_urls(filename: str, tag: str) -> list[str]:
    urls = []
    # tags.json filenames are often already percent-encoded.
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
            vec = fingerprint(dest)
        except Exception as exc:
            print(f"skip {tag}: {exc}", file=sys.stderr)
            continue
        artists.append(
            {
                "tag": tag,
                "s": rec.get("s"),
                "votes": rec.get("v"),
                "v": [round(x, 6) for x in vec],
            }
        )

    out = {
        "version": 1,
        "source": "nax.moe V4.5 constrained artist previews",
        "gallery": GALLERY,
        "dims": HUE_BINS + SAT_BINS + VAL_BINS + 6,
        "artists": artists,
    }
    dest = Path(args.out)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")
    print(f"wrote {len(artists)} fingerprints → {dest} ({dest.stat().st_size} bytes)")
    return 0 if artists else 1


if __name__ == "__main__":
    raise SystemExit(main())

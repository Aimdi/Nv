#!/usr/bin/env python3
"""Build the prebuilt tag-catalog database that ships in the APK's assets.

The database has to satisfy Room's schema validation on first open, so rather than hand-writing
DDL this script reads the schema JSON that Room's annotation processor exports during a build
(``app/schemas/...CatalogDatabase/<version>.json``) and replays the exact ``CREATE`` statements,
identity hash and ``user_version`` that Room will look for.

Sources:

  * ``artists.json`` from ``deus-ex-machina/novelai-anime-v3-artist-comparison`` (Apache-2.0).
  * Illustrious uniqueness scores (from ThetaCursed's explorer, or a previously exported seed).
  * ``tags.json`` from https://nax.moe/downloads/tags.zip — community up/down votes on NovelAI
    V4.5 artist galleries. This is the ranking signal that actually answers "does this artist
    tag pull style on V4.5?", which Danbooru post counts do not.

Usage:
    python3 tools/build_catalog.py \
        --nai work/artists.json \
        --uniqueness-seed work/uniqueness_seed.json \
        --nax work/nax_tags.json \
        --schema app/schemas/dev.naicompanion.app.data.catalog.CatalogDatabase/2.json \
        --out app/src/main/assets/catalog/catalog.db
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Iterable

SOURCE_NAI_V3 = "nai-v3"
SOURCE_ILLUSTRIOUS = "illustrious"
SOURCE_DELIMITER = "|"

# Danbooru bookkeeping tags that are not artists.
EXCLUDED_TAGS = {"banned_artist", "artist_request", "anonymous_artist", "banned_artist"}

# nax.moe galleries that measure Danbooru artist tags on NovelAI V4.5.
NAX_V45_ARTIST_GALLERIES = (
    "danbooru-artist-tags-v4.5",  # constrained prompt
    "danbooru-artist-tags-2-v4.5",  # loose prompt
)

ROOM_MASTER_TABLE_ID = 42


@dataclass
class Artist:
    name: str
    post_count: int = 0
    uniqueness: float | None = None
    sources: set[str] = field(default_factory=set)
    nax_up: int | None = None
    nax_down: int | None = None

    @property
    def display_name(self) -> str:
        return self.name.replace("_", " ")

    @property
    def primary_source(self) -> str:
        # NAI previews match what this app is for, so prefer that label when both apply.
        return SOURCE_NAI_V3 if SOURCE_NAI_V3 in self.sources else sorted(self.sources)[0]

    @property
    def sources_column(self) -> str:
        joined = SOURCE_DELIMITER.join(sorted(self.sources))
        return f"{SOURCE_DELIMITER}{joined}{SOURCE_DELIMITER}"

    @property
    def aliases(self) -> str | None:
        """Extra search terms so a qualified tag is also findable by its bare name."""
        alternatives = set()
        stripped = re.sub(r"\s*\([^)]*\)\s*$", "", self.display_name).strip()
        if stripped and stripped != self.display_name:
            alternatives.add(stripped)
        hyphenless = self.display_name.replace("-", " ")
        if hyphenless != self.display_name:
            alternatives.add(hyphenless)
        return " ".join(sorted(alternatives)) or None

    @property
    def nax_score(self) -> int | None:
        if self.nax_up is None or self.nax_down is None:
            return None
        return self.nax_up - self.nax_down

    @property
    def nax_votes(self) -> int | None:
        if self.nax_up is None or self.nax_down is None:
            return None
        return self.nax_up + self.nax_down


def normalize_tag(raw: str) -> str:
    """Fold a name from either dataset into the canonical Danbooru underscore form."""
    text = raw.strip()
    # ThetaCursed / nax.moe store names in prompt form with spaces and escaped parentheses.
    text = text.replace("\\(", "(").replace("\\)", ")").replace("\\", "")
    text = re.sub(r"\s+", "_", text)
    text = re.sub(r"_+", "_", text)
    return text.strip("_").lower()


def load_nai(path: str) -> list[Artist]:
    with open(path, encoding="utf-8") as handle:
        records = json.load(handle)
    artists = []
    for record in records:
        name = normalize_tag(record["name"])
        if not name or name in EXCLUDED_TAGS:
            continue
        artists.append(
            Artist(
                name=name,
                post_count=int(record.get("post_count") or 0),
                sources={SOURCE_NAI_V3},
            )
        )
    return artists


def load_illustrious(path: str) -> list[Artist]:
    """Parse ``data.js``, which is a JS file wrapping a plain JSON array."""
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    start = text.index("[")
    end = text.rindex("]") + 1
    records = json.loads(text[start:end])
    return _artists_from_uniqueness_records(records, mark_illustrious=True)


def load_uniqueness_seed(path: str) -> list[Artist]:
    """Previously exported uniqueness scores, used when the upstream explorer is unavailable."""
    with open(path, encoding="utf-8") as handle:
        records = json.load(handle)
    return _artists_from_uniqueness_records(records, mark_illustrious=True)


def _artists_from_uniqueness_records(records: list[dict], mark_illustrious: bool) -> list[Artist]:
    artists = []
    for record in records:
        name = normalize_tag(record["name"])
        if not name or name in EXCLUDED_TAGS:
            continue
        uniqueness = record.get("uniqueness_score", record.get("uniqueness"))
        sources = set()
        if mark_illustrious:
            sources.add(SOURCE_ILLUSTRIOUS)
        raw_sources = record.get("sources")
        if isinstance(raw_sources, str):
            sources |= {part for part in raw_sources.split(SOURCE_DELIMITER) if part}
        artists.append(
            Artist(
                name=name,
                post_count=int(record.get("post_count") or 0),
                uniqueness=float(uniqueness) if uniqueness is not None else None,
                sources=sources or {SOURCE_ILLUSTRIOUS},
            )
        )
    return artists


def load_nax_votes(path: str) -> dict[str, tuple[int, int]]:
    """Merge constrained + loose V4.5 galleries into per-artist (up, down) totals."""
    with open(path, encoding="utf-8") as handle:
        payload = json.load(handle)
    galleries = payload.get("galleries", payload)
    totals: dict[str, list[int]] = defaultdict(lambda: [0, 0])
    for slug in NAX_V45_ARTIST_GALLERIES:
        gallery = galleries.get(slug)
        if not gallery:
            print(f"warning: nax gallery {slug!r} missing", file=sys.stderr)
            continue
        tags = gallery["tags"] if isinstance(gallery, dict) else gallery
        for record in tags:
            name = normalize_tag(record["tag"])
            if not name or name in EXCLUDED_TAGS:
                continue
            votes = record.get("votes") or {}
            totals[name][0] += int(votes.get("up") or 0)
            totals[name][1] += int(votes.get("down") or 0)
    return {name: (up, down) for name, (up, down) in totals.items()}


def merge(groups: Iterable[list[Artist]]) -> list[Artist]:
    merged: dict[str, Artist] = {}
    for group in groups:
        for artist in group:
            existing = merged.get(artist.name)
            if existing is None:
                merged[artist.name] = Artist(
                    name=artist.name,
                    post_count=artist.post_count,
                    uniqueness=artist.uniqueness,
                    sources=set(artist.sources),
                    nax_up=artist.nax_up,
                    nax_down=artist.nax_down,
                )
                continue
            existing.post_count = max(existing.post_count, artist.post_count)
            if existing.uniqueness is None:
                existing.uniqueness = artist.uniqueness
            existing.sources |= artist.sources
    return list(merged.values())


def apply_nax_votes(artists: list[Artist], votes: dict[str, tuple[int, int]]) -> None:
    for artist in artists:
        pair = votes.get(artist.name)
        if pair is None:
            continue
        artist.nax_up, artist.nax_down = pair


def load_schema(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)["database"]


def create_database(out_path: str, schema: dict, artists: list[Artist], preview_ext: str) -> None:
    if os.path.exists(out_path):
        os.remove(out_path)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)

    connection = sqlite3.connect(out_path)
    cursor = connection.cursor()

    for entity in schema["entities"]:
        table = entity["tableName"]
        cursor.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            cursor.execute(index["createSql"].replace("${TABLE_NAME}", table))

    # Default browse order is strength, so write the rows already sorted that way.
    artists = sorted(
        artists,
        key=lambda a: (
            a.nax_votes is None or a.nax_votes == 0,
            # Bayesian shrinkage: score * votes / (votes + prior). Matches app strengthRankScore().
            -((a.nax_score or 0) * (a.nax_votes or 0) / ((a.nax_votes or 0) + 10.0)),
            -(a.nax_score or 0),
            -a.post_count,
            a.name,
        ),
    )

    rows = [
        (
            index + 1,
            artist.name,
            artist.display_name,
            "ARTIST",
            artist.post_count,
            artist.primary_source,
            artist.sources_column,
            f"{artist.name}{preview_ext}",
            artist.uniqueness,
            artist.aliases,
            artist.nax_score,
            artist.nax_up,
            artist.nax_down,
            artist.nax_votes,
        )
        for index, artist in enumerate(artists)
    ]
    cursor.executemany(
        "INSERT INTO artists"
        " (id, name, display_name, kind, post_count, source, sources, preview, uniqueness,"
        "  aliases, nax_score, nax_up, nax_down, nax_votes)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )

    # `content=` FTS tables are populated from the content table rather than by inserting rows.
    cursor.execute("INSERT INTO artists_fts(artists_fts) VALUES('rebuild')")

    # Room refuses to open a prebuilt database whose identity hash it does not recognise.
    cursor.execute(
        "CREATE TABLE IF NOT EXISTS room_master_table"
        " (id INTEGER PRIMARY KEY, identity_hash TEXT)"
    )
    cursor.execute(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (?, ?)",
        (ROOM_MASTER_TABLE_ID, schema["identityHash"]),
    )
    cursor.execute(f"PRAGMA user_version = {schema['version']}")

    connection.commit()
    cursor.execute("VACUUM")
    connection.commit()
    connection.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nai", help="artists.json from the NAI v3 artist comparison dataset")
    parser.add_argument("--illustrious", help="app/data.js from the Illustrious style explorer")
    parser.add_argument(
        "--uniqueness-seed",
        help="JSON array of {name, uniqueness_score} when the live explorer is unavailable",
    )
    parser.add_argument(
        "--nax",
        help="tags.json from nax.moe/downloads/tags.zip (V4.5 community votes)",
    )
    parser.add_argument(
        "--schema",
        default="app/schemas/dev.naicompanion.app.data.catalog.CatalogDatabase/2.json",
        help="Room-exported schema JSON for the catalog database",
    )
    parser.add_argument("--out", default="app/src/main/assets/catalog/catalog.db")
    parser.add_argument(
        "--min-post-count",
        type=int,
        default=0,
        help="Drop artists below this Danbooru post count",
    )
    parser.add_argument(
        "--preview-ext",
        default=".webp",
        help="Extension recorded for preview files; must match what build_pack.py writes",
    )
    args = parser.parse_args()

    if not args.nai and not args.illustrious and not args.uniqueness_seed:
        parser.error("at least one of --nai, --illustrious or --uniqueness-seed is required")

    groups = []
    if args.nai:
        nai = load_nai(args.nai)
        print(f"NAI v3: {len(nai)} artists")
        groups.append(nai)
    if args.illustrious:
        illustrious = load_illustrious(args.illustrious)
        print(f"Illustrious: {len(illustrious)} artists")
        groups.append(illustrious)
    if args.uniqueness_seed:
        seed = load_uniqueness_seed(args.uniqueness_seed)
        print(f"Uniqueness seed: {len(seed)} artists")
        groups.append(seed)

    artists = merge(groups)
    if args.min_post_count > 0:
        artists = [a for a in artists if a.post_count >= args.min_post_count]

    if args.nax:
        votes = load_nax_votes(args.nax)
        apply_nax_votes(artists, votes)
        rated = sum(1 for a in artists if a.nax_votes)
        strong = sum(1 for a in artists if (a.nax_score or 0) >= 15 and (a.nax_votes or 0) >= 3)
        weak = sum(1 for a in artists if (a.nax_score or 0) <= -3 and (a.nax_votes or 0) >= 3)
        print(f"nax.moe V4.5 votes: {len(votes)} tags, {rated} matched, {strong} strong, {weak} weak")

    print(f"Merged: {len(artists)} unique artists")

    schema = load_schema(args.schema)
    create_database(args.out, schema, artists, args.preview_ext)

    size = os.path.getsize(args.out)
    print(f"Wrote {args.out} ({size / 1_000_000:.1f} MB, identity {schema['identityHash']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

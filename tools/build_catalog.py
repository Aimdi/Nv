#!/usr/bin/env python3
"""Build the prebuilt tag-catalog database that ships in the APK's assets.

The database has to satisfy Room's schema validation on first open, so rather than hand-writing
DDL this script reads the schema JSON that Room's annotation processor exports during a build
(``app/schemas/...CatalogDatabase/<version>.json``) and replays the exact ``CREATE`` statements,
identity hash and ``user_version`` that Room will look for.

Sources (both permissively licensed, see README for attribution):

  * ``artists.json`` from ``deus-ex-machina/novelai-anime-v3-artist-comparison`` (Apache-2.0) --
    every Danbooru artist tag with more than 94 posts, sampled with NAI Diffusion V3.
  * ``app/data.js`` from ``ThetaCursed/Illustrious-NoobAI-Style-Explorer`` (MIT) -- artists that
    work with Illustrious/NoobAI, with a distinctiveness score.

Artists listed by both datasets are merged into a single row; the ``sources`` column records
every dataset that covers them.

Usage:
    python3 tools/build_catalog.py \
        --nai work/artists.json \
        --illustrious work/data.js \
        --schema app/schemas/dev.naicompanion.app.data.catalog.CatalogDatabase/1.json \
        --out app/src/main/assets/catalog/catalog.db
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
from dataclasses import dataclass, field
from typing import Iterable

SOURCE_NAI_V3 = "nai-v3"
SOURCE_ILLUSTRIOUS = "illustrious"
SOURCE_DELIMITER = "|"

# Danbooru bookkeeping tags that are not artists.
EXCLUDED_TAGS = {"banned_artist", "artist_request", "anonymous_artist"}

ROOM_MASTER_TABLE_ID = 42


@dataclass
class Artist:
    name: str
    post_count: int = 0
    uniqueness: float | None = None
    sources: set[str] = field(default_factory=set)

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


def normalize_tag(raw: str) -> str:
    """Fold a name from either dataset into the canonical Danbooru underscore form."""
    text = raw.strip()
    # ThetaCursed stores names in prompt form with escaped parentheses and spaces.
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

    artists = []
    for record in records:
        name = normalize_tag(record["name"])
        if not name or name in EXCLUDED_TAGS:
            continue
        uniqueness = record.get("uniqueness_score")
        artists.append(
            Artist(
                name=name,
                post_count=int(record.get("post_count") or 0),
                uniqueness=float(uniqueness) if uniqueness is not None else None,
                sources={SOURCE_ILLUSTRIOUS},
            )
        )
    return artists


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
                )
                continue
            # The two datasets were snapshotted at different times; the larger count is newer.
            existing.post_count = max(existing.post_count, artist.post_count)
            if existing.uniqueness is None:
                existing.uniqueness = artist.uniqueness
            existing.sources |= artist.sources

    ordered = sorted(merged.values(), key=lambda a: (-a.post_count, a.name))
    return ordered


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
        )
        for index, artist in enumerate(artists)
    ]
    cursor.executemany(
        "INSERT INTO artists"
        " (id, name, display_name, kind, post_count, source, sources, preview, uniqueness, aliases)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        "--schema",
        default="app/schemas/dev.naicompanion.app.data.catalog.CatalogDatabase/1.json",
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

    if not args.nai and not args.illustrious:
        parser.error("at least one of --nai or --illustrious is required")

    groups = []
    if args.nai:
        nai = load_nai(args.nai)
        print(f"NAI v3: {len(nai)} artists")
        groups.append(nai)
    if args.illustrious:
        illustrious = load_illustrious(args.illustrious)
        print(f"Illustrious: {len(illustrious)} artists")
        groups.append(illustrious)

    artists = merge(groups)
    if args.min_post_count > 0:
        artists = [a for a in artists if a.post_count >= args.min_post_count]
    print(f"Merged: {len(artists)} unique artists")

    schema = load_schema(args.schema)
    create_database(args.out, schema, artists, args.preview_ext)

    size = os.path.getsize(args.out)
    print(f"Wrote {args.out} ({size / 1_000_000:.1f} MB, identity {schema['identityHash']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

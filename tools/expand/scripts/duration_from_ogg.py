#!/usr/bin/env python3
"""Set every line's duration in dialogues.json from its voice clips: the longer of the EN and RU clip.

The server holds a line (subtitles, the mouth, one voice at a time, conversations) for this duration, and both
languages play under the same one, so it has to cover the longer clip. A line with no clip yet keeps its estimate.
Subtitle frames are stretched with the line. dub.py runs this after every run (also an interrupted one), and
gen_from_spec.py after writing the estimates; check_lines.py fails while a clip is longer than its line.

  python3 tools/expand/scripts/duration_from_ogg.py            # rewrite dialogues.json
  python3 tools/expand/scripts/duration_from_ogg.py --check    # only report, exit 1 when a clip is cut
"""
from __future__ import annotations

import argparse
import json
import math
import os
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
DIALOGUES = ASSETS / "dialogues.json"
SOUNDS = ASSETS / "sounds.json"
# a clip may run this much past its line before it counts as cut (rounding to ticks)
SLACK_S = 0.05


def ogg_seconds(path: Path) -> float | None:
    """Length of an Ogg Vorbis file: the last page's granule position over the identification header's rate."""
    try:
        data = path.read_bytes()
    except OSError:
        return None
    head = data.find(b"\x01vorbis")
    last = data.rfind(b"OggS")
    if head < 0 or last < 0 or len(data) < last + 14 or len(data) < head + 16:
        return None
    rate = struct.unpack_from("<I", data, head + 12)[0]
    granule = struct.unpack_from("<q", data, last + 6)[0]
    return granule / rate if rate > 0 and granule > 0 else None


def clip_path(sounds: dict, key: str) -> Path | None:
    entry = sounds.get(key)
    if not entry or not entry.get("sounds"):
        return None
    sound = entry["sounds"][0]
    name = sound["name"] if isinstance(sound, dict) else sound
    return ASSETS / "sounds" / (name.split(":", 1)[1] + ".ogg")


def clip_seconds(sounds: dict, group: str, index: int) -> float | None:
    lengths = []
    for key in (f"dialogue.{group}.{index}", f"dialogue_ru.{group}.{index}"):
        path = clip_path(sounds, key)
        seconds = ogg_seconds(path) if path is not None and path.is_file() else None
        if seconds is not None:
            lengths.append(seconds)
    return max(lengths) if lengths else None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="report clips longer than their line, change nothing")
    args = ap.parse_args()
    data = json.loads(DIALOGUES.read_text(encoding="utf-8"))
    sounds = json.loads(SOUNDS.read_text(encoding="utf-8"))
    cut, changed, measured = [], 0, 0
    for gid, group in data["groups"].items():
        for variant in group["variants"]:
            seconds = clip_seconds(sounds, gid, variant["index"])
            if seconds is None:
                continue
            measured += 1
            old = float(variant.get("duration", 0.0))
            if seconds > old + SLACK_S:
                cut.append((gid, variant["index"], round(old, 2), round(seconds, 2)))
            new = math.ceil(seconds * 20.0) / 20.0
            if abs(new - old) < 1e-6:
                continue
            if old > 0:
                for frame in variant.get("subtitles", []):
                    frame["time"] = round(frame["time"] * new / old, 2)
            variant["duration"] = new
            changed += 1
        if group["variants"]:
            group["maximumDuration"] = max(v.get("duration", 0.0) for v in group["variants"])
    if args.check:
        for gid, index, old, seconds in cut[:20]:
            print(f"cut: {gid}.{index} plays {seconds} s, held {old} s")
        print(f"durations: {measured} lines with clips, {len(cut)} clips longer than their line")
        return 1 if cut else 0
    # the EN and RU dubs run side by side and both call this when they end: never leave a half-written file
    tmp = DIALOGUES.with_name(f"{DIALOGUES.name}.{os.getpid()}.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, DIALOGUES)
    print(f"durations: {measured} lines with clips, {changed} updated ({len(cut)} were cut short)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

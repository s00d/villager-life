#!/usr/bin/env python3
"""Screenshots every spread of the guide book in the running dev client, for a read-through of each language.

  python3 tools/dev/guide_shots.py [out_dir] [only_id ...]

Opens each chapter and entry with /vnapguide <id> <spread>, every spread the entry takes (counted by check_guide.py
the way GuideScreen lays the pages out). Prints the PNG paths in book order.
"""
import json
import os
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mcp import tool  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "guide"))
from check_guide import spreads  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "neoforge/src/main/resources/assets/villager_life/guide"


def shot(command: str, out: Path, name: str) -> Path:
    tool("mc.client.command.run", {"command": command})
    time.sleep(1.2)
    taken = Path(json.loads(tool("mc.client.screenshot.take", {}))["absolutePath"])
    target = out / f"{name}.png"
    shutil.move(taken, target)
    return target


def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1") / "run/screenshots/guide"
    only = set(sys.argv[2:])
    out.mkdir(parents=True, exist_ok=True)
    # the language the client shows: its texts decide how many spreads an entry takes
    options = (ROOT / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1") / "run/options.txt").read_text(encoding="utf-8")
    language = "ru_ru" if "\nlang:ru" in options else "en_us"
    book = json.loads((GUIDE / f"{language}.json").read_text(encoding="utf-8"))
    number = 0
    if not only:
        number += 1
        tool("mc.client.command.run", {"command": "vnapguide all"})  # every chapter open for the shots (toggles: twice closes it again)
        print(shot("vnapguide", out, f"{number:03d}_landing"))
    for category in book["categories"]:
        if not only or category["id"] in only:
            number += 1
            print(shot(f"vnapguide {category['id']} 0", out, f"{number:03d}_{category['id']}"))
        for entry in category["entries"]:
            if only and entry["id"] not in only:
                continue
            for spread in range(spreads(entry)):
                number += 1
                print(shot(f"vnapguide {entry['id']} {spread}", out, f"{number:03d}_{entry['id']}_{spread}"))
    tool("mc.client.command.run", {"command": "vnapguide"})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

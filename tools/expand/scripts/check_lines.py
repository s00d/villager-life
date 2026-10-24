#!/usr/bin/env python3
"""Fast checks of the dialogue data, run by tools/dev/gts.sh before the game tests: no group nothing calls, no
repeated line in a group, EN and RU text for every line, every line in a dub.py manifest with
the text its clip says (else a PENDING_TTS marker), no clip nothing plays, and women answer most situations men do. Exit 1 on a failure.

  python3 tools/expand/scripts/check_lines.py
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools/voice"))
import dub  # noqa: E402
from tts_text import for_tts  # noqa: E402

ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"


def said(text: str) -> str:
    return re.sub(r"\W+", " ", for_tts(text)).strip().lower()


def main() -> int:
    subprocess.run([sys.executable, str(HERE / "audit_lines.py")], check=True, capture_output=True)
    groups = json.loads((ROOT / "tools/expand/out/audit.json").read_text(encoding="utf-8"))
    manifest = {item["voice"]: item for item in dub.load_items(dub.manifest_paths([]))}
    problems = []
    waiting = {"en": 0, "ru": 0}
    for g in groups:
        if g["dead"]:
            problems.append(f"nothing calls {g['id']} {g['title']!r}")
        for v in g["variants"]:
            if "dup_in_group" in v["flags"]:
                problems.append(f"repeated line in {g['id']}: {v['en']!r}")
            if "missing_text" in v["flags"]:
                problems.append(f"no EN or RU text: {g['id']}.{v['index']}")
            item = manifest.get(v["voice"])
            if item is None:
                problems.append(f"no dub.py manifest has {v['voice']} ({g['id']}.{v['index']})")
            for lang in ("en", "ru"):
                folder = ASSETS / "sounds/voice" / lang
                if not (folder / f"{v['voice']}.ogg").is_file():
                    waiting[lang] += 1
                elif item and not (folder / f"{v['voice']}.PENDING_TTS").is_file() \
                        and said(dub.raw_text(item, lang)) != said(v[lang]):
                    problems.append(f"{lang}/{v['voice']}.ogg says {dub.raw_text(item, lang)!r}, subtitle {v[lang]!r}")
    played = {v["voice"] for g in groups for v in g["variants"]}
    orphans = [p.name for lang in ("ru", "en") for p in (ASSETS / "sounds/voice" / lang).iterdir()
               if p.suffix in (".ogg", ".PENDING_TTS") and p.name.split(".")[0] not in played]
    if orphans:
        problems.append(f"{len(orphans)} voice files nothing plays, e.g. {orphans[:5]}")
    # the game stops a clip when its line's duration runs out: a longer clip loses its last words
    held = subprocess.run([sys.executable, str(HERE / "duration_from_ogg.py"), "--check"], capture_output=True, text=True)
    if held.returncode != 0:
        problems.append(f"clips longer than their line (python3 tools/expand/scripts/duration_from_ogg.py): {held.stdout.strip().splitlines()[-1]}")
    family = (ROOT / "neoforge/src/main/java/com/vnap/dialogue/Family.java").read_text(encoding="utf-8")
    women = {g["title"] for g in groups if g["speaker"] == "woman"}
    # a woman answers with her own group of the same title, or a hand-picked stand-in (Family.WOMAN_LINES); men's
    # family lines, the parrot lessons and meeting the special characters are not hers
    mens_own = re.compile(r"^(Man |Kid |Woman |Parrot |Meet |Young )|Teaches the Parrot|Sleeping$")
    men = [g["title"] for g in groups if g["speaker"] == "villager" and g["title"] and not mens_own.search(g["title"])]
    silent = [t for t in men if t not in women and f'"{t}"' not in family]
    share = 1 - len(silent) / max(1, len(men))
    if share < 0.9:
        problems.append(f"women answer only {share:.0%} of the men's situations; silent: {silent[:10]}")
    for p in problems[:40]:
        print("FAIL", p)
    print(f"check_lines: {len(problems)} problems; women answer {share:.0%} of the men's situations; "
          f"clips waiting for dub.py: en {waiting['en']}, ru {waiting['ru']}")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

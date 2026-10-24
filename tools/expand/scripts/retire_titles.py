#!/usr/bin/env python3
"""Remove retired dialogue groups by title: dialogues.json, sounds.json and subtitle keys.

Voice clips stay: several groups share placeholder clips, so orphans are cleaned by hand.

  python3 tools/expand/scripts/retire_titles.py "Cultists Gather" "Demon Spawned" ...
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"


def main(titles: list[str]) -> int:
    dialogues_path = ASSETS / "dialogues.json"
    sounds_path = ASSETS / "sounds.json"
    data = json.loads(dialogues_path.read_text(encoding="utf-8"))
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    langs = {name: json.loads((ASSETS / "lang" / name).read_text(encoding="utf-8")) for name in ("en_us.json", "ru_ru.json")}
    removed = 0
    for title in titles:
        gid = data["titles"].pop(title, None)
        if gid is None:
            print(f"skip (absent): {title}")
            continue
        data["groups"].pop(gid, None)
        for key in [k for k in sounds if k.startswith((f"dialogue.{gid}.", f"dialogue_ru.{gid}."))]:
            del sounds[key]
        prefix = f"subtitles.villager_life.dialogue.{gid}."
        for table in langs.values():
            for key in [k for k in table if k.startswith(prefix)]:
                del table[key]
        removed += 1
        print(f"retired {title} ({gid})")
    dialogues_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for name, table in langs.items():
        (ASSETS / "lang" / name).write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"retired={removed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

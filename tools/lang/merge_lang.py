#!/usr/bin/env python3
"""Merge tools/lang/*.lang.json ({"en": {...}, "ru": {...}}) into the mod's en_us/ru_ru files."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
LANG = ROOT / "neoforge/src/main/resources/assets/villager_life/lang"


def main() -> None:
    extra = {"en": {}, "ru": {}}
    for part in sorted(pathlib.Path(__file__).parent.glob("*.lang.json")):
        data = json.loads(part.read_text(encoding="utf-8"))
        for side in ("en", "ru"):
            extra[side].update(data.get(side, {}))
    for side, name in (("en", "en_us.json"), ("ru", "ru_ru.json")):
        path = LANG / name
        table = json.loads(path.read_text(encoding="utf-8"))
        table.update(extra[side])
        path.write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {len(extra[side])} keys merged, {len(table)} total")
    missing = set(extra["en"]) ^ set(extra["ru"])
    if missing:
        raise SystemExit(f"keys present in only one language: {sorted(missing)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build bilingual TTS manifest for ALL dialogue clips (base + expand).

Reads dialogues.json + sounds.json + lang files → tools/voice/manifests/full_voices.json
Use with:
  tools/voice/dub.py --lang en --manifest tools/voice/manifests/full_voices.json
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
OUT = Path(__file__).resolve().parents[3] / "tools/voice/manifests/full_voices.json"


def main() -> int:
    dialogues = json.loads((ASSETS / "dialogues.json").read_text(encoding="utf-8"))
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    ru = json.loads((ASSETS / "lang/ru_ru.json").read_text(encoding="utf-8"))
    voice_en = ASSETS / "sounds/voice/en"
    voice_ru = ASSETS / "sounds/voice/ru"

    items = []
    for gid, group in dialogues["groups"].items():
        for variant in group.get("variants") or []:
            vi = int(variant["index"])
            sk = f"dialogue.{gid}.{vi}"
            if sk not in sounds:
                continue
            name = sounds[sk]["sounds"][0]["name"]
            tok = name.rsplit("/", 1)[-1]
            frames_en = []
            frames_ru = []
            keys = []
            for sub in variant.get("subtitles") or []:
                k = sub["key"]
                keys.append(k)
                frames_en.append(en.get(k, ""))
                frames_ru.append(ru.get(k, ""))
            en_tts = " ".join(x for x in frames_en if x).strip()
            ru_tts = " ".join(x for x in frames_ru if x).strip()
            if not en_tts and not ru_tts:
                continue

            def status(folder: Path) -> str:
                ogg = folder / f"{tok}.ogg"
                pending = folder / f"{tok}.PENDING_TTS"
                if ogg.exists() and not pending.exists():
                    return "READY"
                return "PENDING_TTS"

            items.append(
                {
                    "id": f"{gid}.{vi}",
                    "group": gid,
                    "group_id": gid,
                    "variant": vi,
                    "voice": tok,
                    "ogg": f"{tok}.ogg",
                    "duration": variant.get("duration", 2.0),
                    "title": group.get("title", ""),
                    "en": en_tts,
                    "ru": ru_tts,
                    "en_tts": en_tts,
                    "ru_tts": ru_tts,
                    "subtitle_keys": keys,
                    "status_en": status(voice_en),
                    "status_ru": status(voice_ru),
                    "status": status(voice_en),
                }
            )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(
            {
                "count": len(items),
                "note": "Full bilingual voice list (base+expand). EN TTS from scratch → voice/en; RU → voice/ru.",
                "items": items,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    pending_en = sum(1 for i in items if i["status_en"] == "PENDING_TTS")
    pending_ru = sum(1 for i in items if i["status_ru"] == "PENDING_TTS")
    print(f"wrote {OUT} items={len(items)} pending_en={pending_en} pending_ru={pending_ru}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

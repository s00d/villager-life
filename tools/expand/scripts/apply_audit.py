#!/usr/bin/env python3
"""Apply review decisions (a JSON file, applied once: variant keys go stale after renumbering) to the mod.

Per variant (keyed by its voice id, or "<group>.<index>"): "drop" removes it; "retext" only fixes the subtitle text
(the clip stays: a typo); "rewrite" gives it new EN/RU text under a NEW voice id
(the old clip goes, a PENDING_TTS marker waits for dub.py, so a running dub can never voice the new text with the
old id). Per group: "remove" deletes it (its spec entry is set to status: removed so gen_from_spec does not bring it
back). Variants are renumbered, sounds.json, subtitles (en_us/ru_ru), dialogues.json titles and the voice manifests
follow; the clips of dropped and re-voiced lines are deleted. Rewritten lines go to
tools/voice/manifests/audit.json, which dub.py reads with the others.

  python3 tools/expand/scripts/apply_audit.py --decisions file.json [--dry-run]
"""
from __future__ import annotations

import argparse
import json
import random
import re
import string
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
NS = "villager_life"
MANIFESTS = ROOT / "tools/voice/manifests"
CHUNKS = ROOT / "tools/expand/spec/chunks"
sys.path.insert(0, str(ROOT / "tools/voice"))


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def save(path: Path, data, indent=2) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=indent) + "\n", encoding="utf-8")


def sub_key(gid: str, index: int, frame: int) -> str:
    return f"subtitles.{NS}.dialogue.{gid}.{index}.{frame}"


def mirror_ru(sounds: dict) -> dict:
    """dialogue_ru.<group>.<index> plays the same clip id from voice/ru: rebuilt from the dialogue.* entries."""
    out = {k: v for k, v in sounds.items() if not k.startswith("dialogue_ru.")}
    for key, entry in list(out.items()):
        if key.startswith("dialogue."):
            out["dialogue_ru." + key[len("dialogue."):]] = json.loads(json.dumps(entry).replace(":voice/en/", ":voice/ru/"))
    return out


def sync_specs(dialogues: dict, en: dict, ru: dict) -> int:
    """Spec chunks keep the text the game now has: each entry's variants are rewritten from the lang files, so a
    later reader (or gen_from_spec) never sees a line the audit dropped or reworded. Returns the entries changed."""
    def q(text: str) -> str:
        return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'

    by_title = {(g["title"], g["speaker"]): gid for gid, g in dialogues["groups"].items()}
    changed = 0
    for chunk in CHUNKS.glob("*.yaml"):
        text = chunk.read_text(encoding="utf-8")
        parts = re.split(r"(?m)^(?=- key:)", text)
        out = [parts[0]]
        for block in parts[1:]:
            gid = (re.search(r"(?m)^  id: (\S+)$", block) or [None, None])[1]
            title = (re.search(r'(?m)^  title: "(.*)"$', block) or [None, ""])[1].replace('\\"', '"')
            speaker = (re.search(r"(?m)^  speaker: (\S+)$", block) or [None, "villager"])[1]
            gid = gid if gid in dialogues["groups"] else by_title.get((title, speaker))
            head, sep, _ = block.partition("  variants:\n")
            if not gid or not sep:
                out.append(block)
                continue
            lines = []
            for variant in dialogues["groups"][gid]["variants"]:
                keys = [sub["key"] for sub in variant.get("subtitles", [])]
                lines += [f"    - weight: {variant.get('weight', 50)}", "      frames:"]
                lines += [f"        - {q(ru.get(k, ''))}" for k in keys]
                lines += ["      frames_en:"] + [f"        - {q(en.get(k, ''))}" for k in keys]
            new = head + sep + "\n".join(lines) + "\n"
            if new.rstrip() != block.rstrip():
                changed += 1
            out.append(new.rstrip("\n") + block[len(block.rstrip("\n")):])
        result = "".join(out)
        if result != text:
            chunk.write_text(result, encoding="utf-8")
    return changed


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--decisions", type=Path, required=True)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    decisions = load(args.decisions)
    vdec: dict = decisions.get("variants", {})
    gdec: dict = decisions.get("groups", {})
    dialogues = load(ASSETS / "dialogues.json")
    sounds = load(ASSETS / "sounds.json")
    en = load(ASSETS / "lang/en_us.json")
    ru = load(ASSETS / "lang/ru_ru.json")

    taken = {name.rsplit("/", 1)[-1] for entry in sounds.values() for s in entry.get("sounds", [])
             for name in [s["name"] if isinstance(s, dict) else s]}
    for lang in ("ru", "en"):
        taken |= {p.name.split(".")[0] for p in (ASSETS / "sounds/voice" / lang).glob("*")}
    rng = random.Random(20261018)

    def new_voice() -> str:
        while True:
            voice = "q" + "".join(rng.choice(string.ascii_lowercase) for _ in range(5))
            if voice not in taken:
                taken.add(voice)
                return voice

    # one clip played by several variants (the rite placeholder `ii`): every variant but the first gets its own id
    # and is voiced anew with its own text
    users: dict[str, list[str]] = {}
    for key, entry in sounds.items():
        if key.startswith("dialogue."):
            for s in entry.get("sounds", []):
                users.setdefault((s["name"] if isinstance(s, dict) else s).rsplit("/", 1)[-1], []).append(key)
    shared = {voice: keys[1:] for voice, keys in users.items() if len(keys) > 1}

    def decision_for(gid: str, index: int, voice: str) -> dict:
        # reviewers keyed a variant by its voice, or by group and index when the voice was shared
        for key in (f"{gid}.{index}", f"{voice}@{gid}#{index}"):
            if key in vdec:
                return vdec[key]
        if voice in vdec and voice not in shared:
            return vdec[voice]
        if f"dialogue.{gid}.{index}" in shared.get(voice, []):
            return {"op": "split"}
        return {"op": "keep"}

    gone_voices: set[str] = set()
    redub: dict[str, str] = {}
    added_items: list[dict] = []
    removed_titles: set[str] = set()
    counts = {"dropped": 0, "rewritten": 0, "groups_removed": 0}
    for gid in list(dialogues["groups"]):
        group = dialogues["groups"][gid]
        old = []
        for variant in group["variants"]:
            entry = sounds.get(f"dialogue.{gid}.{variant['index']}", {}).get("sounds", [])
            name = (entry[0]["name"] if isinstance(entry[0], dict) else entry[0]) if entry else f"{NS}:voice/en/none"
            old.append((variant, name))
        if all(decision_for(gid, v["index"], name.rsplit("/", 1)[-1])["op"] == "keep" for v, name in old) and gid not in gdec:
            continue
        kept = []
        for variant, name in old:
            voice = name.rsplit("/", 1)[-1]
            decision = decision_for(gid, variant["index"], voice)
            if gdec.get(gid, {}).get("op") == "remove" or decision["op"] == "drop":
                if voice not in shared:
                    gone_voices.add(voice)
                counts["dropped"] += 1
                continue
            frames = [(sub["time"], en.get(sub["key"], ""), ru.get(sub["key"], "")) for sub in variant.get("subtitles", [])]
            if decision["op"] == "split":
                t_en = " ".join(f[1] for f in frames).strip()
                t_ru = " ".join(f[2] for f in frames).strip()
                decision = {"op": "rewrite", "en": t_en, "ru": t_ru}
                counts["split"] = counts.get("split", 0) + 1
                counts["rewritten"] -= 1
            elif decision["op"] == "rewrite" and voice not in shared \
                    and " ".join(f[1] for f in frames).strip() == decision["en"].strip():
                # only the Russian changes: the English clip stays, the Russian one is made again under the same id
                decision = {**decision, "op": "retext"}
                redub[voice] = decision["ru"]
            elif decision["op"] == "rewrite" and voice not in shared:
                gone_voices.add(voice)
            if decision["op"] == "retext":
                frames = [(0, decision["en"], decision["ru"])]
                counts["retexted"] = counts.get("retexted", 0) + 1
            if decision["op"] == "rewrite":
                voice = new_voice()
                name = name.rsplit("/", 1)[0] + "/" + voice
                frames = [(0, decision["en"], decision["ru"])]
                variant = {**variant, "duration": round(0.6 + 0.075 * len(decision["en"]), 2)}
                counts["rewritten"] += 1
                added_items.append({"group": gid, "group_id": gid, "voice": voice, "ogg": f"{voice}.ogg",
                                    "duration": variant["duration"], "title": group["title"], "en": decision["en"],
                                    "ru": decision["ru"], "en_tts": decision["en"], "ru_tts": decision["ru"]})
            kept.append((variant, name, frames))
        # every key of the old numbering goes; the kept variants come back renumbered
        for variant, _ in old:
            sounds.pop(f"dialogue.{gid}.{variant['index']}", None)
            for frame in range(len(variant.get("subtitles", []))):
                en.pop(sub_key(gid, variant["index"], frame), None)
                ru.pop(sub_key(gid, variant["index"], frame), None)
        if not kept:
            removed_titles.add(group["title"])
            del dialogues["groups"][gid]
            counts["groups_removed"] += 1
            continue
        variants = []
        for index, (variant, name, frames) in enumerate(kept):
            subs = []
            for frame, (time, t_en, t_ru) in enumerate(frames):
                key = sub_key(gid, index, frame)
                en[key], ru[key] = t_en, t_ru
                subs.append({"time": time, "key": key})
            variants.append({**variant, "index": index, "subtitles": subs})
            sounds[f"dialogue.{gid}.{index}"] = {"sounds": [{"name": name, "stream": True}]}
            for item in added_items:
                if item["voice"] == name.rsplit("/", 1)[-1]:
                    item["id"], item["variant"] = f"{gid}.{index}", index
        group["variants"] = variants
        group["maximumDuration"] = max(v.get("duration", 0) for v in variants)
    dialogues["titles"] = {t: g for t, g in dialogues["titles"].items() if g in dialogues["groups"]}
    sounds = mirror_ru(sounds)

    print(f"{counts}; {len(gone_voices)} clips go, {len(added_items)} new lines wait for TTS, {len(redub)} Russian clips redone")
    if args.dry_run:
        return 0
    save(ASSETS / "dialogues.json", dialogues)
    save(ASSETS / "sounds.json", sounds)
    save(ASSETS / "lang/en_us.json", en)
    save(ASSETS / "lang/ru_ru.json", ru)
    for path in MANIFESTS.glob("*.json"):
        data = load(path)
        items = [it for it in data["items"] if it.get("voice") not in gone_voices]
        changed = len(items) != len(data["items"])
        for it in items:
            if it.get("voice") in redub:
                it["ru"] = it["ru_tts"] = redub[it["voice"]]
                changed = True
        if changed:
            data["items"] = items
            save(path, data)
    for voice, text in redub.items():
        (ASSETS / "sounds/voice/ru" / f"{voice}.ogg").unlink(missing_ok=True)
        (ASSETS / "sounds/voice/ru" / f"{voice}.PENDING_TTS").write_text(text + "\n", encoding="utf-8")
    audit = MANIFESTS / "audit.json"
    previous = load(audit)["items"] if audit.is_file() else []
    save(audit, {"items": [it for it in previous if it["voice"] not in gone_voices] + added_items})
    for item in added_items:
        for lang in ("ru", "en"):
            (ASSETS / "sounds/voice" / lang / f"{item['voice']}.PENDING_TTS").write_text(item[lang] + "\n", encoding="utf-8")
    for chunk in CHUNKS.glob("*.yaml"):
        text = chunk.read_text(encoding="utf-8")
        new = text
        for title in removed_titles:
            pattern = re.compile(r'(title: "' + re.escape(title) + r'"\n(?:  .*\n)*?  status: )active')
            new = pattern.sub(r"\1removed", new)
        if new != text:
            chunk.write_text(new, encoding="utf-8")
    print(f"spec entries brought in line with the game: {sync_specs(dialogues, en, ru)}")
    gone = 0
    for voice in gone_voices:
        for lang in ("ru", "en"):
            for suffix in (".ogg", ".PENDING_TTS"):
                path = ASSETS / "sounds/voice" / lang / f"{voice}{suffix}"
                if path.is_file():
                    path.unlink()
                    gone += 1
    print(f"deleted {gone} clips and markers")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

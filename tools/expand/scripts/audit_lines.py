#!/usr/bin/env python3
"""Report on every dialogue line: who calls the group, the EN/RU text of each variant, its clips, and flags (a
duplicate in the group or elsewhere, very short, EN/RU length mismatch). Writes
tools/expand/out/audit.json (input for the review and for apply_audit.py) and audit.md (to read).

  python3 tools/expand/scripts/audit_lines.py
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
JAVA = ROOT / "neoforge/src/main/java"
OUT = ROOT / "tools/expand/out"
sys.path.insert(0, str(ROOT / "tools/voice"))

# groups whose titles the code builds at run time, not as one literal
DYNAMIC = (re.compile(r"^(Parrot Says|Man Teaches the Parrot|Woman Teaches the Parrot|Kid Teaches the Parrot) \d+$"),)


def norm(text: str) -> str:
    return re.sub(r"[^a-zа-яё0-9 ]", "", text.lower()).strip()


def literals() -> dict[str, list[str]]:
    """Every string literal in the mod's Java code -> where it appears (file:line)."""
    out = subprocess.run(["rg", "-n", "-o", '"[^"]{2,120}"', str(JAVA)], capture_output=True, text=True).stdout
    found: dict[str, list[str]] = defaultdict(list)
    for line in out.splitlines():
        path, number, text = line.split(":", 2)
        found[text.strip('"')].append(f"{Path(path).name}:{number}")
    # text blocks: title tables ("A < B | C", "*button -> Place a Button") and id tables ("cow lvzfcv, pig jqdeef")
    for path in JAVA.rglob("*.java"):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            where = f"{path.name}:{number}"
            for part in re.split(r"->|[|<>\\\",]", line):
                part = part.strip()
                if part:
                    found[part].append(where)
            for word in re.findall(r"\b[a-z][a-z0-9_]{3,}\b", line):
                found[word].append(where)
    return found


def main() -> int:
    dialogues = json.loads((ASSETS / "dialogues.json").read_text(encoding="utf-8"))["groups"]
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    en = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    ru = json.loads((ASSETS / "lang/ru_ru.json").read_text(encoding="utf-8"))
    used = literals()

    def text(variant: dict, lang: dict) -> str:
        return " ".join(lang.get(sub["key"], "") for sub in variant.get("subtitles", [])).strip()

    groups = []
    seen_text: dict[str, list[str]] = defaultdict(list)
    for gid, group in dialogues.items():
        callers = used.get(group["title"], []) + used.get(gid, [])
        if not callers and any(p.match(group["title"]) for p in DYNAMIC):
            callers = ["built at run time (Pets.teach / parrotDay)"]
        variants = []
        for variant in group["variants"]:
            entry = sounds.get(f"dialogue.{gid}.{variant['index']}", {}).get("sounds", [])
            name = entry[0]["name"] if entry and isinstance(entry[0], dict) else (entry[0] if entry else "")
            voice = name.rsplit("/", 1)[-1]
            t_en, t_ru = text(variant, en), text(variant, ru)
            flags = []
            if 0 < len(norm(t_en)) <= 6:
                flags.append("short")
            if t_en and t_ru and (len(t_ru) > 3 * len(t_en) + 10 or len(t_en) > 3 * len(t_ru) + 10):
                flags.append("length_mismatch")
            if not t_en or not t_ru:
                flags.append("missing_text")
            variants.append({"index": variant["index"], "voice": voice, "en": t_en, "ru": t_ru,
                             "duration": variant.get("duration"),
                             "ogg_en": (ASSETS / "sounds/voice/en" / f"{voice}.ogg").is_file(),
                             "ogg_ru": (ASSETS / "sounds/voice/ru" / f"{voice}.ogg").is_file(), "flags": flags})
            seen_text[norm(t_en)].append(f"{gid}.{variant['index']}")
        norms = [norm(v["en"]) for v in variants]
        for v, n in zip(variants, norms):
            if n and norms.count(n) > 1:
                v["flags"].append("dup_in_group")
        groups.append({"id": gid, "title": group["title"], "speaker": group["speaker"], "callers": callers[:4],
                       "dead": not callers, "variants": variants})
    # a woman's or a child's group of the same title answers the men's group wherever that one is called
    called = {g["title"]: g["id"] for g in groups if g["speaker"] == "villager" and not g["dead"]}
    for g in groups:
        if g["dead"] and g["speaker"] in ("woman", "child") and g["title"] in called:
            g["callers"], g["dead"] = [f"answers {called[g['title']]} (same title)"], False
    for g in groups:
        for v in g["variants"]:
            others = [ref for ref in seen_text[norm(v["en"])] if not ref.startswith(g["id"] + ".")]
            if norm(v["en"]) and others:
                v["flags"].append("dup_elsewhere:" + ",".join(others[:3]))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "audit.json").write_text(json.dumps(groups, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    lines = ["# Dialogue audit", "", f"{len(groups)} groups, {sum(len(g['variants']) for g in groups)} variants, "
             f"{sum(g['dead'] for g in groups)} never called.", ""]
    for g in groups:
        lines.append(f"## {g['title'] or '(pair)'} — `{g['id']}` ({g['speaker']}){' **DEAD**' if g['dead'] else ''}")
        lines.append(f"called: {', '.join(g['callers']) or '-'}")
        for v in g["variants"]:
            audio = ("en" if v["ogg_en"] else "") + ("+ru" if v["ogg_ru"] else "")
            lines.append(f"- {v['index']} `{v['voice']}` [{audio or 'no audio'}] {' '.join(v['flags'])}  \n"
                         f"  EN: {v['en']}  \n  RU: {v['ru']}")
        lines.append("")
    (OUT / "audit.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    flagged = sum(1 for g in groups for v in g["variants"] if v["flags"])
    print(f"{len(groups)} groups, {flagged} flagged variants, {sum(g['dead'] for g in groups)} dead -> {OUT / 'audit.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

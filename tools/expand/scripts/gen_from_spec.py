#!/usr/bin/env python3
"""Apply tools/expand/spec/chunks/*.yaml → dialogues.json, lang, sounds.json, tools/voice/manifests/new.json.

EN-primary bilingual generator:
  - frames_en required (English TTS + en_us.json)
  - frames / frames_ru = Russian (ru_ru.json)
  - sounds: dialogue.* → voice/en/{tok}, dialogue_ru.* → voice/ru/{tok}
  - Does NOT build a jar and does NOT run Fish TTS.

Usage:
  python3 gen_from_spec.py                      # add missing titles only
  python3 gen_from_spec.py --purge              # remove previous expand id_map groups, then re-add all
  python3 gen_from_spec.py --rebuild-manifest   # refresh manifest/id_map from all chunk titles already in dialogues
"""
from __future__ import annotations

import argparse
import json
import random
import re
import string
import subprocess
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except ImportError:
    yaml = None

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
SPEC_DIR = Path(__file__).resolve().parents[1] / "spec"
CHUNKS_DIR = SPEC_DIR / "chunks"
OUT_DIR = Path(__file__).resolve().parents[1] / "out"
MANIFEST = ROOT / "tools/voice/manifests/new.json"
ID_MAP = OUT_DIR / "id_map.json"

MODID = "villager_life"
VOICE_EN_PREFIX = f"{MODID}:voice/en/"
VOICE_RU_PREFIX = f"{MODID}:voice/ru/"
CYR = re.compile(r"[\u0400-\u04FF]")


def load_yaml(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    if not text.strip() or text.lstrip().startswith("# DEPRECATED"):
        if yaml is not None:
            data = yaml.safe_load(text)
            return data or []
        return []
    if yaml is not None:
        data = yaml.safe_load(text)
        return data or []
    entries: list[dict] = []
    cur: dict | None = None
    variant: dict | None = None
    mode = None
    for raw in text.splitlines():
        line = raw.rstrip()
        if not line.strip() or line.strip().startswith("#"):
            continue
        if line.startswith("- key:"):
            if cur:
                entries.append(cur)
            cur = {
                "key": line.split(":", 1)[1].strip(),
                "variants": [],
                "match": {},
            }
            variant = None
            mode = None
            continue
        assert cur is not None
        s = line.strip()
        if s.startswith("title:"):
            cur["title"] = s.split(":", 1)[1].strip().strip('"')
        elif s.startswith("category:"):
            cur["category"] = s.split(":", 1)[1].strip()
        elif s.startswith("id:"):
            cur["id"] = s.split(":", 1)[1].strip()
        elif s.startswith("status:"):
            cur["status"] = s.split(":", 1)[1].strip()
        elif s.startswith("trigger:"):
            cur["trigger"] = s.split(":", 1)[1].strip().strip('"')
        elif s.startswith("speaker:"):
            cur["speaker"] = s.split(":", 1)[1].strip()
        elif s == "match:":
            mode = "match"
        elif s == "variants:":
            mode = None
        elif s.startswith("- weight:"):
            variant = {"weight": int(s.split(":", 1)[1].strip()), "frames": [], "frames_en": [], "frames_ru": []}
            cur["variants"].append(variant)
            mode = None
        elif s == "frames:":
            mode = "frames"
        elif s == "frames_ru:":
            mode = "frames_ru"
        elif s == "frames_en:":
            mode = "frames_en"
        elif s.startswith("- ") and mode in ("frames", "frames_en", "frames_ru"):
            val = s[2:].strip().strip('"')
            variant[mode].append(val)  # type: ignore[index]
        elif mode == "match" and ":" in s and not s.startswith("-"):
            k, v = s.split(":", 1)
            cur["match"][k.strip()] = v.strip().strip('"')
    if cur:
        entries.append(cur)
    return entries


def load_all_specs() -> list[dict]:
    entries: list[dict] = []
    if CHUNKS_DIR.is_dir():
        for path in sorted(CHUNKS_DIR.glob("*.yaml")):
            chunk = load_yaml(path)
            print(f"  chunk {path.name}: {len(chunk)}")
            entries.extend(chunk)
    by_key: dict[str, dict] = {}
    for e in entries:
        by_key[e["key"]] = e
    return list(by_key.values())


def variant_frames(var: dict) -> tuple[list[str], list[str]]:
    """Return (frames_en, frames_ru). frames_en required."""
    frames_en = list(var.get("frames_en") or [])
    frames_ru = list(var.get("frames_ru") or var.get("frames") or [])
    return frames_en, frames_ru


def validate_frames_en(frames_en: list[str], frames_ru: list[str], context: str, speaker: str = "villager") -> str | None:
    if not frames_en:
        return f"{context}: missing frames_en"
    joined = " ".join(frames_en).strip()
    # a newborn's cry or coo is a sound, not a sentence
    if len(joined) < (4 if speaker == "infant" else 8):
        return f"{context}: frames_en too short ({len(joined)} chars)"
    if CYR.search(joined):
        return f"{context}: frames_en contains Cyrillic"
    if frames_ru and joined == " ".join(frames_ru).strip():
        return f"{context}: frames_en equals frames_ru (need real EN)"
    return None


def purge_expand(data: dict, sounds: dict, ru: dict, en: dict, voice_en: Path, voice_ru: Path) -> int:
    if not ID_MAP.exists():
        return 0
    id_map = json.loads(ID_MAP.read_text(encoding="utf-8"))
    removed = 0
    for _key, meta in id_map.items():
        gid = meta.get("id")
        title = meta.get("title")
        if not gid:
            continue
        group = data["groups"].pop(gid, None)
        if group:
            removed += 1
        if title:
            data["titles"].pop(title, None)
        for prefix in (f"dialogue.{gid}.", f"dialogue_ru.{gid}."):
            for sk in list(sounds.keys()):
                if not sk.startswith(prefix):
                    continue
                for snd in sounds[sk].get("sounds", []):
                    name = snd["name"] if isinstance(snd, dict) else snd
                    if "/voice/" in name:
                        tok = name.rsplit("/", 1)[-1]
                        for vd in (voice_en, voice_ru):
                            pending = vd / f"{tok}.PENDING_TTS"
                            if pending.exists():
                                pending.unlink()
                del sounds[sk]
        sub_prefix = f"subtitles.{MODID}.dialogue.{gid}."
        for lang in (ru, en):
            for k in list(lang.keys()):
                if k.startswith(sub_prefix):
                    del lang[k]
    return removed


def next_tokens(existing: set[str], n: int) -> list[str]:
    alpha = string.ascii_lowercase

    def nxt(tok: str) -> str:
        chars = list(tok)
        i = len(chars) - 1
        while i >= 0:
            idx = alpha.index(chars[i]) + 1
            if idx < 26:
                chars[i] = alpha[idx]
                return "".join(chars)
            chars[i] = "a"
            i -= 1
        return "a" + ("a" * len(tok))

    three = sorted(t for t in existing if len(t) == 3)
    cur = three[-1] if three else "dij"
    out: list[str] = []
    while len(out) < n:
        cur = nxt(cur)
        if cur not in existing:
            out.append(cur)
            existing.add(cur)
    return out


def alloc_ids(existing: set[str], n: int, rng: random.Random) -> list[str]:
    out: list[str] = []
    while len(out) < n:
        cand = "".join(rng.choice(string.ascii_lowercase) for _ in range(6))
        if cand not in existing:
            existing.add(cand)
            out.append(cand)
    return out


def est_duration(frames: list[str]) -> float:
    total = 0.0
    for i, f in enumerate(frames):
        total += max(0.9, min(4.5, len(f) * 0.055))
        if i < len(frames) - 1:
            total += 0.25
    return round(total, 2)


def body_for(title: str, category: str, trigger: str) -> str:
    return (
        f"§eTrigger\n\n§7{trigger or category}\n\n"
        f"§eReaction\n\n§7CreateAERO expand: {title}."
    )


def collect_tokens(sounds: dict, voice_en: Path, voice_ru: Path) -> set[str]:
    tokens: set[str] = set()
    for folder in (voice_en, voice_ru):
        if folder.is_dir():
            for p in folder.iterdir():
                if p.suffix == ".ogg" or p.name.endswith(".PENDING_TTS"):
                    tokens.add(p.name.replace(".PENDING_TTS", "").removesuffix(".ogg") if False else p.stem)
                    # stem of foo.PENDING_TTS is foo.PENDING_TTS — fix:
    tokens = set()
    for folder in (voice_en, voice_ru):
        if not folder.is_dir():
            continue
        for p in folder.glob("*.ogg"):
            tokens.add(p.stem)
        for p in folder.glob("*.PENDING_TTS"):
            tokens.add(p.name[: -len(".PENDING_TTS")])
    for entry in sounds.values():
        for s in entry.get("sounds", []):
            name = s["name"] if isinstance(s, dict) else s
            if "/voice/" in name:
                tokens.add(name.rsplit("/", 1)[-1])
    return tokens


def write_pending(path: Path, lang: str, frames: list[str]) -> None:
    path.write_text(
        f"Generate with Fish S2 ({lang}); see tools/expand/README.md\n" + "\n".join(frames) + "\n",
        encoding="utf-8",
    )


def bind_sounds(sounds: dict, gid: str, vi: int, tok: str) -> None:
    sounds[f"dialogue.{gid}.{vi}"] = {"sounds": [{"name": f"{VOICE_EN_PREFIX}{tok}", "stream": True}]}
    sounds[f"dialogue_ru.{gid}.{vi}"] = {"sounds": [{"name": f"{VOICE_RU_PREFIX}{tok}", "stream": True}]}


def status_for(voice_dir: Path, tok: str) -> str:
    ogg = voice_dir / f"{tok}.ogg"
    pending = voice_dir / f"{tok}.PENDING_TTS"
    if ogg.exists() and not pending.exists():
        return "READY"
    return "PENDING_TTS"


def rebuild_manifest_from_dialogues(entries: list[dict], data: dict, sounds: dict, ru: dict, en: dict,
                                    voice_en: Path, voice_ru: Path) -> tuple[dict, list[dict]]:
    title_to_gid = dict(data.get("titles") or {})
    by_speaker: dict[tuple[str, str], str] = {}
    for gid, g in data["groups"].items():
        title_to_gid.setdefault(g.get("title", ""), gid)
        by_speaker.setdefault((g.get("title", ""), g.get("speaker", "villager")), gid)

    id_map: dict[str, dict] = {}
    items: list[dict] = []
    missing: list[str] = []

    for e in entries:
        key = e["key"]
        title = e["title"]
        gid = e.get("id") if e.get("id") in data["groups"] else (
            by_speaker.get((title, e.get("speaker", "villager"))) or title_to_gid.get(title))
        if not gid or gid not in data["groups"]:
            missing.append(title)
            continue
        group = data["groups"][gid]
        id_map[key] = {
            "id": gid,
            "title": title,
            "category": e.get("category", ""),
            "match": e.get("match") or {},
            "variants": len(group.get("variants") or []),
        }
        for variant in group.get("variants") or []:
            vi = int(variant["index"])
            sk = f"dialogue.{gid}.{vi}"
            tok = "pending"
            if sk in sounds and sounds[sk].get("sounds"):
                name = sounds[sk]["sounds"][0]["name"]
                tok = name.rsplit("/", 1)[-1]
            frames_en = []
            frames_ru = []
            subtitle_keys = []
            for sub in variant.get("subtitles") or []:
                k = sub["key"]
                subtitle_keys.append(k)
                frames_en.append(en.get(k, ""))
                frames_ru.append(ru.get(k, ""))
            en_tts = " ".join(x for x in frames_en if x).strip()
            ru_tts = " ".join(x for x in frames_ru if x).strip()
            items.append(
                {
                    "id": f"{gid}.{vi}",
                    "group": gid,
                    "group_id": gid,
                    "expand_key": key,
                    "variant": vi,
                    "voice": tok,
                    "ogg": f"{tok}.ogg",
                    "duration": variant.get("duration", 2.0),
                    "en": en_tts,
                    "ru": ru_tts,
                    "en_tts": en_tts,
                    "ru_tts": ru_tts,
                    "ru_frames": frames_ru,
                    "status_en": status_for(voice_en, tok),
                    "status_ru": status_for(voice_ru, tok),
                    "status": status_for(voice_en, tok),
                    "subtitle_keys": subtitle_keys,
                    "title": title,
                    "category": e.get("category"),
                    "spec_key": key,
                }
            )
    if missing:
        print(f"rebuild_manifest missing titles={len(missing)} sample={missing[:5]}")
    return id_map, items


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--purge", action="store_true", help="Remove previous expand groups from id_map, then re-add")
    ap.add_argument("--rebuild-manifest", action="store_true", help="Only refresh manifest/id_map from chunks↔dialogues")
    args = ap.parse_args()

    rng = random.Random(20261016)
    print("Loading specs…")
    entries = [e for e in load_all_specs() if e.get("status", "active") == "active"]

    # Fail fast on missing frames_en
    errors: list[str] = []
    for e in entries:
        for vi, var in enumerate(e.get("variants") or []):
            frames_en, frames_ru = variant_frames(var)
            err = validate_frames_en(frames_en, frames_ru, f"{e.get('key')}[{vi}]", e.get("speaker", "villager"))
            if err:
                errors.append(err)
    if errors:
        print(f"ERROR: {len(errors)} frames_en validation failures:", file=sys.stderr)
        for err in errors[:30]:
            print(f"  {err}", file=sys.stderr)
        if len(errors) > 30:
            print(f"  … and {len(errors) - 30} more", file=sys.stderr)
        return 1

    dialogues_path = ASSETS / "dialogues.json"
    sounds_path = ASSETS / "sounds.json"
    ru_path = ASSETS / "lang" / "ru_ru.json"
    en_path = ASSETS / "lang" / "en_us.json"
    voice_en = ASSETS / "sounds" / "voice" / "en"
    voice_ru = ASSETS / "sounds" / "voice" / "ru"
    voice_en.mkdir(parents=True, exist_ok=True)
    voice_ru.mkdir(parents=True, exist_ok=True)

    data = json.loads(dialogues_path.read_text(encoding="utf-8"))
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    ru = json.loads(ru_path.read_text(encoding="utf-8"))
    en = json.loads(en_path.read_text(encoding="utf-8"))

    if args.rebuild_manifest and not args.purge:
        id_map, manifest_items = rebuild_manifest_from_dialogues(
            entries, data, sounds, ru, en, voice_en, voice_ru
        )
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        ID_MAP.write_text(json.dumps(id_map, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        MANIFEST.write_text(
            json.dumps(
                {
                    "count": len(manifest_items),
                    "note": "Bilingual TTS: en_tts → voice/en, ru_tts → voice/ru; Fish --lang en|ru",
                    "items": manifest_items,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"rebuild_manifest id_map={len(id_map)} items={len(manifest_items)}")
        print(f"manifest={MANIFEST}")
        return 0

    if args.purge:
        n = purge_expand(data, sounds, ru, en, voice_en, voice_ru)
        print(f"purged_expand_groups={n}")

    existing_ids = set(data["groups"].keys())
    existing_titles = set(data["titles"].keys())
    existing_tokens = collect_tokens(sounds, voice_en, voice_ru)


    # a group exists when its id does, or a group of the same title and speaker (a woman's or a child's line may
    # share the title of the men's line it answers)
    existing_pairs = {(g["title"], g.get("speaker", "villager")) for g in data["groups"].values()}
    to_add = []
    skipped = []
    for e in entries:
        title = e["title"]
        if e.get("id") in existing_ids or (title, e.get("speaker", "villager")) in existing_pairs:
            skipped.append(title)
            continue
        to_add.append(e)

    random_ids = iter(alloc_ids(existing_ids, sum(1 for e in to_add if not e.get("id")), rng))
    ids = [e.get("id") or next(random_ids) for e in to_add]
    variant_count = sum(len(e.get("variants") or []) for e in to_add if not e.get("id"))
    tokens = next_tokens(existing_tokens, variant_count)

    id_map: dict[str, dict] = {}
    if ID_MAP.exists() and not args.purge:
        try:
            id_map = json.loads(ID_MAP.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            id_map = {}

    manifest_items: list[dict] = []
    token_i = 0
    new_voice_placeholders = 0

    for e, gid in zip(to_add, ids):
        title = e["title"]
        speaker = e.get("speaker", "villager")
        variants_out: list[dict] = []
        max_dur = 0.0
        for vi, var in enumerate(e.get("variants", [])):
            frames_en, frames_ru = variant_frames(var)
            if not frames_en:
                continue
            if not frames_ru:
                frames_ru = list(frames_en)  # RU missing → copy EN so key exists; still EN-primary TTS
            while len(frames_ru) < len(frames_en):
                frames_ru.append(frames_en[len(frames_ru)])
            while len(frames_en) < len(frames_ru):
                frames_en.append(frames_en[-1] if frames_en else "")
            # Duration from EN (default locale audio)
            dur = est_duration(frames_en)
            max_dur = max(max_dur, dur)
            if e.get("id"):
                tok = f"{gid}_{vi}"
            else:
                tok = tokens[token_i]
                token_i += 1
            bind_sounds(sounds, gid, vi, tok)
            write_pending(voice_en / f"{tok}.PENDING_TTS", "en", frames_en)
            write_pending(voice_ru / f"{tok}.PENDING_TTS", "ru", frames_ru)
            new_voice_placeholders += 2

            subs = []
            subtitle_keys = []
            t = 0.0
            for fi, (fe, fr) in enumerate(zip(frames_en, frames_ru)):
                key = f"subtitles.{MODID}.dialogue.{gid}.{vi}.{fi}"
                en[key] = fe
                ru[key] = fr
                subs.append({"time": round(t, 2), "key": key})
                subtitle_keys.append(key)
                t += max(0.9, min(4.5, len(fe) * 0.055)) + 0.25

            en_joined = " ".join(frames_en)
            ru_joined = " ".join(frames_ru)
            variants_out.append(
                {
                    "index": vi,
                    "duration": dur,
                    "weight": int(var.get("weight", 50)),
                    "subtitles": subs,
                }
            )
            manifest_items.append(
                {
                    "id": f"{gid}.{vi}",
                    "group": gid,
                    "group_id": gid,
                    "expand_key": e.get("key"),
                    "variant": vi,
                    "voice": tok,
                    "ogg": f"{tok}.ogg",
                    "duration": dur,
                    "en": en_joined,
                    "ru": ru_joined,
                    "en_tts": en_joined,
                    "ru_tts": ru_joined,
                    "ru_frames": frames_ru,
                    "status_en": "PENDING_TTS",
                    "status_ru": "PENDING_TTS",
                    "status": "PENDING_TTS",
                    "subtitle_keys": subtitle_keys,
                    "title": title,
                    "category": e.get("category"),
                    "spec_key": e.get("key"),
                }
            )

        group = {
            "title": title,
            "body": body_for(title, e.get("category", ""), e.get("trigger", "")),
            "speaker": speaker,
            "maximumDuration": max_dur,
            "variants": variants_out,
        }
        data["groups"][gid] = group
        data["titles"].setdefault(title, gid)
        id_map[e["key"]] = {
            "id": gid,
            "title": title,
            "category": e.get("category"),
            "match": e.get("match") or {},
            "variants": len(variants_out),
        }

    # Always refresh full manifest for all chunk titles after add
    full_id_map, full_items = rebuild_manifest_from_dialogues(
        entries, data, sounds, ru, en, voice_en, voice_ru
    )
    # Prefer newly written id_map entries
    full_id_map.update(id_map)

    dialogues_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    sounds_path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    ru_path.write_text(json.dumps(ru, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    en_path.write_text(json.dumps(en, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    ID_MAP.write_text(json.dumps(full_id_map, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MANIFEST.write_text(
        json.dumps(
            {
                "count": len(full_items),
                "note": "Bilingual TTS: en_tts → voice/en/{voice}.ogg, ru_tts → voice/ru/{voice}.ogg; Fish --lang en|ru",
                "items": full_items,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    # Final EN Cyrillic QA
    cyr_keys = [k for k, v in en.items() if k.startswith("subtitles.") and CYR.search(str(v))]
    if cyr_keys:
        print(f"WARNING: {len(cyr_keys)} Cyrillic keys in en_us.json", file=sys.stderr)

    print(
        f"added_groups={len(to_add)} skipped_existing_titles={len(skipped)} "
        f"voice_clips={token_i} pending_markers={new_voice_placeholders} "
        f"manifest_items={len(full_items)}"
    )
    # the estimates above stand only for lines without a clip yet
    subprocess.run([sys.executable, str(Path(__file__).with_name("duration_from_ogg.py"))], check=False)
    print(f"manifest={MANIFEST}")
    print(f"id_map={ID_MAP}")
    if skipped:
        print("skipped:", ", ".join(skipped[:10]), ("…" if len(skipped) > 10 else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

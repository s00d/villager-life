#!/usr/bin/env python3
"""Find clipped voice clips in sounds/voice/<lang>/ and write the regeneration config.

Each spoken clip gets the check.py verdict: a loud, unfading last 80 ms, a length far short of the text, or a
last word Whisper does not hear. Suspects get a waveform picture in out/diagrams/<lang>/. The report goes to
out/scan_<lang>.json, and manifests/regen.json collects the suspects of both languages (bad_ru / bad_en, the
reasons, and empty ru_tts_short / en_tts_short to fill in when a line keeps clipping). Scanning one language
keeps the other language's flags and every short text already written.

  .venv/bin/python tools/voice/scan.py --lang ru
  .venv/bin/python tools/voice/scan.py --lang en --whisper suspects
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

import check
from dub import HERE, VOICE, load_items, manifest_paths, raw_text
from tts_text import for_tts

OUT = HERE / "out"
REGEN = HERE / "manifests" / "regen.json"


def diagram(ogg: Path, png: Path) -> None:
    png.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(ogg), "-filter_complex",
                    "aformat=channel_layouts=mono,showwavespic=s=1200x240:colors=#3070d0", "-frames:v", "1", str(png)],
                   check=True)


def update_regen(lang: str, items: dict[str, dict], scanned: set[str], bad: dict[str, list[str]]) -> int:
    regen = {it["voice"]: it for it in json.loads(REGEN.read_text(encoding="utf-8"))["items"]} if REGEN.is_file() else {}
    for voice in scanned & regen.keys():
        regen[voice][f"bad_{lang}"] = False
        regen[voice].pop(f"reason_{lang}", None)
    for voice, reasons in bad.items():
        src = items[voice]
        entry = regen.setdefault(voice, {
            "voice": voice, "title": src.get("title") or src.get("group") or "",
            "ru": raw_text(src, "ru"), "en": raw_text(src, "en"), "ru_tts_short": "", "en_tts_short": "",
            "bad_ru": False, "bad_en": False,
        })
        entry[f"bad_{lang}"] = True
        entry[f"reason_{lang}"] = ",".join(reasons)
    kept = sorted((e for e in regen.values() if e.get("bad_ru") or e.get("bad_en")), key=lambda e: e["voice"])
    REGEN.write_text(json.dumps({"count": len(kept), "items": kept}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return len(kept)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", choices=("en", "ru"), required=True)
    ap.add_argument("--manifest", type=Path, action="append", default=[], help="repeatable; default as in dub.py")
    ap.add_argument("--only", action="append", default=[], help="voice id, repeatable")
    ap.add_argument("--whisper", choices=("all", "suspects", "off"), default="all",
                    help="Whisper every clip, only clips the envelope already flags, or none")
    ap.add_argument("--no-diagrams", action="store_true")
    args = ap.parse_args()
    lang = args.lang
    if not args.no_diagrams and not shutil.which("ffmpeg"):
        print("ERROR: ffmpeg not found (or pass --no-diagrams)", file=sys.stderr)
        return 2

    items = {it["voice"]: it for it in load_items(manifest_paths(args.manifest))}
    if args.only:
        items = {v: it for v, it in items.items() if v in set(args.only)}
    transcribe = None if args.whisper == "off" else check.whisper()
    report, bad = [], {}
    clips = [(v, it) for v, it in items.items() if (VOICE / lang / f"{v}.ogg").is_file()]
    for n, (voice, it) in enumerate(clips, 1):
        text = for_tts(raw_text(it, lang))
        if not text:
            continue
        ogg = VOICE / lang / f"{voice}.ogg"
        verdict = check.check(ogg, text, lang, transcribe if args.whisper == "all" else None)
        if args.whisper == "suspects" and not verdict.ok:
            verdict = check.check(ogg, text, lang, transcribe)
        row = {"voice": voice, "text": text, **{k: getattr(verdict, k) for k in
               ("duration", "tail_db", "fade_db", "expected_s", "transcript", "last_word", "word_score", "reasons")}}
        report.append(row)
        if not verdict.ok:
            bad[voice] = verdict.reasons
            if not args.no_diagrams:
                diagram(ogg, OUT / "diagrams" / lang / f"{voice}.png")
        if n % 100 == 0:
            print(f"{n}/{len(clips)} scanned, {len(bad)} suspects", flush=True)

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"scan_{lang}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total = update_regen(lang, items, {row["voice"] for row in report}, bad)
    by_reason: dict[str, int] = {}
    for reasons in bad.values():
        for r in reasons:
            by_reason[r] = by_reason.get(r, 0) + 1
    print(f"{lang}: {len(report)} clips, {len(bad)} suspects {by_reason}; report {OUT / f'scan_{lang}.json'}; "
          f"regen.json now {total} lines")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

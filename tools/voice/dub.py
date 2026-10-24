#!/usr/bin/env python3
"""Dub manifest lines with Fish S2 Pro (MLX) straight into the mod's sounds/voice/<lang>/<voice>.ogg.

Existing clips are kept unless --force. A line is said as written: its S2 emotion tags ([gasp], [snoring], ...)
are part of its text, and laughter, screams and snoring are lines like any other. A run makes every clip once
first, then checks them all (loud tail, length, Whisper last word), then remakes the bad ones with another seed, then
ending in "!" instead of "."/"..." (a short line ending in a full stop is cut mid-word by Fish), then with
<lang>_tts_short if the manifest has it. Clips made but not yet checked are listed in out/unchecked_<lang>.txt, so a
stopped run checks them next time. The log records generated tokens against the budget (`length` = cut by it).

  .venv/bin/python tools/voice/dub.py --lang ru                      # everything that needs a voice (see below)
  .venv/bin/python tools/voice/dub.py --lang en --manifest tools/voice/manifests/regen.json --force
  .venv/bin/python tools/voice/dub.py --lang en --speaker child       # only the children's lines

Each clip is voiced by its dialogue group's speaker: speaker/<lang>/<speaker>/ref.{wav,txt} when that folder
exists (child, woman, parrot, infant), otherwise the shared speaker/<lang>/ref.{wav,txt}. One run per language does
all of it: a clip is (re)made when it is missing, when its <voice>.PENDING_TTS marker is there, or when it is older
than its speaker's own ref.wav (a speaker that got a voice of its own: its old clips were made with the shared one).
A missing ref.txt next to a ref.wav is transcribed with Whisper first (check it once). After every run, also a
stopped one, the line durations in dialogues.json are set from the clips (duration_from_ogg.py).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

from tts_text import for_tts

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
MANIFESTS = HERE / "manifests"
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
VOICE = ASSETS / "sounds/voice"
LOGS = HERE / "logs"
SKIP_BY_DEFAULT = {"regen.json"}
DEFAULT_MODEL = Path.home() / "models" / "fishaudio-s2-pro-8bit-mlx"
TOKENS_PER_S = 21.5


def manifest_paths(given: list[Path]) -> list[Path]:
    if given:
        return given
    return sorted(p for p in MANIFESTS.glob("*.json") if p.name not in SKIP_BY_DEFAULT)


def load_items(paths: list[Path]) -> list[dict]:
    seen: dict[str, dict] = {}
    for path in paths:
        for item in json.loads(path.read_text(encoding="utf-8"))["items"]:
            seen.setdefault(item["voice"], item)
    return list(seen.values())


def voice_speakers() -> dict[str, str]:
    """Clip token -> speaker of the dialogue group that plays it (dialogues.json + sounds.json)."""
    groups = json.loads((ASSETS / "dialogues.json").read_text(encoding="utf-8"))["groups"]
    sounds = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    out: dict[str, str] = {}
    for key, entry in sounds.items():
        if not key.startswith("dialogue."):
            continue
        group = groups.get(key.split(".")[1])
        for sound in entry.get("sounds", []):
            name = sound["name"] if isinstance(sound, dict) else sound
            if group and "voice/" in name:
                out[name.rsplit("/", 1)[-1]] = group["speaker"]
    return out


def reference_dir(lang: str, speaker: str) -> Path:
    own = HERE / "speaker" / lang / speaker
    return own if (own / "ref.wav").is_file() else HERE / "speaker" / lang


def own_reference(lang: str, speaker: str) -> Path | None:
    """The speaker's own ref.wav, or None when it uses the shared voice."""
    wav = HERE / "speaker" / lang / speaker / "ref.wav"
    return wav if wav.is_file() else None


def ensure_transcripts(lang: str) -> None:
    """Every ref.wav of this language without a ref.txt gets one from Whisper (printed: check it once)."""
    missing = [wav for wav in (HERE / "speaker" / lang).rglob("ref.wav") if not wav.with_name("ref.txt").is_file()]
    if not missing:
        return
    import mlx_whisper

    from check import WHISPER_MODEL

    for wav in missing:
        text = mlx_whisper.transcribe(str(wav), path_or_hf_repo=WHISPER_MODEL, language=lang)["text"].strip()
        wav.with_name("ref.txt").write_text(text + "\n", encoding="utf-8")
        print(f"transcribed {wav.relative_to(HERE)}: {text}", flush=True)


def raw_text(item: dict, lang: str) -> str:
    return item.get(f"{lang}_tts") or item.get(lang) or ""


def budget(text: str, lang: str) -> int:
    """2.5x the audio the text needs plus 4 s of slack, at least 8 s: an EOS stop, not the budget, ends a clip."""
    from check import CHARS_PER_S

    seconds = len(text) / CHARS_PER_S[lang]
    return int(min(2048, max(8.0, seconds * 2.5 + 4.0) * TOKENS_PER_S))


def to_ogg(wav: Path, ogg: Path, quality: str) -> None:
    """Mono Ogg Vorbis through libsndfile's libvorbis: Minecraft positions and fades with distance only mono sounds,
    and ffmpeg's own Vorbis encoder writes stereo only. quality is Vorbis q 0..10."""
    import soundfile as sf

    samples, rate = sf.read(str(wav), always_2d=True)
    tmp = ogg.with_name(ogg.stem + ".partial.ogg")
    try:
        sf.write(str(tmp), samples.mean(axis=1), rate, format="OGG", subtype="VORBIS",
                 compression_level=max(0.0, min(1.0, 1.0 - float(quality) / 10.0)))
    except Exception:
        tmp.unlink(missing_ok=True)
        raise
    tmp.replace(ogg)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", choices=("en", "ru"))
    ap.add_argument("--manifest", type=Path, action="append", default=[],
                    help="repeatable; default: every manifests/*.json except regen.json")
    ap.add_argument("--only", action="append", default=[], help="voice id, repeatable")
    ap.add_argument("--speaker", action="append", default=[],
                    help="dialogue speaker (villager, child, woman, mayor, ...), repeatable")
    ap.add_argument("--limit", type=int)
    ap.add_argument("--force", action="store_true", help="overwrite existing clips")
    ap.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    ap.add_argument("--ref-audio", type=Path, help="one reference for every clip; default: by speaker, see above")
    ap.add_argument("--ref-text-file", type=Path, help="transcript of --ref-audio")
    ap.add_argument("--attempts", type=int, default=3, help="seeds to try before the short text")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--ogg-q", default="5", help="Vorbis quality 0..10")
    args = ap.parse_args()

    paths = manifest_paths(args.manifest)
    if args.lang is None:
        ap.error("--lang is required")
    lang = args.lang
    if bool(args.ref_audio) != bool(args.ref_text_file):
        ap.error("--ref-audio and --ref-text-file go together")
    # The clone copies the reference's accent along with its timbre: each language needs a native speaker sample.
    if not args.ref_audio:
        ensure_transcripts(lang)
    default_ref = reference_dir(lang, "villager")
    checks = [(args.model.is_dir(), f"model dir {args.model}"), (shutil.which("ffmpeg"), "ffmpeg")]
    if args.ref_audio:
        checks += [(args.ref_audio.is_file(), f"ref {args.ref_audio}"), (args.ref_text_file.is_file(), f"ref transcript {args.ref_text_file}")]
    else:
        checks += [((default_ref / "ref.wav").is_file(), f"ref {default_ref / 'ref.wav'}"),
                   ((default_ref / "ref.txt").is_file(), f"ref transcript {default_ref / 'ref.txt'}")]
    for need, what in checks:
        if not need:
            print(f"ERROR: missing {what}", file=sys.stderr)
            return 2

    import mlx.core as mx
    import numpy as np
    import soundfile as sf
    from mlx_speech.generation.fish_s2_pro import FishS2ProRuntime

    import check

    items = load_items(paths)
    speakers = voice_speakers()
    for it in items:
        it["speaker"] = speakers.get(it["voice"], "villager")
    if args.only:
        items = [it for it in items if it["voice"] in set(args.only)]
    if args.speaker:
        items = [it for it in items if it["speaker"] in set(args.speaker)]
    items = [it for it in items if it.get(f"bad_{lang}", True)]
    # a manifest may still list a line the game dropped: its clip would play nowhere
    items = [it for it in items if it["voice"] in speakers]
    if args.limit is not None:
        items = items[: args.limit]

    out_dir = VOICE / lang
    LOGS.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    run_log = LOGS / f"dub-{lang}-{stamp}.log"
    progress = LOGS / f"dub-{lang}-{stamp}.jsonl"

    def log(msg: str) -> None:
        line = f"[{datetime.now():%H:%M:%S}] {msg}"
        print(line, flush=True)
        with run_log.open("a", encoding="utf-8") as f:
            f.write(line + "\n")

    def record(rec: dict) -> None:
        with progress.open("a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    log(f"lang={lang} items={len(items)} manifests={[p.name for p in paths]} out={out_dir}")
    # a long run must not stop when the Mac idles to sleep: caffeinate holds it awake until this process exits
    if shutil.which("caffeinate"):
        subprocess.Popen(["caffeinate", "-i", "-w", str(os.getpid())])
    runtime = FishS2ProRuntime.from_dir(args.model)
    references: dict[Path, object] = {}

    def reference_for(speaker: str):
        if args.ref_audio:
            wav, txt = args.ref_audio, args.ref_text_file
        else:
            folder = reference_dir(lang, speaker)
            wav, txt = folder / "ref.wav", folder / "ref.txt"
        if wav not in references:
            log(f"reference {wav}")
            references[wav] = runtime.encode_reference(str(wav), txt.read_text(encoding="utf-8").strip())
        return references[wav]

    def needs_voice(it: dict) -> bool:
        ogg = out_dir / f"{it['voice']}.ogg"
        own = own_reference(lang, it["speaker"])
        stale = own is not None and ogg.is_file() and ogg.stat().st_mtime < own.stat().st_mtime
        return args.force or not ogg.is_file() or (out_dir / f"{it['voice']}.PENDING_TTS").is_file() or stale

    # clips made but not yet checked: kept on disk, so a run stopped before its checking pass leaves them to the next
    unchecked_file = HERE / "out" / f"unchecked_{lang}.txt"
    unchecked_file.parent.mkdir(parents=True, exist_ok=True)
    unchecked = set(unchecked_file.read_text(encoding="utf-8").split()) if unchecked_file.is_file() else set()

    def save_unchecked() -> None:
        unchecked_file.write_text("".join(f"{v}\n" for v in sorted(unchecked)), encoding="utf-8")

    def tries_for(it: dict) -> list[tuple[str, int]]:
        """The first take, then the other seeds, then ending in "!" (Fish stops a very short line ending in "." or
        "..." too early, mid-word, whatever the seed; the subtitle keeps the original), then the manifest's short text."""
        text = for_tts(raw_text(it, lang))
        tries = [(text, args.seed + n) for n in range(args.attempts)]
        exclaimed = re.sub(r"(\.\.\.|…|\.)$", "!", text)
        if exclaimed != text:
            tries += [(exclaimed, args.seed), (exclaimed, args.seed + 1)]
        short = for_tts(it.get(f"{lang}_tts_short") or "")
        if short and short != text:
            tries.append((short, args.seed))
        return tries

    def synthesize(it: dict, line: str, seed: int, wav: Path) -> bool:
        limit = budget(line, lang)
        mx.random.seed(seed)
        try:
            result = runtime.synthesize(line, reference_audio=reference_for(it["speaker"]), max_new_tokens=limit)
            sf.write(str(wav), np.asarray(result.waveform, dtype=np.float32).squeeze(), int(result.sample_rate))
        except Exception as e:  # noqa: BLE001 - one bad line must not stop a 2700-line batch
            wav.unlink(missing_ok=True)
            log(f"{it['voice']} seed={seed} EXC {e}")
            return False
        stop = "length" if result.generated_tokens >= limit else "eos"
        log(f"{it['voice']} ({it['speaker']}) seed={seed} tokens={result.generated_tokens}/{limit} stop={stop} :: {line[:70]}")
        record({"voice": it["voice"], "lang": lang, "seed": seed, "text": line, "tokens": result.generated_tokens,
                "budget": limit, "stop": stop})
        return True

    def verdict_log(voice: str, verdict) -> str:
        return (f"{voice} dur={verdict.duration}s tail={verdict.tail_db}dB fade={verdict.fade_db}dB "
                f"word={verdict.word_score} {','.join(verdict.reasons) or 'ok'}")

    by_voice = {it["voice"]: it for it in items}
    todo = [it for it in items if needs_voice(it) and for_tts(raw_text(it, lang))]
    log(f"{len(todo)} clips to make, {len(unchecked & by_voice.keys())} made earlier wait for their check")
    counts = {"made": 0, "fail": 0, "checked": 0, "bad": 0, "remade": 0, "still_bad": 0}
    t0 = time.time()

    def pace() -> str:
        """Clips made of those to make, the pace so far and when making should end (the check comes after)."""
        done = counts["made"] + counts["fail"]
        per = (time.time() - t0) / done
        left = per * (len(todo) - done)
        finish = datetime.fromtimestamp(time.time() + left)
        return f"{done}/{len(todo)}, {per:.1f} s a clip, ~{int(left // 3600)} h {int(left % 3600 // 60)} min left, ends ~{finish:%a %H:%M}"

    try:
        # 1. every clip once, no checks: the model runs alone
        for it in todo:
            voice = it["voice"]
            wav = out_dir / f"{voice}.{lang}.wav"
            text, seed = tries_for(it)[0]
            if not synthesize(it, text, seed, wav):
                counts["fail"] += 1
            else:
                to_ogg(wav, out_dir / f"{voice}.ogg", args.ogg_q)
                wav.unlink(missing_ok=True)
                (out_dir / f"{voice}.PENDING_TTS").unlink(missing_ok=True)
                unchecked.add(voice)
                save_unchecked()
                counts["made"] += 1
            log(f"progress {pace()}")

        # 2. check what was made: loud tail, length, the last word through Whisper
        pending_check = sorted(v for v in unchecked if v in by_voice and (out_dir / f"{v}.ogg").is_file())
        transcribe = None
        if pending_check:
            log(f"checking {len(pending_check)} clips")
            transcribe = check.whisper()
        bad = {}
        for n, voice in enumerate(pending_check, 1):
            text = for_tts(raw_text(by_voice[voice], lang))
            verdict = check.check(out_dir / f"{voice}.ogg", text, lang, transcribe)
            counts["checked"] += 1
            if verdict.ok:
                unchecked.discard(voice)
                save_unchecked()
            else:
                bad[voice] = verdict
                counts["bad"] += 1
                log(f"[{n}/{len(pending_check)}] BAD {verdict_log(voice, verdict)}")

        # 3. remake the bad ones with the other takes, each checked, keeping the best
        if bad:
            log(f"remaking {len(bad)} bad clips")
        for voice, first in bad.items():
            it = by_voice[voice]
            best = (out_dir / f"{voice}.ogg", first, False)
            for line, seed in tries_for(it)[1:]:
                wav = out_dir / f"{voice}.{lang}.wav"
                if not synthesize(it, line, seed, wav):
                    continue
                verdict = check.check(wav, line, lang, transcribe)
                log(verdict_log(voice, verdict))
                if len(verdict.reasons) < len(best[1].reasons):
                    if best[2]:
                        best[0].unlink(missing_ok=True)
                    kept = wav.with_name(f"{voice}.{lang}.best.wav")
                    wav.replace(kept)
                    best = (kept, verdict, True)
                else:
                    wav.unlink(missing_ok=True)
                if verdict.ok:
                    break
            if best[2]:
                to_ogg(best[0], out_dir / f"{voice}.ogg", args.ogg_q)
                best[0].unlink(missing_ok=True)
                counts["remade"] += 1
            if not best[1].ok:
                counts["still_bad"] += 1
                log(f"{voice} STILL {','.join(best[1].reasons)}; add {lang}_tts_short in regen.json")
            unchecked.discard(voice)
            save_unchecked()
    finally:
        # lines are held as long as their longest clip: a new or stopped run must not leave words cut off
        if counts["made"] + counts["remade"] > 0:
            subprocess.run([sys.executable, str(ROOT / "tools/expand/scripts/duration_from_ogg.py")], check=False)

    log(f"done {counts} in {(time.time() - t0) / 60:.1f} min; log {run_log}")
    return 0 if counts["fail"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Lines for the trailers in one of the voices of voices.json (fal.ai ElevenLabs), a WAV each (48 kHz mono).

  python3 tools/trailer/tts.py VOICE LANG SCRIPT.json OUT_DIR [ID ...]
  python3 tools/trailer/tts.py player en script.json out/en P01 P04

SCRIPT.json: {"P01": {"en": "...", "ru": "..."}, ...}. The fal key: ~/.secrets/fal-key. Paid: 0.10 $ per 1000
characters; a line already in OUT_DIR is not made again (delete it to remake it).
"""
import json
import subprocess
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
KEY = (Path.home() / ".secrets/fal-key").read_text().strip()


def call(url, body=None):
    req = urllib.request.Request(url, json.dumps(body).encode() if body is not None else None,
                                 {"Authorization": "Key " + KEY, "Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=120))


def say(voice, lang, text, out):
    body = dict(voice["input"], text=text, language_code=lang)
    queued = call("https://queue.fal.run/" + voice["endpoint"], body)
    for _ in range(300):
        if call(queued["status_url"])["status"] == "COMPLETED":
            break
        time.sleep(1)
    audio = call(queued["response_url"])["audio"]["url"]
    mp3 = out.with_suffix(".mp3")
    urllib.request.urlretrieve(audio, mp3)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(mp3), "-ar", "48000", "-ac", "1", str(out)], check=True)
    mp3.unlink()


def main():
    name, lang, script_path, out_dir = sys.argv[1:5]
    only = set(sys.argv[5:])
    voice = json.loads((HERE / "voices.json").read_text())[name]
    script = json.loads(Path(script_path).read_text())
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    todo = [(k, v[lang]) for k, v in script.items() if (not only or k in only) and not (out / f"{k}.wav").exists()]
    print(f"{len(todo)} lines, {sum(len(t) for _, t in todo)} characters, ~{sum(len(t) for _, t in todo) / 10000:.3f} $")
    with ThreadPoolExecutor(6) as pool:
        for k, _ in zip([k for k, _ in todo], pool.map(lambda kt: say(voice, lang, kt[1], out / f"{kt[0]}.wav"), todo)):
            print("done", k)


if __name__ == "__main__":
    main()

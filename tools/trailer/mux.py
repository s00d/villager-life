#!/usr/bin/env python3
"""Clip + its slice of the game WAV -> <out>.mp4 (video copied, sound AAC).
   mux.py <recordings dir> <name> <out dir> [--keep]"""
import json, subprocess, sys, os
from pathlib import Path
rec, name, out = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
meta = json.loads((rec / f"{name}.json").read_text())
wav = Path(meta["wav"])
HEAD, FRAME = 68, 8                    # data chunk offset, bytes per stereo f32 sample
start = max(HEAD, (meta["wavStart"] - HEAD) // FRAME * FRAME + HEAD)
end = (meta["wavEnd"] - HEAD) // FRAME * FRAME + HEAD
pcm = out / f"{name}.f32"
with open(wav, "rb") as f:
    f.seek(start)
    pcm.write_bytes(f.read(end - start))
dst = out / f"{name}.mp4"
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(rec / f"{name}.mp4"),
                "-f", "f32le", "-ar", "48000", "-ac", "2", "-i", str(pcm),
                "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", str(dst)], check=True)
pcm.unlink()
(out / f"{name}.json").write_text(json.dumps(meta))
if "--keep" not in sys.argv:
    for ext in (".mp4", ".json", ".ffmpeg.log"):
        (rec / f"{name}{ext}").unlink(missing_ok=True)
print(dst, meta["frames"], "frames", meta["dropped"], "dropped", round((end - start) / 384000, 2), "s sound")

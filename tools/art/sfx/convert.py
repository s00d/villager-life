"""fal SFX (mp3, tools/art/fal/sfx) -> mono ogg in the mod's sounds/sfx: trailing silence trimmed, a short fade
out, peak at -1 dBFS. Minecraft only places and fades mono sounds with distance."""
import pathlib, subprocess, sys
import numpy as np, soundfile as sf

ROOT = pathlib.Path(__file__).resolve().parents[3]
SRC = ROOT / "tools/art/fal/sfx"
OUT = ROOT / "neoforge/src/main/resources/assets/villager_life/sounds/sfx"
NAMES = [line.split("\t")[0] for batch in sorted((ROOT / "tools/art/sfx").glob("batch*.tsv"))
         for line in batch.read_text().splitlines() if line.strip()]


def convert(name):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(SRC / f"{name}.mp3"), "-ac", "1", "-ar", "44100", "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    a = np.frombuffer(raw, dtype=np.float32).copy()
    peak = np.abs(a).max()
    if peak <= 0: raise SystemExit(f"{name}: silent")
    # trailing quiet (below -50 dB of the peak) cut, then 40 ms fade
    loud = np.nonzero(np.abs(a) > peak * 10 ** (-50 / 20))[0]
    a = a[: min(len(a), loud[-1] + 2205)]
    fade = min(len(a), 1764)
    a[-fade:] *= np.linspace(1, 0, fade)
    a *= 10 ** (-1 / 20) / peak
    OUT.mkdir(parents=True, exist_ok=True)
    sf.write(OUT / f"{name}.ogg", a, 44100, format="OGG", subtype="VORBIS")
    return len(a) / 44100


for name in sys.argv[1:] or NAMES:
    print(f"{name} {convert(name):.2f}s")

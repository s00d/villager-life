#!/usr/bin/env python3
"""9 frames of a video evenly spread -> 3x3 sheet png.  frames.py <video> <out.png> [labels...]"""
import subprocess, sys, json
from PIL import Image, ImageDraw
v, out = sys.argv[1], sys.argv[2]
dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", v]))
W, H = 640, 360
sheet = Image.new("RGB", (W * 3, H * 3))
for i in range(9):
    t = dur * (i + 0.5) / 9
    png = subprocess.check_output(["ffmpeg", "-v", "error", "-ss", str(t), "-i", v, "-frames:v", "1", "-vf", f"scale={W}:{H}", "-f", "image2pipe", "-vcodec", "png", "-"])
    from io import BytesIO
    im = Image.open(BytesIO(png)).convert("RGB")
    ImageDraw.Draw(im).text((6, 4), f"{t:.1f}s", fill=(255, 255, 0))
    sheet.paste(im, ((i % 3) * W, (i // 3) * H))
sheet.save(out)
print(out, round(dur, 2))

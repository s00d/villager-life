#!/usr/bin/env python3
"""Title cards of the second film as clips (work/card_<name>.mp4, silent sound track): a still of the world, blurred
and darkened, slowly pushing in; the logo eases in from a little larger with a warm glow, a line under it, the tagline
after. cards.py"""
import math
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HOME = Path.home() / "Movies/VillagerLife/film2"
W, H, FPS = 1920, 1080, 30
TITLE = ("/System/Library/Fonts/Palatino.ttc", 1)
TEXT = ("/System/Library/Fonts/Avenir Next.ttc", 2)
LIGHT = ("/System/Library/Fonts/Avenir Next.ttc", 0)


def font(spec, size):
    return ImageFont.truetype(spec[0], size, index=spec[1])


def ease(x):
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


def still(clip, at):
    png = HOME / "work/tmp/still.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    src = HOME / "work/en" / f"{clip}.mp4"
    if not src.exists():
        src = HOME / "work" / f"{clip}.mp4"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(at), "-i", str(src), "-frames:v", "1", str(png)], check=True)
    return Image.open(png).convert("RGB").resize((W, H))


def text_layer(lines, t):
    """lines: (text, font, colour, y, appears at, spacing)"""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d, g = ImageDraw.Draw(layer), ImageDraw.Draw(glow)
    for text, f, colour, y, start, spacing in lines:
        a = ease((t - start) / 0.9)
        if a <= 0:
            continue
        scale = 1.0 + 0.08 * (1 - a)
        fs = font(f, int(f[2] * scale)) if len(f) > 2 else f
        box = d.textbbox((0, 0), text, font=fs)
        w = box[2] - box[0] + spacing * max(0, len(text) - 1)
        x = (W - w) // 2
        yy = y - (box[3] - box[1]) // 2 + int(14 * (1 - a))
        for ch in text:
            cw = d.textlength(ch, font=fs)
            g.text((x, yy), ch, font=fs, fill=(255, 170, 60, int(160 * a)))
            d.text((x + 3, yy + 4), ch, font=fs, fill=(10, 6, 2, int(170 * a)))
            d.text((x, yy), ch, font=fs, fill=colour[:3] + (int(255 * a),))
            x += cw + spacing
    glow = glow.filter(ImageFilter.GaussianBlur(22))
    return Image.alpha_composite(glow, layer)


def card(name, clip, at, seconds, lines, rule_y=None, rule_at=0.0, lang="en"):
    base = still(clip, at).filter(ImageFilter.GaussianBlur(14))
    out = HOME / "work" / (f"card_{name}.mp4" if lang == "en" else f"{lang}/card_{name}.mp4")
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
                            "-i", "-", "-f", "lavfi", "-t", str(seconds), "-i", "anullsrc=r=48000:cl=stereo",
                            "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", str(out)],
                           stdin=subprocess.PIPE)
    n = int(seconds * FPS)
    for i in range(n):
        t = i / FPS
        # a slow push in on the world, darkened most at the edges
        z = 1.0 + 0.05 * t / seconds
        cw, ch = int(W / z), int(H / z)
        frame = base.crop(((W - cw) // 2, (H - ch) // 2, (W + cw) // 2, (H + ch) // 2)).resize((W, H))
        shade = Image.new("L", (W, H), 0)
        sd = ImageDraw.Draw(shade)
        for r in range(12):
            k = r / 12
            sd.ellipse((-W * 0.25 + k * W * 0.25, -H * 0.3 + k * H * 0.3, W * 1.25 - k * W * 0.25, H * 1.3 - k * H * 0.3), fill=int(110 + 120 * k))
        dark = Image.new("RGB", (W, H), (8, 6, 12))
        frame = Image.composite(frame, dark, shade.filter(ImageFilter.GaussianBlur(60)))
        frame = Image.blend(Image.new("RGB", (W, H), (0, 0, 0)), frame, 0.45)
        img = frame.convert("RGBA")
        if rule_y is not None:
            a = ease((t - rule_at) / 0.8)
            if a > 0:
                d = ImageDraw.Draw(img)
                half = int(260 * a)
                d.rectangle((W // 2 - half, rule_y, W // 2 + half, rule_y + 3), fill=(255, 200, 110, int(220 * a)))
        img = Image.alpha_composite(img, text_layer(lines, t))
        # in from black, out to black
        k = min(1.0, t / 0.6, (seconds - t) / 0.6)
        img = Image.blend(Image.new("RGB", (W, H), (0, 0, 0)), img.convert("RGB"), max(0.0, k))
        enc.stdin.write(img.tobytes())
    enc.stdin.close()
    enc.wait()
    print(out)


TEXTS = {
    "en": ("VILLAGER  LIFE", "a village that lives on its own", "Villagers that live.  A spire that waits.",
           "Minecraft  ·  NeoForge  ·  voiced in English and Russian"),
    "ru": ("ЖИЗНЬ  ДЕРЕВНИ", "деревня, которая живёт сама", "Жители живут.  Шпиль ждёт.",
           "Minecraft  ·  NeoForge  ·  озвучено на русском и английском"),
}


def main():
    import sys
    lang = sys.argv[1] if len(sys.argv) > 1 else "en"
    title, intro, outro, small = TEXTS[lang]
    gold, white, grey = (255, 222, 150), (250, 246, 236), (205, 200, 190)
    card("intro", "p1_s01_arrive", 1.0, 4.2, [
        (title, TITLE + (150 if lang == "en" else 130,), gold, H // 2 - 50, 0.3, 6),
        (intro, TEXT + (52,), white, H // 2 + 80, 1.3, 1),
    ], rule_y=H // 2 - 150, rule_at=0.9, lang=lang)
    card("outro", "p2_summit", 6.0, 7.0, [
        (title, TITLE + (170 if lang == "en" else 146,), gold, H // 2 - 70, 0.3, 8),
        (outro, TEXT + (54,), white, H // 2 + 70, 1.4, 1),
        (small, LIGHT + (38,), grey, H // 2 + 150, 2.2, 1),
    ], rule_y=H // 2 - 180, rule_at=1.0, lang=lang)


if __name__ == "__main__":
    main()

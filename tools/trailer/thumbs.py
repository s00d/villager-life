#!/usr/bin/env python3
"""YouTube thumbnails (1280×720) for both films, EN and RU, from the films' own frames:
  film1 — the walled town at sunset with the spire, the title;
  film2 — split on a slant: the quiet village (left) and a keeper of the spire (right), the hook line.
thumbs.py -> ~/Movies/VillagerLife/publish/thumb_film{1,2}_{en,ru}.jpg"""
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageEnhance

FILMS = Path.home() / "Movies/VillagerLife"
OUT = FILMS / "publish"
W, H = 1280, 720
HEAVY = ("/System/Library/Fonts/Supplemental/Arial Black.ttf", 0)
BOLD = ("/System/Library/Fonts/Avenir Next.ttc", 2)
SERIF = ("/System/Library/Fonts/Palatino.ttc", 1)


def font(spec, size):
    return ImageFont.truetype(spec[0], size, index=spec[1])


def frame(clip, at):
    png = OUT / "frame.png"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(at), "-i", str(clip), "-frames:v", "1", str(png)], check=True)
    img = Image.open(png).convert("RGB")
    png.unlink()
    return img


def cover(img, w, h, focus=0.5):
    """Scaled to fill w×h, cropped around a horizontal focus (0 left .. 1 right)."""
    s = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    x = round((img.width - w) * focus)
    y = (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def text(draw, xy, s, f, fill, stroke=8, anchor="la"):
    draw.text(xy, s, font=f, fill=fill, stroke_width=stroke, stroke_fill=(12, 8, 4), anchor=anchor)


def glow(img, xy, s, f, colour, radius=18, anchor="la"):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, s, font=f, fill=colour, stroke_width=10, stroke_fill=colour, anchor=anchor)
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)))


def film1(lang):
    base = cover(frame(FILMS / "film1/work/en/s17_finale.mp4", 6), W, H, 0.35)
    base = ImageEnhance.Color(base).enhance(1.25)
    img = base.convert("RGBA")
    # darker at the left, where the words go
    shade = Image.new("L", (W, H))
    for x in range(W):
        ImageDraw.Draw(shade).line([(x, 0), (x, H)], fill=int(max(0, 190 - x * 0.32)))
    img.alpha_composite(Image.merge("RGBA", (*[Image.new("L", (W, H), 0)] * 3, shade)))
    d = ImageDraw.Draw(img)
    title, line1, line2 = (("VILLAGER", "LIFE", "villagers that live, build and love"),
                           ("ЖИЗНЬ", "ДЕРЕВНИ", "жители живут, строят и любят"))[lang == "ru"]
    big = font(SERIF, 150 if lang == "en" else 132)
    glow(img, (60, 70), title, big, (255, 170, 60, 140))
    glow(img, (60, 230), line1, big, (255, 170, 60, 140))
    text(d, (60, 70), title, big, (255, 228, 160))
    text(d, (60, 230), line1, big, (255, 228, 160))
    d.rectangle((66, 410, 66 + 360, 416), fill=(255, 200, 110))
    text(d, (64, 440), line2, font(BOLD, 44), (250, 246, 236), stroke=6)
    tag = ("Minecraft mod · NeoForge", "мод для Minecraft · NeoForge")[lang == "ru"]
    text(d, (64, 510), tag, font(BOLD, 34), (210, 205, 195), stroke=5)
    return img.convert("RGB")


def film2(lang):
    left = cover(frame(FILMS / "film2/work/en/p1_s06_return.mp4", 8), W, H, 0.3)
    right = cover(frame(FILMS / "film2/work/p2_f080_keeper.mp4", 10), W, H, 0.55)
    right = ImageEnhance.Contrast(ImageEnhance.Color(right).enhance(1.3)).enhance(1.1)
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon([(W * 0.52, 0), (W, 0), (W, H), (W * 0.40, H)], fill=255)
    img = Image.composite(right, left, mask).convert("RGBA")
    d = ImageDraw.Draw(img)
    # the slash between the two worlds
    d.line([(W * 0.52, 0), (W * 0.40, H)], fill=(255, 255, 255), width=10)
    d.line([(W * 0.52 + 9, 0), (W * 0.40 + 9, H)], fill=(170, 90, 255), width=6)
    # a dark band at the top for the words
    band = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(band).rectangle((0, 0, W, 190), fill=(0, 0, 0, 150))
    img.alpha_composite(band.filter(ImageFilter.GaussianBlur(20)))
    d = ImageDraw.Draw(img)
    hook = ("I INSTALLED ONE MOD...", "ПОСТАВИЛ ОДИН МОД...")[lang == "ru"]
    f = font(HEAVY, 80 if lang == "en" else 72)
    glow(img, (W // 2, 30), hook, f, (255, 210, 80, 150), anchor="ma")
    text(d, (W // 2, 30), hook, f, (255, 236, 120), stroke=9, anchor="ma")
    a, b = (("3 DAYS LATER", "THE SPIRE"), ("ЧЕРЕЗ 3 ДНЯ", "ШПИЛЬ"))[lang == "ru"]
    small = font(HEAVY, 54)
    text(d, (40, H - 100), a, small, (255, 255, 255), stroke=7)
    text(d, (W - 40, H - 100), b, small, (230, 200, 255), stroke=7, anchor="ra")
    name = font(BOLD, 30)
    text(d, (40, 200), "Villager Life", name, (240, 240, 240), stroke=5)
    return img.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for lang in ("en", "ru"):
        for n, make in ((1, film1), (2, film2)):
            path = OUT / f"thumb_film{n}_{lang}.jpg"
            make(lang).save(path, quality=92)
            print(path)


if __name__ == "__main__":
    main()

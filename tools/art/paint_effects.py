#!/usr/bin/env python3
"""Status effect icons (textures/mob_effect/<name>.png, 18x18 like vanilla): the Spire's afflictions and the parish
hymn. Each a small picture of its own on a transparent ground, dark outline, two tones."""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/villager_life/textures/mob_effect"
S = 18


def canvas():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def outline(im):
    """A one-pixel dark edge round whatever is drawn, as vanilla icons have."""
    px = im.load()
    out = im.copy()
    po = out.load()
    for x in range(S):
        for y in range(S):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and px[nx, ny][3] > 200:
                    po[x, y] = (24, 18, 20, 255)
                    break
    return out


def entangled():
    im, d = canvas()
    d.arc((3, 3, 14, 14), 200, 520, fill=(96, 140, 52), width=2)
    d.arc((5, 1, 12, 16), 20, 330, fill=(70, 104, 38), width=2)
    d.line((8, 15, 10, 9), fill=(120, 84, 48), width=2)
    for p in ((4, 6), (13, 11), (9, 3)):
        d.point(p, fill=(150, 200, 80))
    return im


def inked():
    im, d = canvas()
    d.ellipse((4, 7, 13, 16), fill=(30, 26, 52))
    d.polygon(((8, 1), (4, 10), (13, 10)), fill=(30, 26, 52))
    d.ellipse((6, 9, 8, 11), fill=(92, 84, 140))
    d.ellipse((13, 13, 15, 15), fill=(30, 26, 52))
    d.ellipse((2, 13, 3, 14), fill=(30, 26, 52))
    return im


def jolted():
    im, d = canvas()
    d.polygon(((10, 1), (4, 10), (8, 10), (6, 17), (14, 7), (10, 7), (12, 1)), fill=(240, 176, 60))
    d.line((9, 3, 7, 8), fill=(255, 236, 160))
    return im


def stopped():
    im, d = canvas()
    d.ellipse((2, 2, 15, 15), fill=(200, 190, 168))
    d.ellipse((4, 4, 13, 13), fill=(236, 228, 206))
    d.line((8, 8, 8, 4), fill=(60, 50, 40), width=1)
    d.line((8, 8, 11, 9), fill=(60, 50, 40), width=1)
    for x, y in ((8, 3), (13, 8), (8, 13), (3, 8)):
        d.point((x, y), fill=(120, 100, 80))
    d.line((1, 16, 16, 1), fill=(150, 60, 50), width=1)
    return im


def sapped():
    im, d = canvas()
    d.polygon(((3, 14), (6, 4), (14, 2), (12, 11)), fill=(110, 170, 64))
    d.line((4, 13, 12, 4), fill=(70, 110, 40))
    d.ellipse((11, 11, 16, 17), fill=(196, 40, 48))
    d.point((12, 12), fill=(250, 140, 140))
    return im


def waterlogged():
    im, d = canvas()
    for box, c in (((2, 8, 9, 15), (70, 140, 210)), ((9, 3, 15, 9), (90, 160, 225)), ((10, 12, 14, 16), (70, 140, 210)), ((4, 2, 7, 5), (110, 180, 235))):
        d.ellipse(box, outline=c, width=1, fill=(40, 90, 150, 120))
        d.point((box[0] + 1, box[1] + 1), fill=(220, 240, 255))
    return im


def frostbite():
    im, d = canvas()
    c = (176, 220, 245)
    for a, b in (((8, 1), (8, 16)), ((1, 8), (16, 8)), ((3, 3), (13, 13)), ((3, 13), (13, 3))):
        d.line(a + b, fill=c, width=1)
    for p in ((8, 3), (8, 14), (3, 8), (14, 8)):
        d.ellipse((p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1), fill=(230, 248, 255))
    d.ellipse((6, 6, 10, 10), fill=(120, 190, 235))
    return im


def blight():
    im, d = canvas()
    for box in ((3, 6, 9, 12), (8, 3, 14, 9), (8, 10, 15, 16), (1, 12, 5, 16)):
        d.ellipse(box, fill=(150, 100, 172))
        d.point((box[0] + 2, box[1] + 1), fill=(220, 180, 236))
    d.point((12, 12), fill=(90, 50, 110))
    return im


def stung():
    im, d = canvas()
    d.ellipse((3, 5, 12, 12), fill=(232, 180, 32))
    d.line((5, 5, 5, 12), fill=(40, 30, 20), width=1)
    d.line((8, 5, 8, 12), fill=(40, 30, 20), width=1)
    d.polygon(((12, 8), (16, 9), (12, 10)), fill=(40, 30, 20))
    d.ellipse((4, 1, 9, 5), fill=(220, 236, 250, 200))
    return im


def honeyed():
    im, d = canvas()
    d.rectangle((2, 2, 15, 6), fill=(220, 150, 30))
    for x, h in ((3, 12), (7, 9), (11, 15), (14, 8)):
        d.line((x, 6, x, h), fill=(220, 150, 30), width=2)
        d.ellipse((x - 1, h - 1, x + 1, h + 1), fill=(240, 180, 50))
    d.line((3, 3, 12, 3), fill=(255, 220, 120))
    return im


def starmarked():
    im, d = canvas()
    d.ellipse((1, 9, 16, 16), outline=(200, 180, 255), width=1)
    d.polygon(((8, 0), (10, 5), (15, 5), (11, 8), (13, 13), (8, 10), (3, 13), (5, 8), (1, 5), (6, 5)), fill=(250, 240, 200))
    d.point((8, 6), fill=(255, 255, 255))
    return im


def doom():
    im, d = canvas()
    d.polygon(((8, 1), (13, 6), (14, 13), (2, 13), (3, 6)), fill=(140, 36, 64))
    d.rectangle((1, 13, 15, 15), fill=(110, 24, 50))
    d.ellipse((7, 14, 9, 17), fill=(60, 10, 26))
    d.line((6, 4, 5, 11), fill=(200, 90, 120))
    return im


def sundered():
    im, d = canvas()
    d.polygon(((2, 2), (15, 2), (15, 9), (8, 16), (2, 9)), fill=(150, 136, 120))
    d.polygon(((4, 4), (13, 4), (13, 9), (8, 14), (4, 9)), fill=(190, 176, 156))
    d.line((9, 2, 7, 7, 10, 10, 7, 16), fill=(40, 30, 28), width=1)
    return im


def parish_hymn():
    im, d = canvas()
    d.rectangle((7, 2, 10, 15), fill=(228, 197, 122))
    d.rectangle((3, 5, 14, 8), fill=(228, 197, 122))
    d.line((8, 3, 8, 14), fill=(255, 240, 190))
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for draw in (entangled, inked, jolted, stopped, sapped, waterlogged, frostbite, blight, stung, honeyed, starmarked, doom, sundered, parish_hymn):
        outline(draw()).save(OUT / f"{draw.__name__}.png")
    print("effects:", OUT)


if __name__ == "__main__":
    main()

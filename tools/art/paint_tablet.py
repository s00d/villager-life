"""The spire's tablet window (textures/gui/spire_tablet.png, 256x256): a sheet of old parchment on a stone slab, drawn
here (no generated images). Atlas:

    (0,0)   parchment 200x180 with a darker rim and a pale vein of the Core across it
    (200,0) wax seal 24x24 (the Builders' sign: a ring and a spire)
    (200,24) arrow right 12x12, (212,24) arrow left 12x12
    (0,180) stone slab edge strip 256x12 (the slab's frame, tiled)
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life", "textures", "gui", "spire_tablet.png")

PAPER = (231, 214, 178)
PAPER_DARK = (196, 168, 120)
RIM = (150, 112, 70)
VEIN = (176, 156, 214)


def noise(x, y, seed):
    r = random.Random(x * 73856093 ^ y * 19349663 ^ seed)
    return r.random()


def smooth(x, y, scale, seed):
    gx, gy = x / scale, y / scale
    x0, y0 = int(gx), int(gy)
    fx, fy = gx - x0, gy - y0
    a = noise(x0, y0, seed)
    b = noise(x0 + 1, y0, seed)
    c = noise(x0, y0 + 1, seed)
    d = noise(x0 + 1, y0 + 1, seed)
    top = a + (b - a) * fx
    bottom = c + (d - c) * fx
    return top + (bottom - top) * fy


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def parchment(atlas):
    w, h = 200, 180
    for y in range(h):
        for x in range(w):
            n = 0.55 * smooth(x, y, 14, 1) + 0.3 * smooth(x, y, 5, 2) + 0.15 * noise(x, y, 3)
            colour = mix(PAPER, PAPER_DARK, max(0.0, n - 0.35) * 1.2)
            # a darker, burnt-looking rim, ragged by noise
            edge = min(x, y, w - 1 - x, h - 1 - y) + smooth(x, y, 6, 4) * 4
            if edge < 6:
                colour = mix(colour, RIM, (6 - edge) / 6)
            # the Core's pale vein running across the sheet
            vy = 166 + 3 * math.sin(x / 17.0)
            d = abs(y - vy)
            if d < 3 and 10 < x < w - 10:
                colour = mix(colour, VEIN, (3 - d) / 6)
            alpha = 0 if edge < 1.2 else 255
            atlas.putpixel((x, y), colour + (alpha,))


def seal(atlas, ox, oy):
    for y in range(24):
        for x in range(24):
            d = math.hypot(x - 11.5, y - 11.5)
            if d > 11.5:
                continue
            base = (150, 40, 44) if d < 10 else (110, 26, 30)
            if 6 < d < 7.4:
                base = (200, 90, 90)
            if abs(x - 11.5) < 1.2 and 5 < y < 18:
                base = (220, 120, 110)
            if 15 < y < 18 and abs(x - 11.5) < 4:
                base = (220, 120, 110)
            atlas.putpixel((ox + x, oy + y), base + (255,))


def arrow(atlas, ox, oy, right):
    for y in range(12):
        for x in range(12):
            tip = 11 - x if right else x
            if abs(y - 5.5) <= tip / 2.0 and tip < 10:
                atlas.putpixel((ox + x, oy + y), (110, 74, 40, 255))


def slab(atlas):
    for y in range(12):
        for x in range(256):
            n = smooth(x, y, 4, 9)
            g = int(96 + 50 * n - (8 if y in (0, 11) else 0))
            atlas.putpixel((x, 180 + y), (g, g, g + 6, 255))


def main():
    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    parchment(atlas)
    seal(atlas, 200, 0)
    arrow(atlas, 200, 24, True)
    arrow(atlas, 212, 24, False)
    slab(atlas)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)


if __name__ == "__main__":
    main()

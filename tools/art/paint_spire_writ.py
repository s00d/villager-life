#!/usr/bin/env python3
"""The Builders' Writ (spire/SpireWritItem): a parchment held open between two dark rollers with brass knobs, laid
corner to corner, three lines of the Builders' violet glyphs, and a wax seal on a ribbon with the spire's mark in gold.
Pixel art drawn here, no generation:  textures/item/spire_writ.png (16×16)
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "neoforge/src/main/resources/assets/villager_life/textures/item/spire_writ.png"

PARCH = (240, 226, 188)
PARCH_SHADE = (212, 188, 136)
PARCH_EDGE = (150, 112, 62)
WOOD = (92, 58, 34)
WOOD_LIGHT = (128, 86, 50)
BRASS = (230, 182, 84)
BRASS_DARK = (150, 104, 36)
GLYPH = (124, 78, 206)
GLYPH_LIGHT = (176, 136, 240)
WAX = (112, 36, 150)
WAX_LIGHT = (176, 96, 222)
WAX_DARK = (64, 18, 92)
GOLD = (246, 208, 96)
RIBBON = (168, 40, 64)


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c + (255,)

    # along: t = x - y (bottom-left -13 .. top-right 13), across: w = x + y - 15
    for y in range(16):
        for x in range(16):
            t, w = x - y, x + y - 15
            if -8 <= t <= 8 and abs(w) <= 3:
                edge = abs(w) == 3 or abs(t) == 8
                put(x, y, PARCH_EDGE if edge else PARCH_SHADE if abs(w) == 2 or abs(t) >= 7 else PARCH)
    # the glyph lines: dashes along three rows, not to the edges
    for w, phase in ((-1, 0), (1, 1)):
        for t in range(-6, 7):
            if (t + phase) % 3 == 2:
                continue
            # x - y = t, x + y = 15 + w
            if (t + 15 + w) % 2:
                continue
            x, y = (t + 15 + w) // 2, (15 + w - t) // 2
            put(x, y, GLYPH_LIGHT if t % 4 == 0 else GLYPH)
    # the rollers, a little wider than the page, brass knobs at their ends
    for t0 in (-10, 9):
        for t in (t0, t0 + 1):
            for w in range(-4, 5):
                if (t + 15 + w) % 2:
                    continue
                x, y = (t + 15 + w) // 2, (15 + w - t) // 2
                put(x, y, WOOD_LIGHT if t == t0 else WOOD)
        for w in (-5, 5):
            for t in (t0, t0 + 1):
                if (t + 15 + w) % 2 == 0:
                    put((t + 15 + w) // 2, (15 + w - t) // 2, BRASS)
    # odd cells the diagonal walk above misses on the rollers: fill between their neighbours
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                t, w = x - y, x + y - 15
                if (t in (-10, -9, 9, 10)) and abs(w) <= 4:
                    put(x, y, WOOD)
                elif abs(w) == 5 and t in (-10, -9, 9, 10):
                    put(x, y, BRASS if (x + y) % 2 else BRASS_DARK)
    # the ribbon from the page's middle down to the seal
    for x, y in ((8, 9), (9, 10), (9, 11)):
        put(x, y, RIBBON)
    # the seal: a round of violet wax, light on its upper left, the spire's mark in gold
    cx, cy = 10.5, 12.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.6:
                put(x, y, WAX_DARK if d > 2.0 else WAX_LIGHT if (x - cx) + (y - cy) < -1.2 else WAX)
    # the spire's mark: a needle on a step
    for x, y in ((10, 10), (10, 11), (10, 12), (9, 13), (10, 13), (11, 13)):
        put(x, y, GOLD)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()

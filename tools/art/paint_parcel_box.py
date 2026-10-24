#!/usr/bin/env python3
"""The parcel box by a stall (block/town/ParcelBoxBlock): a crate of dark planks with iron bands at its corners, a
brass name plate and a wax seal on its front, a lid tied with twine. Pixel art drawn here, no generation:
  textures/block/town/parcel_box_{side,front,top}.png (16x16)"""
from pathlib import Path
import random
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/villager_life/textures/block/town"
PLANK = [(92, 62, 38), (104, 72, 44), (84, 56, 34), (112, 80, 50)]
IRON = (70, 72, 78)
IRON_LIGHT = (132, 136, 144)
BRASS = (214, 170, 82)
BRASS_DARK = (150, 110, 44)
TWINE = (196, 168, 112)
WAX = (160, 40, 40)


def planks(rng, vertical=False):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            board = (x if vertical else y) // 4
            c = PLANK[(board * 3 + 1) % len(PLANK)]
            j = rng.randint(-6, 6)
            if (y if vertical else x) % 7 == 3 and rng.random() < 0.35:
                j -= 14
            if (x if vertical else y) % 4 == 3:
                j -= 22
            img.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in c) + (255,))
    return img


def bands(img):
    for y in range(4, 16):
        for x in (1, 14):
            img.putpixel((x, y), IRON + (255,))
        img.putpixel((2, y), IRON_LIGHT + (255,)) if y % 3 == 0 else None
    for x in range(1, 15):
        img.putpixel((x, 4), IRON + (255,))
        img.putpixel((x, 15), IRON + (255,))
    for (x, y) in ((1, 4), (14, 4), (1, 15), (14, 15)):
        img.putpixel((x, y), IRON_LIGHT + (255,))


def main():
    rng = random.Random(7781)
    OUT.mkdir(parents=True, exist_ok=True)
    side = planks(rng)
    bands(side)
    side.save(OUT / "parcel_box_side.png")
    front = planks(rng)
    bands(front)
    for y in range(7, 11):
        for x in range(4, 12):
            front.putpixel((x, y), (BRASS if y < 10 and x < 11 else BRASS_DARK) + (255,))
    for x in range(5, 11):
        front.putpixel((x, 8), (110, 80, 34, 255) if x % 2 else BRASS_DARK + (255,))
    for (x, y) in ((7, 12), (8, 12), (7, 13), (8, 13), (6, 12), (9, 13)):
        front.putpixel((x, y), WAX + (255,))
    front.save(OUT / "parcel_box_front.png")
    top = planks(rng, vertical=True)
    for i in range(1, 15):
        top.putpixel((i, 8), TWINE + (255,))
        top.putpixel((8, i), TWINE + (255,))
    for (x, y) in ((7, 7), (9, 9), (7, 9), (9, 7)):
        top.putpixel((x, y), (150, 120, 70, 255))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        top.putpixel((x, y), IRON + (255,))
    top.save(OUT / "parcel_box_top.png")


if __name__ == "__main__":
    main()

"""The tower clock's textures (hand-drawn pixel art, no generation): its 3x3 dial with the two hands
(textures/block/tower_clock_dial.png, the layout in client/TowerClockRenderer) and the block's small front."""
import math, pathlib
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/villager_life/textures/block"
IRON, IRON_HI = (38, 35, 40, 255), (74, 70, 78, 255)
GOLD, GOLD_DK = (214, 170, 72, 255), (150, 110, 46, 255)
FACE, FACE_DK, LINE = (238, 228, 200, 255), (218, 204, 170, 255), (120, 98, 70, 255)


def dial(img, size, ox=0, oy=0):
    c = (size - 1) / 2
    r = size / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d > r - 0.2:
                continue
            if d > r - 1.3:
                col = IRON
            elif d > r - 2.4:
                col = GOLD if (x + y) % 5 else GOLD_DK
            elif d > r - 3.4:
                col = IRON_HI
            else:
                # the face, a little darker towards its edge
                col = FACE if d < r * 0.7 else FACE_DK
                # the chapter ring the hour marks stand in
                if abs(d - (r - 9.5)) < 0.5 and size > 20:
                    col = LINE
            img.putpixel((ox + x, oy + y), col)
    # the hours: a bar at each, long and doubled at the quarters
    for h in range(12):
        a = math.radians(h * 30)
        quarter = h % 3 == 0
        outer, inner = (r - 4, r - (9 if quarter else 7)) if size > 20 else (r - 3, r - 4)
        for t in [i / 4 for i in range(int(outer * 4), int(inner * 4), -1)]:
            for w in ((-0.5, 0.5) if quarter and size > 20 else (0,)):
                x = c + math.sin(a) * t + math.cos(a) * w
                y = c - math.cos(a) * t + math.sin(a) * w
                img.putpixel((ox + round(x), oy + round(y)), IRON)
    # the boss the hands turn on
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            img.putpixel((ox + round(c) + dx, oy + round(c) + dy), GOLD if dx or dy else IRON)


def hand(img, x0, rows, tip):
    """A hand 4 wide, its tip at row 0, a spade head {tip} rows long, the pivot 3 rows above the bottom."""
    for y in range(rows):
        if y < tip:
            # a spear head: a point, the full width, narrowing back to the shaft
            xs = range(1, 3) if y == 0 or y == tip - 1 else range(0, 4)
        elif y >= rows - 3:
            xs = range(1, 3)  # the counterweight's stub
        else:
            xs = range(1, 3)
        for x in xs:
            img.putpixel((x0 + x, y), IRON if x in (1, 2) else IRON_HI)
    img.putpixel((x0 + 1, rows - 3), GOLD)
    img.putpixel((x0 + 2, rows - 3), GOLD)


img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
dial(img, 48)
hand(img, 52, 16, 5)
hand(img, 58, 24, 4)
img.save(OUT / "tower_clock_dial.png")

front = Image.open(pathlib.Path.home() / ".gradle_unused") if False else None
small = Image.new("RGBA", (16, 16), (122, 121, 122, 255))
# stone bricks under a small clock face (the block in hand, and where the big dial is not drawn)
for y in range(16):
    for x in range(16):
        mortar = y % 4 == 3 or (x + (0 if (y // 4) % 2 else 4)) % 8 == 7
        small.putpixel((x, y), (100, 99, 100, 255) if mortar else (128, 127, 128, 255) if (x * 7 + y * 3) % 5 else (138, 137, 138, 255))
dial(small, 12, 2, 2)
small.putpixel((8, 4), IRON)
small.putpixel((8, 5), IRON)
small.putpixel((9, 7), IRON)
small.save(OUT / "tower_clock_front.png")
print("tower_clock_dial.png, tower_clock_front.png")

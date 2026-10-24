#!/usr/bin/env python3
"""The Spire's section draughts (spire/Draughts): ten reagents (one a chapter, from its floors' chests), ten draughts (a
chapter's liquid in the Builders' flask: a round body, a long neck, a brass collar and a wax seal in the chapter's
colour) and ten ward icons (a shield with the chapter's mark). Pixel art drawn here, no generation:
  textures/item/spire_reagent_<chapter>.png, spire_draught_<chapter>.png  (16×16)
  textures/mob_effect/spire_ward_<chapter>.png                             (18×18)
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life/textures"
TIERS = ["root", "archive", "clockwork", "garden", "deep", "ice", "spore", "hive", "star", "summit"]
# liquid / light / dark per chapter (Draughts.COLOURS is the middle one)
COLOURS = {
    "root": (0x7A9A3A, 0xB4D060, 0x3E5A1A), "archive": (0x4A4A9A, 0x8484D8, 0x24244E),
    "clockwork": (0xD0962E, 0xF4CC6A, 0x7A5214), "garden": (0xE87AA8, 0xFFB6D2, 0x9A3E68),
    "deep": (0x2A9AB0, 0x7AD8E8, 0x135260), "ice": (0x9ADCF4, 0xE6F8FF, 0x4E92B4),
    "spore": (0xA070C0, 0xD6AEF0, 0x5A3478), "hive": (0xE8B020, 0xFFE07A, 0x8A5E0A),
    "star": (0x7A6AF0, 0xC0B6FF, 0x3A2E9A), "summit": (0xF0E6C8, 0xFFFFFF, 0xA8946A),
}
GLASS = (0xCFE6EE, 0x8FB0BC, 0x5A7480)
BRASS = (0xE0B050, 0xA07020, 0x5A3C10)
CORK = (0xB8885A, 0x7A5634)


def rgb(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def new(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


# every chapter its own vessel (one flask in ten colours read as a vanilla potion): body mask, liquid line, neck, stopper
def _round(cx, cy, r):
    return lambda x, y: ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= r


VESSELS = {
    # a gourd: two bellies, twine round the waist
    "root": dict(body=lambda x, y: _round(7.5, 11, 4.4)(x, y) or _round(7.5, 5.8, 2.6)(x, y), level=6, neck=(7, 8, 2, 3), band=[(y, 8) for y in ()],
                 twine=8),
    # a squat ink bottle with square shoulders
    "archive": dict(body=lambda x, y: 3 <= x <= 12 and 7 <= y <= 14 and not ((x in (3, 12)) and y in (7, 14)), level=9, neck=(6, 9, 4, 6)),
    # a vial in a brass cage
    "clockwork": dict(body=lambda x, y: 5 <= x <= 10 and 4 <= y <= 14, level=7, neck=(6, 9, 2, 3), cage=True),
    # a drop-shaped flask, a leaf on the stopper
    "garden": dict(body=lambda x, y: _round(7.5, 10.5, 4.3)(x, y) or (y >= 4 and abs(x - 7.5) <= (y - 3) * 0.75 and y <= 10), level=8, neck=(7, 8, 2, 3), leaf=True),
    # a tall sea-glass bottle with bubbles
    "deep": dict(body=lambda x, y: 4 <= x <= 11 and 5 <= y <= 14 and not (x in (4, 11) and y in (5, 14)), level=6, neck=(6, 9, 2, 4), bubbles=True),
    # a pointed crystal phial
    "ice": dict(body=lambda x, y: abs(x - 7.5) + abs(y - 9.5) * 0.7 <= 4.6, level=8, neck=(7, 8, 2, 5), spikes=True),
    # a round flask under a mushroom cap
    "spore": dict(body=_round(7.5, 10.5, 4.6), level=9, neck=(7, 8, 4, 5), cap=True),
    # a hexagonal honey jar
    "hive": dict(body=lambda x, y: 3 <= x <= 12 and 6 <= y <= 14 and abs(x - 7.5) <= 4.5 - max(0, abs(y - 10) - 2.5) * 0.9, level=8, neck=(5, 10, 4, 5), lid=True),
    # a slender vial, a star on the stopper
    "star": dict(body=lambda x, y: 6 <= x <= 9 and 4 <= y <= 14 or _round(7.5, 12.5, 2.4)(x, y), level=6, neck=(7, 8, 2, 3), star=True),
    # the Builders' flask: round body, long neck, brass collar, a ring of light
    "summit": dict(body=_round(7.5, 10.2, 5.3), level=8, neck=(6, 9, 2, 5), collar=True),
}


def draught(tier):
    liquid, light, dark = (rgb(c) for c in COLOURS[tier])
    v = VESSELS[tier]
    body = v["body"]
    img = new()
    cells = [(x, y) for y in range(16) for x in range(16) if body(x, y)]
    inside = set(cells)
    xs = [x for x, _ in cells]
    ys = [y for _, y in cells]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for (x, y) in cells:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if y >= v["level"]:
            t = max(0.0, min(1.0, ((x - x0) / max(1, x1 - x0) * 0.6 + (y - v["level"]) / max(1, y1 - v["level"]) * 0.6)))
            c = mix(light, liquid, min(1.0, t * 1.8)) if t < 0.5 else mix(liquid, dark, (t - 0.5) * 2)
            if edge:
                c = mix(c, rgb(GLASS[2]), 0.5)
        else:
            c = rgb(GLASS[2]) if edge else rgb(GLASS[1], 140)
        put(img, x, y, c)
    for x in range(16):
        if (x, v["level"]) in inside and (x - 1, v["level"]) in inside and (x + 1, v["level"]) in inside:
            put(img, x, v["level"], mix(light, rgb(0xFFFFFF), 0.45))
    nx0, nx1, ny0, ny1 = v["neck"]
    for y in range(ny0, ny1 + 1):
        for x in range(nx0, nx1 + 1):
            if (x, y) not in inside:
                put(img, x, y, rgb(GLASS[2]) if x in (nx0, nx1) and nx1 > nx0 else rgb(GLASS[0], 170))
    top = ny0 - 1
    # the stopper: cork, then each vessel's own mark
    for x in range(nx0, nx1 + 1):
        put(img, x, top, rgb(CORK[0] if x <= (nx0 + nx1) // 2 else CORK[1]))
    if v.get("twine"):
        for x in range(16):
            if (x, v["twine"]) in inside:
                put(img, x, v["twine"], rgb(0xC8A060) if x % 2 else rgb(0x8A6A34))
        put(img, 7, top - 1, liquid)
        put(img, 8, top - 1, dark)
    if v.get("cage"):
        for y in (5, 9, 13):
            for x in range(4, 12):
                put(img, x, y, rgb(BRASS[0] if x < 8 else BRASS[1]))
        for y in range(4, 15):
            put(img, 4, y, rgb(BRASS[2]))
            put(img, 11, y, rgb(BRASS[2]))
        for x in range(5, 11):
            put(img, x, 15, rgb(BRASS[1]))
    if v.get("leaf"):
        for (x, y) in ((9, 0), (10, 0), (9, 1), (10, 1), (11, 0)):
            put(img, x, y, rgb(0x5AA040) if y else rgb(0x8AD060))
    if v.get("bubbles"):
        for (x, y) in ((6, 12), (8, 10), (7, 8), (9, 13)):
            put(img, x, y, mix(light, rgb(0xFFFFFF), 0.7))
        for x in range(nx0, nx1 + 1):
            put(img, x, top, rgb(0x3A6A5A) if x % 2 else rgb(0x2A4A40))
    if v.get("spikes"):
        for (x, y) in ((4, 7), (11, 8), (5, 13), (10, 12)):
            put(img, x, y, rgb(0xFFFFFF))
        for x in range(nx0, nx1 + 1):
            put(img, x, top, rgb(0xE6F8FF))
    if v.get("cap"):
        for y, half in ((1, 2), (2, 4), (3, 5)):
            for x in range(int(7.5 - half), int(7.5 + half) + 1):
                put(img, x, y, mix(light, liquid, 0.3) if (x + y) % 4 else rgb(0xF4E0FF))
        for x in range(2, 14):
            put(img, x, 4 if x in (2, 13) else 3, mix(dark, (0, 0, 0), 0.2)) if x in (2, 13) else None
    if v.get("lid"):
        for x in range(4, 12):
            put(img, x, 3, rgb(0xC09030) if x < 8 else rgb(0x7A5414))
        put(img, 7, 2, rgb(0xF0D070))
        put(img, 8, 2, rgb(0xC09030))
    if v.get("star"):
        for (x, y) in ((7, 0), (8, 0), (6, 1), (9, 1), (7, 1), (8, 1)):
            put(img, x, y, rgb(0xFFF4A0) if y == 0 else rgb(0xF0C030))
        for (x, y) in ((3, 4), (12, 7), (11, 3)):
            put(img, x, y, light)
    if v.get("collar"):
        for x in range(nx0 - 1, nx1 + 2):
            put(img, x, ny1, rgb(BRASS[0] if x < 8 else BRASS[1]))
        for x in range(nx0, nx1 + 1):
            put(img, x, 0, liquid if x < nx1 else dark)
    # a gleam on the glass
    gx = x0 + 1
    gy = next((y for y in range(16) if (gx, y) in inside and (gx, y + 1) in inside), y0)
    put(img, gx, gy + 1, (255, 255, 255, 220))
    put(img, gx, gy + 2, (255, 255, 255, 150))
    # an outline in the chapter's dark, as Minecraft's items read
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), mix(dark, (0, 0, 0), 0.5)[:3] + (255,))
    return out


def blob(img, cx, cy, r, light, mid, dark):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                t = ((x - cx + r) + (y - cy + r)) / (4 * r)
                put(img, x, y, mix(light, mid, min(1, t * 1.8)) if t < 0.5 else mix(mid, dark, (t - 0.5) * 2))


def reagent(tier):
    mid, light, dark = (rgb(c) for c in COLOURS[tier])
    img = new()
    if tier == "root":
        # a knot of twisted root, a green shoot from it
        bark = (rgb(0x8A6034), rgb(0x5A3A1A), rgb(0x3A2410))
        for i, (x, y) in enumerate([(3, 12), (4, 11), (5, 11), (6, 10), (7, 10), (8, 9), (9, 9), (10, 10), (11, 11), (11, 12), (10, 13),
                                    (9, 13), (8, 12), (7, 12), (6, 13), (5, 14), (6, 8), (7, 7), (8, 6), (9, 7), (12, 12), (13, 13)]):
            put(img, x, y, bark[i % 3])
            put(img, x, y + 1, bark[2])
        for (x, y) in ((8, 5), (9, 4), (10, 3), (11, 3), (9, 3), (7, 4)):
            put(img, x, y, light if x > 8 else mid)
    elif tier == "archive":
        # a drop of ink, a quill's gleam on it
        for y in range(2, 15):
            w = max(0.0, (y - 2) / 6) if y < 8 else (1 - ((y - 10) / 5) ** 2) ** 0.5 if y >= 10 else 1.0
            r = 4.6 * min(1.0, w)
            for x in range(16):
                if abs(x - 7.5) <= r:
                    t = (x - 7.5 + r) / (2 * r + 0.01)
                    put(img, x, y, mix(mid, dark, t) if y > 5 else mid)
        put(img, 6, 9, light)
        put(img, 6, 10, light)
        put(img, 7, 8, (255, 255, 255, 200))
    elif tier == "clockwork":
        # a coiled brass spring
        for turn in range(5):
            y = 3 + turn * 2
            for x in range(4, 12):
                put(img, x, y, light if x < 7 else mid)
                put(img, x, y + 1, dark if x > 9 else mid)
            put(img, 3, y + 1, dark)
            put(img, 12, y, dark)
        for y in (2, 13):
            for x in range(6, 10):
                put(img, x, y, rgb(BRASS[1]))
    elif tier == "garden":
        # a drop of nectar cupped in a petal
        petal = (rgb(0xFFD6E6), rgb(0xF29AC0), rgb(0xB8507C))
        for y in range(8, 15):
            for x in range(2, 14):
                if abs(x - 7.5) <= (y - 7) * 0.9 and y < 14 or (y == 14 and 5 <= x <= 10):
                    put(img, x, y, petal[0] if x < 6 else petal[1] if x < 10 else petal[2])
        blob(img, 7.5, 7.0, 2.8, rgb(0xFFF4B0), rgb(0xF0C030), rgb(0xA07010))
        put(img, 6, 6, (255, 255, 255, 230))
    elif tier == "deep":
        blob(img, 7.5, 8.5, 5.0, light, mid, dark)
        put(img, 5, 6, (255, 255, 255, 240))
        put(img, 6, 5, (255, 255, 255, 180))
        put(img, 5, 7, (255, 255, 255, 150))
    elif tier == "ice":
        # a shard of rime, long and clear
        for y in range(1, 15):
            half = 3 - abs(y - 8) * 0.4
            for x in range(16):
                if abs(x - 7.5 - (8 - y) * 0.2) <= max(0.6, half):
                    side = x - 7.5 - (8 - y) * 0.2
                    put(img, x, y, light if side < -0.5 else mid if side < 1 else dark)
        put(img, 6, 6, (255, 255, 255))
        put(img, 7, 4, (255, 255, 255))
    elif tier == "spore":
        blob(img, 7.5, 8.5, 4.6, light, mid, dark)
        for (x, y) in ((5, 7), (9, 6), (8, 10), (6, 11), (10, 9)):
            put(img, x, y, rgb(0xF4E0FF))
        for (x, y) in ((2, 3), (13, 4), (12, 13), (3, 13)):
            put(img, x, y, light)
    elif tier == "hive":
        # a lump of royal jelly in a wax cell
        wax = (rgb(0xF0D070), rgb(0xC09030), rgb(0x7A5414))
        for y in range(4, 15):
            for x in range(2, 14):
                if abs(x - 7.5) + abs(y - 9.5) * 0.6 <= 6.2:
                    put(img, x, y, wax[0] if x < 6 else wax[1] if x < 11 else wax[2])
        blob(img, 7.5, 8.5, 3.0, rgb(0xFFF8E0), rgb(0xF4E4B0), rgb(0xC8B070))
    elif tier == "star":
        # a heap of star dust with sparks over it
        for y in range(10, 15):
            for x in range(16):
                if abs(x - 7.5) <= (y - 9) * 1.4:
                    put(img, x, y, light if (x + y) % 3 == 0 else mid if x < 9 else dark)
        for (x, y) in ((7, 3), (7, 4), (7, 5), (6, 4), (8, 4), (3, 7), (12, 6), (11, 2), (4, 2)):
            put(img, x, y, (255, 255, 255) if (x, y) in ((7, 4), (3, 7), (12, 6)) else light)
    else:
        # an ember of the Core, warm in its middle
        blob(img, 7.5, 8.5, 5.0, rgb(0xFFFFFF), rgb(0xF0D8A0), rgb(0xB06A30))
        blob(img, 7.5, 8.5, 2.2, rgb(0xFFFFFF), rgb(0xFFF4D0), rgb(0xF0C070))
        for (x, y) in ((7, 1), (8, 2), (12, 3), (3, 4)):
            put(img, x, y, rgb(0xF4A040))
    # an outline in the chapter's dark (Minecraft items read by their edge)
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), mix(dark, (0, 0, 0), 0.4)[:3] + (255,))
    return out


def ward(tier):
    mid, light, dark = (rgb(c) for c in COLOURS[tier])
    img = new(18)
    # a shield: flat top, round bottom
    for y in range(1, 17):
        for x in range(1, 17):
            if y < 9:
                inside = 2 <= x <= 15
            else:
                inside = ((x - 8.5) ** 2) / 49 + ((y - 9) ** 2) / 64 <= 1
            if inside:
                edge = not (3 <= x <= 14) or y < 3 or (y >= 9 and ((x - 8.5) ** 2) / 30 + ((y - 9) ** 2) / 42 > 1)
                put(img, x, y, rgb(BRASS[1]) if edge else (light if x < 8 else mid if y < 10 else dark))
    # the chapter's draught on it, small: a flask's outline
    for (x, y) in ((8, 5), (9, 5), (8, 6), (9, 6), (7, 7), (10, 7), (6, 8), (11, 8), (6, 9), (11, 9), (7, 10), (10, 10), (8, 11), (9, 11)):
        put(img, x, y, (255, 255, 255, 235))
    for (x, y) in ((7, 9), (8, 9), (9, 9), (10, 9), (8, 10), (9, 10)):
        put(img, x, y, mix(light, (255, 255, 255, 255), 0.5))
    return img


def main():
    (ASSETS / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "mob_effect").mkdir(parents=True, exist_ok=True)
    for tier in TIERS:
        draught(tier).save(ASSETS / "item" / f"spire_draught_{tier}.png")
        reagent(tier).save(ASSETS / "item" / f"spire_reagent_{tier}.png")
        ward(tier).save(ASSETS / "mob_effect" / f"spire_ward_{tier}.png")
    print("draughts, reagents, wards:", len(TIERS))


if __name__ == "__main__":
    main()

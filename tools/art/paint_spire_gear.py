#!/usr/bin/env python3
"""Spire gear textures (docs/tower/DESIGN.md §7), drawn here, no generation.

One hand-drawn 16x16 template a kind of piece (sword, axe, helmet, chestplate, leggings, boots) in shade letters, a bow
and a chapter seal drawn by code, each recoloured with the ten tiers' palettes; the outline is added round whatever is
filled. Armour layers (64x32, the vanilla humanoid layout) are painted face by face with the same palettes.

Every piece also has three honed looks (SpireGear.stage: +5, +15, +30), files <name>_s1.._s3 (armour
spire_<tier>_s<k>_layer_N): stage 1 gilds its edges, stage 2 lights the Core's rune and gem in it, stage 3 gives it a
its gilded edges burning with the tier's light (items) or a thread of light just inside its plates' rims (armour). Armour is plate,
smooth: highlight, shade, lames, a ridge, rivets — no grain (it read as mail).

    python3 tools/art/paint_spire_gear.py
"""
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life/textures"

# dark, mid, light, accent
TIERS = {
    "root": (0x3B2A1A, 0x6E5034, 0xA2825A, 0x6AA84F),
    "archive": (0x3A1F1A, 0x7A3B2E, 0xD8C39A, 0xC9A227),
    "clockwork": (0x5A2E17, 0xB4683B, 0xE7A26C, 0x5FB3A1),
    "garden": (0x5A2A3F, 0xC9789A, 0xF6C8D9, 0x5E9E3A),
    "deep": (0x173A40, 0x3F8F8A, 0x8FD6C4, 0x2F5F9E),
    "ice": (0x2E5A86, 0x8CC4EC, 0xE4F4FF, 0x4FA3E0),
    "spore": (0x4A1E1E, 0xB0362E, 0xE8DCC8, 0x9CCC65),
    "hive": (0x5A3A10, 0xD99A1E, 0xFFD25A, 0x3A2A10),
    "star": (0x1F1A2E, 0x5B3F8F, 0xB08FE0, 0xFFE57A),
    "summit": (0x8A7A5A, 0xE2DACA, 0xFFFFFF, 0xF2C94C),
}
NUMERALS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
WOOD = (0x5B3B22, 0x8A5E38)

# the templates: L light, M mid, D dark, A accent, G gem (light accent), W wood, w light wood; the outline is added
SWORD = [
    "................",
    ".............LM.",
    "............LMD.",
    "...........LMD..",
    "..........LMD...",
    ".........LMD....",
    "........LMD.....",
    ".......LMD......",
    "...A..LMD.......",
    "....AAMD........",
    ".....AA.........",
    "....wAAA........",
    "...wW...A.......",
    "..wW............",
    ".G..............",
    "................",
]
AXE = [
    "........LL......",
    ".......LLMD.....",
    "......LLMMDDAwW.",
    ".....LLMMMDAwW..",
    "....LLMMMDAwW...",
    "...LLMMDD.wW....",
    "....LDD..wW.....",
    ".....D..wW......",
    ".......wW.......",
    "......wW........",
    ".....wW.........",
    "....wW..........",
    "...wW...........",
    "..wW............",
    ".G..............",
    "................",
]
HELMET = [
    "................",
    "................",
    "................",
    "....LLLLLLLL....",
    "...LLMMMMMMMD...",
    "...LMMAAAAMMD...",
    "...LMMMMMMMMD...",
    "...LMD....LMD...",
    "...LMD....LMD...",
    "...DDD....DDD...",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CHESTPLATE = [
    "................",
    "................",
    "..LLM......LMD..",
    ".LLMMD....LMMDD.",
    ".LMMMMLLLLMMMMD.",
    ".LMMMMMAAMMMMMD.",
    "..DDLMMMMMMMDD..",
    "....LMMAAMMD....",
    "....LMMMMMMD....",
    "....LMMAAMMD....",
    "....LMMMMMMD....",
    "....LMMMMMMD....",
    "....DDDDDDDD....",
    "................",
    "................",
    "................",
]
LEGGINGS = [
    "................",
    "................",
    "....LLLLLLLD....",
    "....AAAGAAAA....",
    "....LMMMMMMD....",
    "....LMMDLMMD....",
    "....LMD..LMD....",
    "....LMD..LMD....",
    "....LMD..LMD....",
    "....LMD..LMD....",
    "....LMD..LMD....",
    "....LMD..LMD....",
    "....DDD..DDD....",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...LMD...LMD....",
    "...LMD...LMD....",
    "...LMD...LMD....",
    "...AAA...AAA....",
    "..LMMD..LMMD....",
    ".LMMMD.LMMMD....",
    ".DDDDD.DDDDD....",
    "................",
    "................",
    "................",
]
TEMPLATES = {"sword": SWORD, "axe": AXE, "helmet": HELMET, "chestplate": CHESTPLATE, "leggings": LEGGINGS, "boots": BOOTS}

# What each hone stage adds to a piece's silhouette (x, y, shade), stage by stage, the later on top: an upgrade has
# to read in the outline alone, not only in its colours (pixel-art practice; Stick to Slick evolves its weapon's
# model at milestones). The sword is drawn by sword() instead.
GROWTH = {
    "axe": [
        [(2, 5, "L"), (3, 6, "L"), (4, 7, "D"), (7, 0, "L"), (6, 1, "L")],
        [(13, 0, "A"), (14, 0, "G"), (13, 1, "A"), (12, 1, "D"), (12, 6, "A"), (13, 7, "A")],
        [(1, 4, "L"), (1, 5, "M"), (2, 6, "D"), (8, 0, "G"), (5, 1, "L")],
    ],
    "helmet": [
        [(7, 1, "A"), (8, 1, "A"), (6, 2, "L"), (7, 2, "L"), (8, 2, "L"), (9, 2, "M")],
        [(2, 3, "L"), (1, 2, "L"), (2, 4, "M"), (13, 3, "L"), (14, 2, "L"), (13, 4, "M")],
        [(7, 0, "G"), (8, 0, "G"), (0, 1, "L"), (15, 1, "L")],
    ],
    "chestplate": [
        [(0, 3, "L"), (1, 2, "L"), (2, 1, "L"), (13, 1, "L"), (14, 2, "M"), (15, 3, "D")],
        [(6, 3, "A"), (9, 3, "A"), (6, 2, "A"), (9, 2, "A")],
        [(0, 4, "M"), (15, 4, "D"), (7, 4, "G"), (8, 4, "G")],
    ],
    "leggings": [
        [(3, 3, "L"), (3, 4, "M"), (12, 3, "D"), (12, 4, "D")],
        [(4, 8, "A"), (6, 8, "A"), (9, 8, "A"), (11, 8, "A")],
        [(3, 2, "G"), (12, 2, "G"), (7, 3, "G"), (8, 3, "G")],
    ],
    "boots": [
        [(2, 6, "L"), (6, 6, "D"), (8, 6, "L"), (12, 6, "D")],
        [(0, 10, "A"), (0, 11, "A"), (7, 10, "A"), (7, 11, "A")],
        [(1, 8, "L"), (0, 9, "L"), (13, 8, "L"), (13, 9, "M")],
    ],
}


def grown(rows, piece, stage):
    """A template with what its hone stages add to its outline."""
    grid = [list(r) for r in rows]
    for added in GROWTH.get(piece, [])[:stage]:
        for x, y, shade in added:
            grid[y][x] = shade
    return ["".join(r) for r in grid]


def sword(stage):
    """A sword on the diagonal, its hilt low on the left: each stage a longer or broader blade with a fuller of the
    Core down it, a wider guard turning up at its tips, a heavier pommel; the last flares near the point."""
    grid = [["."] * 16 for _ in range(16)]

    def put(x, y, shade):
        if 0 <= x < 16 and 0 <= y < 16:
            grid[y][x] = shade

    length, width, guard, grip = ((8, 3, 1, 2), (9, 3, 2, 2), (8, 4, 3, 2), (8, 4, 3, 2))[stage]
    for i in range(length):
        y, x = 10 - i, 5 + i
        cols = ["L", "M", "D"] if width == 3 else ["L", "M", "A", "D"]
        if i == length - 1:
            cols = ["L", "D"]
        elif i == length - 2 and width == 4:
            cols = ["L", "M", "D"]
        for k, shade in enumerate(cols):
            put(x + k, y, shade)
        if stage == 3 and i in (length - 4, length - 3):
            put(x - 1, y, "L")
            put(x + width, y, "D")
    for k in range(-guard, guard + 1):
        put(4 + k, 11 + k, "A")
        put(5 + k, 11 + k, "A")
    if stage >= 2:
        put(4 - guard + 1, 11 - guard - 1, "A")
        put(5 + guard + 1, 11 + guard - 1, "A")
        put(5, 11, "G")
    for j in range(grip):
        put(3 - j, 12 + j, "w")
        put(4 - j, 12 + j, "W")
    if stage == 0:
        put(1, 14, "G")
    elif stage == 1:
        for x, y in ((0, 14), (1, 14), (0, 15), (1, 15)):
            put(x, y, "G")
    else:
        for x, y in ((0, 14), (2, 14), (1, 13), (1, 15)):
            put(x, y, "A")
        put(1, 14, "G")
    return ["".join(r) for r in grid]

# 3x5 pixel numerals for the seals
GLYPHS = {
    "I": ["#", "#", "#", "#", "#"],
    "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
}


def rgb(value, alpha=255):
    return (value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF, alpha)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def shades(tier):
    dark, mid, light, accent = (rgb(c) for c in TIERS[tier])
    return {
        "D": dark, "M": mid, "L": light, "A": accent, "G": mix(accent, (255, 255, 255), 0.45),
        "W": rgb(WOOD[0]), "w": rgb(WOOD[1]), "O": mix(dark, (0, 0, 0), 0.55),
    }


def outline(img, color):
    """Every clear pixel next to a filled one (sideways) becomes the outline."""
    w, h = img.size
    px = img.load()
    edge = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] == 0 and any(
        0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 0 and px[x + dx, y + dy] != color
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in edge:
        px[x, y] = color


def from_template(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    outline(img, palette["O"])
    return img


def line(points_from, points_to):
    (x0, y0), (x1, y1) = points_from, points_to
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps)) for i in range(steps + 1)]


def bow(palette, pull, stage=0):
    """The limb bent from top right to bottom left round the top-left corner (as the vanilla bow lies), the string
    across, pulled back {pull} pixels with an arrow on it when drawn."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    a, c, b = (13, 1), (1, 1), (1, 13)
    limb = []
    for i in range(41):
        t = i / 40
        x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t * t * b[0]
        y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t * t * b[1]
        limb.append((round(x + 0.6), round(y + 0.6)))
    for x, y in limb:
        img.putpixel((x, y), palette["M"])
        if x + 1 < 16 and y + 1 < 16 and img.getpixel((x + 1, y + 1))[3] == 0:
            img.putpixel((x + 1, y + 1), palette["D"])
    for x, y in limb[:6] + limb[-6:]:
        img.putpixel((x, y), palette["L"])
    for x, y in ((4, 4), (5, 4), (4, 5), (3, 5), (5, 3)):
        img.putpixel((x, y), palette["A"])
    img.putpixel((4, 4), palette["G"])
    # stage by stage: the tips shod in metal, a heavier grip with the Core in it, feathers of light off the tips
    if stage >= 1:
        for x, y in limb[:3] + limb[-3:]:
            img.putpixel((x, y), palette["A"])
    if stage >= 2:
        for x, y in ((3, 4), (4, 3), (5, 5), (6, 4), (4, 6)):
            img.putpixel((x, y), palette["A"])
        img.putpixel((4, 4), (255, 255, 255, 255))
    if stage >= 3:
        for x, y in ((15, 0), (15, 2), (12, 0), (0, 15), (2, 15), (0, 12)):
            img.putpixel((x, y), palette["L"])
    tip1, tip2 = (14, 1), (1, 14)
    mid = (7 + pull, 7 + pull)
    string = (235, 230, 215, 255)
    for p in line(tip1, mid) + line(mid, tip2):
        if img.getpixel(p)[3] == 0 or pull:
            img.putpixel(p, string)
    if pull:
        shaft, head, feather = (150, 110, 70, 255), (200, 200, 205, 255), (240, 240, 240, 255)
        nock = mid
        point = (max(1, mid[0] - 9), max(1, mid[1] - 9))
        for p in line(nock, point):
            img.putpixel(p, shaft)
        img.putpixel(point, head)
        img.putpixel((point[0] + 1, point[1]), head)
        img.putpixel((point[0], point[1] + 1), head)
        for p in ((nock[0] - 1, nock[1]), (nock[0], nock[1] - 1)):
            if 0 <= p[0] < 16 and 0 <= p[1] < 16:
                img.putpixel(p, feather)
    outline(img, palette["O"])
    return img


def seal(palette, numeral):
    """A wax seal: a disc in the tier's colours with its numeral pressed in, two ribbon tails under it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, r = 7.5, 7.0, 5.6
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                shade = "L" if d < r - 1.2 and (x - cx) + (y - cy) < -3.5 else "D" if d > r - 1.0 else "M"
                img.putpixel((x, y), palette[shade])
    for x, y in ((5, 12), (5, 13), (4, 14), (10, 12), (10, 13), (11, 14)):
        img.putpixel((x, y), palette["A"])
    glyphs = [GLYPHS[ch] for ch in numeral]
    width = sum(len(g[0]) for g in glyphs) + len(glyphs) - 1
    x0 = round(cx - width / 2 + 0.5)
    for g in glyphs:
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch == "#":
                    img.putpixel((x0 + gx, 5 + gy), palette["G"])
        x0 += len(g[0]) + 1
    outline(img, palette["O"])
    return img


# ---- honed looks

GOLD = ((150, 104, 40, 255), (214, 166, 72, 255), (250, 218, 130, 255))


# where each kind of piece sets the Core's gem at stage 2: the guard of a blade, the cheek of an axe, a helm's brow…
GEMS = {"sword": (5, 11), "axe": (8, 3), "helmet": (7, 5), "chestplate": (7, 6), "leggings": (7, 4), "boots": (4, 10), "bow": (4, 4)}


def staged(img, palette, stage, kind=None):
    """An item's look at a hone stage, built over its plain one."""
    if stage == 0:
        return img
    out = img.copy()
    px = out.load()
    w, h = out.size
    light, accent, gem, rim = palette["L"], palette["A"], palette["G"], palette["O"]
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                continue
            # 1: the light edges gilded
            if c == light:
                px[x, y] = GOLD[2] if (x + y) % 3 else GOLD[1]
            # 2: the Core's light in the accents and gems
            elif stage >= 2 and c == accent:
                px[x, y] = gem
            elif stage >= 2 and c == gem:
                px[x, y] = (255, 255, 255, 255)
    if stage >= 2 and kind in GEMS:
        gx, gy = GEMS[kind]
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            if px[gx + dx, gy + dy][3]:
                px[gx + dx, gy + dy] = gem
        px[gx, gy] = (255, 255, 255, 255)
    if stage >= 3:
        # the metal itself burns with the tier's light along its gilded edges; the outline stays dark
        glow = mix(gem, (255, 255, 255), 0.45)
        for y in range(h):
            for x in range(w):
                if px[x, y] == GOLD[2]:
                    px[x, y] = glow
    return out


# ---- armour layers (64x32): a box at (u, v) of w x h x d unfolds as vanilla's model parts do


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def plate(img, rect, palette, stage, rows=None, lames=0, ridge=False, band=None, emblem=False, rivets=True):
    """One face of a plate of armour, smooth: a highlight under its top edge and down its left, shade on the right,
    lames (overlapping strips) every {lames} rows, a ridge down the middle, a coloured band. By hone stage: 1 gilds
    the rim, 2 lights the band and the emblem with the Core, 3 runs a thread of light just inside the rim."""
    x0, y0, w, h = rect
    px = img.load()
    first, last = rows if rows else (0, h - 1)
    dark, mid, light = palette["D"], palette["M"], palette["L"]
    accent, gem = palette["A"], palette["G"]
    for y in range(first, last + 1):
        ly = y - first
        for x in range(w):
            rim = x in (0, w - 1) or y in (first, last)
            # gilding on the top and bottom edges only: the sides of a narrow face are most of it
            gilt = stage >= 1 and y in (first, last)
            thread = stage >= 3 and w >= 6 and last - first >= 6 and y == first + 1 and 0 < x < w - 1
            if rim:
                c = (GOLD[1] if x % 3 else GOLD[2]) if gilt else dark
            elif lames and ly % lames == 0:
                c = mix(mid, dark, 0.55)
            elif ly == 1 or (lames and ly % lames == 1):
                c = light
            elif x == 1:
                c = mix(mid, light, 0.45)
            elif x == w - 2:
                c = mix(mid, dark, 0.35)
            else:
                c = mid
            if ridge and not rim and x in (w // 2 - 1, w // 2):
                c = (gem if x == w // 2 - 1 else mix(gem, mid, 0.4)) if stage >= 3 else light if x == w // 2 - 1 else mix(mid, light, 0.3)
            if band is not None and y == band and not rim:
                c = gem if stage >= 2 else accent
            if thread:
                c = mix(gem, (255, 255, 255), 0.25)
            px[x0 + x, y0 + y] = c
    if rivets and w >= 5 and last - first >= 4:
        stud = GOLD[2] if stage >= 1 else light
        for x, y in ((2, first + 2), (w - 3, first + 2), (2, last - 2), (w - 3, last - 2)):
            px[x0 + x, y0 + y] = stud
    if emblem and w >= 6 and last - first >= 7:
        # the Builders' sign: a ring over a tower on its step
        cx, top = x0 + w // 2, y0 + first + 2
        glow = (255, 255, 255, 255) if stage >= 2 else gem
        ring = gem if stage >= 2 else accent
        for dx, dy in ((-1, 0), (0, 0), (-2, 1), (1, 1), (-1, 2), (0, 2)):
            px[cx + dx, top + dy] = ring
        for dy in range(3, 6):
            px[cx - 1, top + dy] = glow if dy == 3 else ring
            px[cx, top + dy] = ring
        px[cx - 2, top + 5] = ring
        px[cx + 1, top + 5] = ring


def armour(palette, stage):
    one = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    two = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    head = faces(0, 0, 8, 8, 8)
    for name, rect in head.items():
        side = name not in ("top", "bottom")
        plate(one, rect, palette, stage, band=2 if side else None, ridge=name == "top", rivets=False)
    # the visor: the face shows through a slit under the brow band
    fx, fy, _, _ = head["front"]
    for y in range(3, 6):
        for x in range(2, 6):
            one.putpixel((fx + x, fy + y), (0, 0, 0, 0))
    for name, rect in faces(16, 16, 8, 12, 4).items():
        front = name in ("front", "back")
        plate(one, rect, palette, stage, lames=4 if name != "front" else 0, ridge=name == "front", emblem=name == "front",
              band=10 if name in ("front", "back", "left", "right") else None)
    for name, rect in faces(40, 16, 4, 12, 4).items():
        if name in ("top", "bottom"):
            plate(one, rect, palette, stage, rivets=False)
        else:
            plate(one, rect, palette, stage, rows=(0, 6), lames=3, band=5, rivets=False)
    for name, rect in faces(0, 16, 4, 12, 4).items():
        if name == "bottom":
            plate(one, rect, palette, stage, rivets=False)
        elif name != "top":
            plate(one, rect, palette, stage, rows=(7, 11), band=8, rivets=False)
    for name, rect in faces(16, 16, 8, 12, 4).items():
        if name not in ("top", "bottom"):
            plate(two, rect, palette, stage, rows=(7, 11), band=8, rivets=False)
    for name, rect in faces(0, 16, 4, 12, 4).items():
        if name == "top":
            plate(two, rect, palette, stage, rivets=False)
        elif name != "bottom":
            plate(two, rect, palette, stage, rows=(0, 8), lames=3, rivets=False)
    return one, two


# the plates of the armour in the round (client/spire/SpireArmorLayer, 1.21.1): u, v, w, h, d of each box, laid
# out below the vanilla layer on a 64x64 sheet
EXTRAS = {
    "crest": (0, 32, 1, 3, 9), "wing": (20, 32, 1, 4, 3), "spike": (28, 32, 1, 2, 1), "tasset": (32, 32, 4, 3, 1),
    "knee": (42, 32, 4, 3, 1), "toe": (52, 32, 5, 2, 1), "pauldron": (0, 44, 5, 3, 6), "plate": (22, 44, 4, 2, 6),
    "chest_plate": (42, 44, 6, 5, 1), "collar": (0, 53, 9, 2, 7), "cuff": (32, 53, 6, 1, 6),
}


def in_the_round(layer, palette, stage):
    """A 64x64 sheet: the layer above, the plates below, painted as the layer is."""
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    sheet.paste(layer, (0, 0))
    for name, (u, v, w, h, d) in EXTRAS.items():
        for face, rect in faces(u, v, w, h, d).items():
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            side = face not in ("top", "bottom")
            plate(sheet, rect, palette, stage, lames=2 if name == "pauldron" and side else 0,
                  band=1 if name in ("crest", "collar") and side and rect[3] > 2 else None,
                  ridge=name == "crest" and face == "top", emblem=name == "chest_plate" and face == "front", rivets=name in ("pauldron", "chest_plate"))
    return sheet


# a hero's armour over a villager (client/spire/HeroLook): u, v, w, h, d of each box, sized to a villager's head
# (8x10x8), robe and legs, on a 64x64 sheet a tier and stage
HERO = {
    "helmet": (0, 0, 9, 6, 9), "crest": (36, 0, 1, 3, 9), "wing": (56, 0, 1, 4, 3), "cuirass": (0, 16, 9, 7, 7),
    "pauldron": (32, 16, 3, 3, 7), "tasset": (0, 32, 10, 3, 8), "greave": (36, 32, 5, 6, 5),
}


def hero(palette, stage):
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for name, (u, v, w, h, d) in HERO.items():
        for face, rect in faces(u, v, w, h, d).items():
            side = face not in ("top", "bottom")
            plate(sheet, rect, palette, stage, lames=2 if name in ("pauldron", "tasset") and side else 0,
                  band=2 if name == "helmet" and side else 1 if name == "greave" and side else None,
                  ridge=(name == "crest" and face == "top") or (name == "cuirass" and face == "front"),
                  emblem=name == "cuirass" and face == "front", rivets=name in ("cuirass", "helmet"))
    # the visor's slit, dark, under the brow band
    fx, fy, _, _ = faces(*HERO["helmet"])["front"]
    for x in range(2, 7):
        sheet.putpixel((fx + x, fy + 4), palette["O"])
    return sheet


def coat(palette, stage):
    """A hero's surcoat over a villager's own UV (as the outfits are): the robe a tabard of the tier with the
    Builders' sign and a hemmed band, plate vambraces on the crossed arms, the body under it plated, greaves on the
    legs. With it on, a hero reads as one knight, not plates over a patchwork of clothes."""
    sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for face, rect in faces(16, 20, 8, 12, 6).items():
        plate(sheet, rect, palette, stage, lames=3 if face not in ("top", "bottom") else 0, rivets=False)
    for face, rect in faces(0, 38, 8, 20, 6).items():
        if face in ("top",):
            continue
        front = face in ("front", "back")
        plate(sheet, rect, palette, stage, ridge=front, emblem=face == "front", band=rect[3] - 3 if face != "bottom" else None,
              rivets=False)
    for box in ((44, 22, 4, 8, 4), (40, 38, 8, 4, 4)):
        for face, rect in faces(*box).items():
            plate(sheet, rect, palette, stage, lames=2 if face not in ("top", "bottom") else 0, rivets=False)
    for face, rect in faces(0, 22, 4, 12, 4).items():
        plate(sheet, rect, palette, stage, lames=4 if face not in ("top", "bottom") else 0, rivets=False)
    return sheet


def main():
    items = ASSETS / "item"
    layers = ASSETS / "models/armor"
    items.mkdir(parents=True, exist_ok=True)
    layers.mkdir(parents=True, exist_ok=True)
    count = 0
    for i, tier in enumerate(TIERS):
        palette = shades(tier)
        for stage in range(4):
            suffix = f"_s{stage}" if stage else ""
            for piece, rows in TEMPLATES.items():
                shape = sword(stage) if piece == "sword" else grown(rows, piece, stage)
                staged(from_template(shape, palette), palette, stage, piece).save(items / f"spire_{tier}_{piece}{suffix}.png")
                count += 1
            staged(bow(palette, 0, stage), palette, stage, "bow").save(items / f"spire_{tier}_bow{suffix}.png")
            for frame, pull in enumerate((1, 2, 3)):
                staged(bow(palette, pull, stage), palette, stage, "bow").save(items / f"spire_{tier}_bow{suffix}_pulling_{frame}.png")
            one, two = armour(palette, stage)
            one.save(layers / f"spire_{tier}{suffix}_layer_1.png")
            two.save(layers / f"spire_{tier}{suffix}_layer_2.png")
            in_the_round(one, palette, stage).save(layers / f"spire_{tier}{suffix}_3d_layer_1.png")
            in_the_round(two, palette, stage).save(layers / f"spire_{tier}{suffix}_3d_layer_2.png")
            heroes = ASSETS / "entity/hero"
            heroes.mkdir(parents=True, exist_ok=True)
            hero(palette, stage).save(heroes / f"spire_{tier}{suffix}.png")
            coat(palette, stage).save(heroes / f"spire_{tier}{suffix}_coat.png")
            count += 4
        seal(palette, NUMERALS[i]).save(items / f"spire_seal_{tier}.png")
        count += 1
    print(f"paint_spire_gear: {count} item textures, {8 * len(TIERS)} armour layers")


if __name__ == "__main__":
    main()

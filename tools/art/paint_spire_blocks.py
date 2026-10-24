"""What the Builders left inside the spire (textures/block/spire/*.png), drawn here, no generated images:

    sconce.png    16x16  brass (0..7, 0..7), the Core's crystal (8..15, 0..15)
    tapestry.png  16x32  deep blue cloth, a gold border, the Builders' sign (a tower under a ring)
    brazier.png   16x16  dark stone, worn
    coals.png     16x16  the Core's cold fire glowing in the bowl
    rune.png      16x16  a dark tile with its joint
    rune_glyph.png 16x16 the glyph alone (drawn bright over the tile)

The rift over a spire's cut (textures/block/spire/rift_*.png, 64x64, tiling, laid over four blocks by
client/spire/SpireRiftRenderer, the glowing ones drawn additive):
    rift_void.png    the deep of it: indigo dark, clouds of the Core's violet, faint dust
    rift_swirl.png   wisps of light drifting over it, black where none
    rift_motes.png   sparks, black where none
    rift_edge.png    the glow along a cut edge, bright at the bottom fading up
    rift_beam.png    16x64, the beam out of the rift's heart: streaks of light running up it (it tiles up and down)

The spire's own pieces:
    portal.png       16x256, sixteen frames (portal.png.mcmeta): a vortex of the Core's light turning, half see-through
    portal_dark.png  a closed portal: dark glass with the vortex's cracks faintly in it
    chest_side.png   the layer chest's side: dark slate in courses, a vein of the Core across it
    chest_lid.png    its lid: slate with a brass rim, the Builders' glyph in the middle
    crystal.png      a shard of the Core: facets of violet, light along their edges
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life", "textures", "block", "spire")

BRASS = (184, 142, 70)
BRASS_LIGHT = (232, 196, 120)
BRASS_DARK = (110, 78, 36)
VEIN = (178, 132, 236)
VEIN_LIGHT = (232, 210, 255)
VEIN_DARK = (104, 64, 160)


def jitter(rng, colour, amount):
    return tuple(max(0, min(255, int(c + (rng.random() - 0.5) * amount))) for c in colour) + (255,)


def sconce(rng):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            base = BRASS_LIGHT if x + y < 4 else BRASS_DARK if x + y > 10 else BRASS
            image.putpixel((x, y), jitter(rng, base, 18))
    for y in range(16):
        for x in range(8, 16):
            edge = x in (8, 15)
            base = VEIN_DARK if edge else VEIN_LIGHT if (x + y) % 5 == 0 else VEIN
            image.putpixel((x, y), jitter(rng, base, 14))
    return image


def tapestry(rng):
    image = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    cloth, dark, gold = (36, 52, 104), (24, 34, 72), (214, 172, 82)
    for y in range(32):
        for x in range(16):
            base = dark if (x + y * 3) % 7 == 0 else cloth
            image.putpixel((x, y), jitter(rng, base, 10))
    for y in range(32):
        for x in (2, 13):
            image.putpixel((x, y), gold + (255,))
    for x in range(2, 14):
        image.putpixel((x, 1), gold + (255,))
        image.putpixel((x, 28), gold + (255,))
    # the fringe: points at the bottom
    for x in range(16):
        for y in range(29, 32):
            if (x % 3 == 1 and y < 32) or y == 29:
                image.putpixel((x, y), gold + (255,))
            else:
                image.putpixel((x, y), (0, 0, 0, 0))
    # the sign: a ring over a narrowing tower on three steps
    ring = [(6, 5), (7, 4), (8, 4), (9, 5), (10, 6), (10, 7), (9, 8), (8, 9), (7, 9), (6, 8), (5, 7), (5, 6)]
    for x, y in ring:
        image.putpixel((x, y), (VEIN_LIGHT if y < 6 else VEIN) + (255,))
    for y in range(11, 25):
        half = 1 + (y - 11) // 5
        for x in range(8 - half, 8 + half):
            image.putpixel((x, y), (gold if x != 8 - half else (170, 128, 56)) + (255,))
    for y, half in ((25, 4), (26, 5)):
        for x in range(8 - half, 8 + half):
            image.putpixel((x, y), (170, 128, 56) + (255,))
    image.putpixel((7, 14), VEIN_LIGHT + (255,))
    image.putpixel((8, 19), VEIN_LIGHT + (255,))
    return image


def brazier(rng):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    stone = (54, 50, 58)
    for y in range(16):
        for x in range(16):
            base = (40, 37, 44) if y % 4 == 0 or (x + (y // 4) * 3) % 8 == 0 else stone
            image.putpixel((x, y), jitter(rng, base, 16))
    for x in range(16):
        image.putpixel((x, 0), BRASS_DARK + (255,))
    return image


def coals(rng):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            roll = rng.random()
            base = VEIN_LIGHT if roll < 0.18 else VEIN if roll < 0.6 else (90, 140, 230) if roll < 0.8 else VEIN_DARK
            image.putpixel((x, y), jitter(rng, base, 20))
    return image


def rune(rng):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tile = (58, 58, 66)
    for y in range(16):
        for x in range(16):
            joint = x == 0 or y == 0
            image.putpixel((x, y), jitter(rng, (36, 36, 42) if joint else tile, 12))
    return image


def glyph():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = [
        "................",
        "................",
        "......VVVV......",
        ".....V....V.....",
        "....V..LL..V....",
        "....V.L..L.V....",
        ".....V.LL.V.....",
        "......VVVV......",
        ".......VV.......",
        "......VLLV......",
        ".....VL..LV.....",
        "....VL....LV....",
        "...VVVVVVVVVV...",
        "................",
        "................",
        "................",
    ]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "V":
                image.putpixel((x, y), VEIN + (255,))
            elif c == "L":
                image.putpixel((x, y), VEIN_LIGHT + (255,))
    return image


def wrap_noise(rng, size, cells):
    """Smooth value noise that tiles: a grid of random values, cosine-blended, wrapping round both ways."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            gx, gy = x * cells / size, y * cells / size
            x0, y0 = int(gx), int(gy)
            fx, fy = gx - x0, gy - y0
            fx, fy = (1 - math.cos(fx * math.pi)) / 2, (1 - math.cos(fy * math.pi)) / 2
            a = grid[y0 % cells][x0 % cells]
            b = grid[y0 % cells][(x0 + 1) % cells]
            c = grid[(y0 + 1) % cells][x0 % cells]
            d = grid[(y0 + 1) % cells][(x0 + 1) % cells]
            out[y][x] = (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    return out


def fractal(rng, size, octaves):
    total = [[0.0] * size for _ in range(size)]
    weight = 0.0
    for i, cells in enumerate(octaves):
        layer = wrap_noise(rng, size, cells)
        w = 0.5 ** i
        weight += w
        for y in range(size):
            for x in range(size):
                total[y][x] += layer[y][x] * w
    return [[v / weight for v in row] for row in total]


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rift_void(rng):
    image = Image.new("RGBA", (64, 64))
    cloud = fractal(rng, 64, (4, 8, 16))
    deep, mid = (8, 4, 22), (78, 34, 136)
    for y in range(64):
        for x in range(64):
            t = (cloud[y][x] - 0.3) * 2.6
            colour = mix(deep, mid, t)
            if t > 0.75:
                colour = mix(colour, VEIN, (t - 0.75) * 2)
            image.putpixel((x, y), colour + (255,))
    for _ in range(40):
        x, y = rng.randrange(64), rng.randrange(64)
        image.putpixel((x, y), mix((120, 100, 170), (220, 210, 255), rng.random()) + (255,))
    return image


def rift_swirl(rng):
    image = Image.new("RGBA", (64, 64))
    flow = fractal(rng, 64, (2, 4, 8))
    for y in range(64):
        for x in range(64):
            # bands along the noise's contours: thin bright wisps
            band = abs(math.sin(flow[y][x] * math.pi * 9))
            t = max(0.0, (band - 0.9) / 0.1) * (0.4 + flow[y][x])
            image.putpixel((x, y), mix((0, 0, 0), mix(VEIN, VEIN_LIGHT, t), t) + (255,))
    return image


def rift_motes(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    for _ in range(26):
        x, y = rng.randrange(64), rng.randrange(64)
        bright = mix(VEIN, (255, 255, 255), rng.random())
        image.putpixel((x, y), bright + (255,))
        if rng.random() < 0.4:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                image.putpixel(((x + dx) % 64, (y + dy) % 64), mix((0, 0, 0), bright, 0.4) + (255,))
    return image


def rift_edge(rng):
    image = Image.new("RGBA", (64, 64))
    flicker = wrap_noise(rng, 64, 16)
    for y in range(64):
        for x in range(64):
            # bottom of the sprite is the cut: bright there, fading upward in tongues
            up = (63 - y) / 63.0
            reach = 0.35 + flicker[0][x] * 0.65
            t = max(0.0, 1.0 - up / reach) ** 1.6
            image.putpixel((x, y), mix((0, 0, 0), mix(VEIN, VEIN_LIGHT, t), t) + (255,))
    return image


def rift_beam(rng):
    image = Image.new("RGBA", (16, 64))
    streaks = wrap_noise(rng, 64, 8)
    for y in range(64):
        for x in range(16):
            # bright down the middle, a few streaks broken along their length
            across = 1.0 - abs(x - 7.5) / 8.0
            streak = streaks[y][(x * 4) % 64]
            t = across ** 1.5 * (0.45 + 0.55 * max(0.0, streak - 0.3) / 0.7)
            image.putpixel((x, y), mix((0, 0, 0), mix(VEIN, (255, 255, 255), t * 0.8), t) + (255,))
    return image


def portal(rng):
    frames = 16
    image = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        turn = f / frames * math.pi * 2
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                d = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                # two arms of a spiral, turning; brighter toward the eye
                arm = (math.sin(a * 2 + d * 0.9 - turn) + 1) / 2
                t = arm * 0.7 + max(0.0, 1 - d / 9) * 0.5
                colour = mix(VEIN_DARK, VEIN_LIGHT, t)
                alpha = int(150 + 90 * min(1.0, t))
                image.putpixel((x, f * 16 + y), colour + (alpha,))
    return image


def portal_dark(rng):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            crack = abs(math.sin(math.atan2(y - 7.5, x - 7.5) * 2 + d * 0.9)) < 0.12
            colour = mix((22, 16, 34), VEIN_DARK, 0.6) if crack else jitter(rng, (24, 20, 32), 8)[:3]
            image.putpixel((x, y), colour + (200,))
    return image


SLATE = (52, 56, 66)
SLATE_DARK = (34, 37, 45)


def chest_side(rng):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            seam = y in (5, 11) or (x == (4 if y < 5 else 10 if y < 11 else 6))
            base = SLATE_DARK if seam else SLATE
            image.putpixel((x, y), jitter(rng, base, 10))
    # the vein of the Core running across, a little wavy
    for x in range(1, 15):
        y = 8 + int(round(math.sin(x / 2.2)))
        image.putpixel((x, y), VEIN + (255,))
        if x % 4 == 0:
            image.putpixel((x, y - 1), VEIN_LIGHT + (255,))
    return image


def chest_lid(rng):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            base = BRASS_DARK if edge else BRASS if x in (1, 14) or y in (1, 14) else SLATE
            image.putpixel((x, y), jitter(rng, base, 10))
    glyph = ["..VVVV..", ".V.LL.V.", "V.L..L.V", "V.L..L.V", ".V.LL.V.", "..VVVV..", "...VV...", "..VVVV.."]
    for y, row in enumerate(glyph):
        for x, c in enumerate(row):
            if c != ".":
                image.putpixel((4 + x, 4 + y), (VEIN_LIGHT if c == "L" else VEIN) + (255,))
    return image


def crystal(rng):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            facet = ((x + y) // 4 + (x - y + 16) // 5) % 3
            base = (VEIN_DARK, VEIN, VEIN_LIGHT)[facet]
            edge = (x + y) % 4 == 0
            image.putpixel((x, y), jitter(rng, mix(base, (255, 255, 255), 0.35) if edge else base, 10))
    return image


def cracked(rng):
    """A worn wall laid in quickly: brick courses with a crack zig-zagging through, a warm glint deep in it."""
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            joint = y % 4 == 3 or (x + (y // 4) * 4) % 8 == 7
            image.putpixel((x, y), jitter(rng, (78, 76, 80) if joint else (122, 118, 120), 10))
    x = 3
    for y in range(16):
        x = max(1, min(14, x + rng.choice((-1, 0, 1, 1))))
        image.putpixel((x, y), (30, 28, 32, 255))
        if y % 5 == 2:
            image.putpixel((x, y), (224, 168, 92, 255))
        if rng.random() < 0.4:
            image.putpixel((min(15, x + 1), y), (52, 50, 56, 255))
    return image


def amber(rng):
    """Honeycomb gone to amber: hexagon cells, gold to brown, a glint in some."""
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 4
            cx = (x + (2 if row % 2 else 0)) % 4
            wall = y % 4 == 0 or cx == 0
            base = (150, 92, 24) if wall else (232, 168, 48)
            image.putpixel((x, y), jitter(rng, base, 12))
            if not wall and rng.random() < 0.06:
                image.putpixel((x, y), (255, 226, 140, 255))
    return image


def ice_mirror(rng):
    """A pane of clear ice in a frost rim: light, with a bright streak across like a reflection."""
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            rim = x in (0, 15) or y in (0, 15)
            streak = abs((x - y) - 3) <= 1 or abs((x - y) + 6) == 0
            colour = (206, 234, 250) if rim else (238, 250, 255) if streak else (150, 200, 236)
            image.putpixel((x, y), jitter(rng, colour, 6))
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(11)
    sconce(rng).save(os.path.join(OUT, "sconce.png"))
    tapestry(rng).save(os.path.join(OUT, "tapestry.png"))
    brazier(rng).save(os.path.join(OUT, "brazier.png"))
    coals(rng).save(os.path.join(OUT, "coals.png"))
    rune(rng).save(os.path.join(OUT, "rune.png"))
    rift_void(rng).save(os.path.join(OUT, "rift_void.png"))
    rift_swirl(rng).save(os.path.join(OUT, "rift_swirl.png"))
    rift_motes(rng).save(os.path.join(OUT, "rift_motes.png"))
    rift_edge(rng).save(os.path.join(OUT, "rift_edge.png"))
    rift_beam(rng).save(os.path.join(OUT, "rift_beam.png"))
    portal(rng).save(os.path.join(OUT, "portal.png"))
    with open(os.path.join(OUT, "portal.png.mcmeta"), "w") as meta:
        meta.write('{"animation": {"frametime": 2, "interpolate": true}}\n')
    portal_dark(rng).save(os.path.join(OUT, "portal_dark.png"))
    chest_side(rng).save(os.path.join(OUT, "chest_side.png"))
    chest_lid(rng).save(os.path.join(OUT, "chest_lid.png"))
    crystal(rng).save(os.path.join(OUT, "crystal.png"))
    cracked(rng).save(os.path.join(OUT, "cracked_wall.png"))
    amber(rng).save(os.path.join(OUT, "amber.png"))
    ice_mirror(rng).save(os.path.join(OUT, "ice_mirror.png"))
    glyph().save(os.path.join(OUT, "rune_glyph.png"))


if __name__ == "__main__":
    main()

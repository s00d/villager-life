#!/usr/bin/env python3
"""The floors' doors, riddles and curios (spire/SpireDoorBlock, SpirePuzzleBlock, SpireCurioBlock): their textures
(textures/block/spire/mech/*.png), models (models/block/spire/mech/*.json) and blockstates, drawn and built here.

Textures are of two sorts: materials (16x16 swatches — bronze, brass, dark wood, iron, slate, paper, crystal of the four
notes, petals, honey, puffball skin), sampled by a model's faces where they fall (as vanilla's own uv), and faces drawn
whole (the door's leaf, a glyph's rune, a rune plate, a cog, a valve's wheel, a gong's disk). Models are cuboids in
Blockbench's terms; a curio has a model a frame (FRAME 0..3), the door one a panel (ROW, COL) and its lit rune.

    python3 tools/art/paint_spire_mechanisms.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life")
TEX = os.path.join(ASSETS, "textures", "block", "spire", "mech")
MODELS = os.path.join(ASSETS, "models", "block", "spire", "mech")
STATES = os.path.join(ASSETS, "blockstates")
NS = "villager_life:block/spire/mech/"

BRONZE = (150, 98, 52)
BRASS = (196, 152, 76)
BRASS_LIGHT = (240, 206, 128)
BRASS_DARK = (112, 78, 38)
WOOD = (74, 50, 34)
WOOD_DARK = (46, 30, 22)
IRON = (96, 100, 108)
SLATE = (58, 60, 70)
SLATE_DARK = (38, 40, 48)
CORE = (178, 132, 236)
CORE_LIGHT = (236, 214, 255)
NOTES = [(127, 184, 255), (155, 224, 143), (230, 184, 90), (224, 127, 192)]


def jitter(rng, colour, amount, alpha=255):
    return tuple(max(0, min(255, int(c + (rng.random() - 0.5) * amount))) for c in colour) + (alpha,)


def lighter(colour, t):
    return tuple(int(c + (255 - c) * t) for c in colour)


def darker(colour, t):
    return tuple(int(c * (1 - t)) for c in colour)


def swatch(rng, base, amount=18, grain=None):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            colour = base
            if grain == "wood":
                colour = darker(base, 0.25) if (y + (x // 4) * 3) % 5 == 0 else base
            elif grain == "metal":
                colour = lighter(base, 0.25) if (x + y) % 7 == 0 else darker(base, 0.15) if (x * 3 + y) % 11 == 0 else base
            elif grain == "slate":
                colour = darker(base, 0.3) if y % 4 == 3 or (x + (y // 4) * 5) % 8 == 7 else base
            image.putpixel((x, y), jitter(rng, colour, amount))
    return image


def crystal(rng, base, lit):
    image = Image.new("RGBA", (16, 16))
    top = lighter(base, 0.55 if lit else 0.25)
    for y in range(16):
        for x in range(16):
            facet = (x + y) // 4 % 2 == 0
            colour = top if facet else (lighter(base, 0.2) if lit else base)
            if (x - y) % 6 == 0:
                colour = lighter(colour, 0.5)
            image.putpixel((x, y), jitter(rng, colour, 12))
    return image


TOWER = [
    ".......*.......",
    "......*o*......",
    ".......*.......",
    "......###......",
    ".....#####.....",
    "......###......",
    "......###......",
    ".....#####.....",
    ".....#####.....",
    "....#######....",
    "....#######....",
    "...#########...",
    "...#########...",
    "..###########..",
    ".#############.",
]


def door(rng, lit):
    """The leaf: 48x64 in a 64x64 sheet — slate planks in a bronze frame, riveted iron bands, a seam down the middle
    where it parts, and the Builders' sign: a stepped tower under the Core's spark, in a brass ring."""
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    plank_light = lighter(SLATE, 0.12)
    for y in range(64):
        for x in range(48):
            column = x % 8
            colour = SLATE_DARK if column == 0 else plank_light if column == 1 else SLATE
            if x in (23, 24):
                colour = darker(SLATE_DARK, 0.3)
            image.putpixel((x, y), jitter(rng, colour, 10))
    for y in range(64):
        for x in range(48):
            if x < 2 or x > 45 or y < 2 or y > 61:
                edge = x in (0, 47) or y in (0, 63)
                image.putpixel((x, y), jitter(rng, BRASS_DARK if edge else BRONZE, 10))
    for y0 in (8, 52):
        for y in range(y0, y0 + 4):
            for x in range(2, 46):
                colour = lighter(IRON, 0.3) if y == y0 else darker(IRON, 0.3) if y == y0 + 3 else IRON
                image.putpixel((x, y), jitter(rng, colour, 10))
        for x in range(5, 44, 6):
            image.putpixel((x, y0 + 1), BRASS_LIGHT + (255,))
            image.putpixel((x, y0 + 2), BRASS_DARK + (255,))
    cx, cy = 23.5, 30.5
    for y in range(64):
        for x in range(48):
            d = math.hypot(x - cx, y - cy)
            if d < 10.2:
                image.putpixel((x, y), jitter(rng, darker(SLATE_DARK, 0.35), 6))
            elif d < 12.3:
                # brass ring, lit from the top left
                light = (x - cx) + (y - cy) < -4
                colour = BRASS_LIGHT if light else BRASS_DARK if (x - cx) + (y - cy) > 6 else BRASS
                image.putpixel((x, y), jitter(rng, colour, 8))
    glow, mid, shade = (CORE_LIGHT, lighter(CORE, 0.35), CORE) if lit else (lighter(CORE, 0.25), CORE, darker(CORE, 0.35))
    for row, line in enumerate(TOWER):
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            x, y = 16 + col, 23 + row
            left = col < 7
            colour = (255, 248, 220) if ch == "o" else glow if ch == "*" else mid if left else shade
            image.putpixel((x, y), jitter(rng, colour, 6))
    # two ring knockers below the sign, either side of the seam
    knocker = [".bbb.", "b...b", "b...b", "b...b", ".bdb."]
    for hx in (15, 28):
        for dx in range(5):
            image.putpixel((hx + dx, 42), BRASS_DARK + (255,))
            image.putpixel((hx + dx, 43), BRONZE + (255,))
        for row, line in enumerate(knocker):
            for col, ch in enumerate(line):
                if ch != ".":
                    image.putpixel((hx + col, 44 + row), (BRASS_DARK if ch == "d" else BRASS_LIGHT if row < 2 else BRASS) + (255,))
    return image


def rune_face(rng, base, glow, lit, ring=True):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), jitter(rng, base, 14))
    colour = lighter(glow, 0.5) if lit else darker(glow, 0.45)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if ring and 5.5 < d < 6.6:
                image.putpixel((x, y), jitter(rng, colour, 10))
    for x, y in [(7, 3), (8, 3), (7, 4), (8, 4), (5, 6), (6, 7), (7, 8), (8, 8), (9, 7), (10, 6), (7, 9), (8, 9), (7, 10), (8, 10),
                 (6, 11), (9, 11), (5, 12), (10, 12)]:
        image.putpixel((x, y), jitter(rng, colour, 8))
    return image


def cog(rng):
    """A cog's face, see-through round it: eight teeth, a rim, four spokes, a hub."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            tooth = math.cos(a * 8) > 0.2
            if d < 5.6 or (d < 7.6 and tooth):
                spoke = abs(dx) < 1 or abs(dy) < 1
                if d < 1.6:
                    colour = BRASS_LIGHT
                elif d < 3.8 and not spoke:
                    continue
                else:
                    colour = BRASS if d < 6 else BRASS_DARK
                image.putpixel((x, y), jitter(rng, colour, 16))
    return image


def wheel(rng):
    """A valve's wheel: a rim, three spokes, a hub, see-through between."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            spoke = min(abs(math.sin(a - k * 2 * math.pi / 3)) for k in range(3)) < 0.18 and d < 6.5
            if 5.6 < d < 7.4 or spoke or d < 1.8:
                image.putpixel((x, y), jitter(rng, (170, 46, 40) if 5.6 < d else BRASS, 14))
    return image


def gong_disk(rng):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7.6:
                colour = BRASS_LIGHT if d < 2.2 else BRONZE if int(d) % 2 else lighter(BRONZE, 0.15)
                if d > 6.8:
                    colour = BRASS_DARK
                image.putpixel((x, y), jitter(rng, colour, 14))
    return image


def petals(rng, base):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            if d < 2.2:
                image.putpixel((x, y), jitter(rng, (240, 210, 90), 12))
            elif d < 7.5 * (0.55 + 0.45 * abs(math.cos(a * 2.5))):
                image.putpixel((x, y), jitter(rng, lighter(base, 0.3 * (1 - d / 8)), 16))
    return image


def spike_plate(rng):
    # worn slate with a three by three grid of dark holes, each with a glint of iron down in it
    image = swatch(rng, SLATE_DARK, 14, "slate")
    for i in range(3):
        for j in range(3):
            cx, cy = 3 + i * 5, 3 + j * 5
            for dx in range(-1, 1):
                for dy in range(-1, 1):
                    image.putpixel((cx + dx, cy + dy), jitter(rng, (22, 20, 24), 6))
            image.putpixel((cx - 1, cy - 1), jitter(rng, IRON, 10))
    return image


def dart_face(rng):
    # a wall stone with three dark slits and a brass rim round them: the face a dart wall turns to the passage
    image = swatch(rng, SLATE, 12, "slate")
    for x in range(3, 13):
        for y in (4, 8, 12):
            image.putpixel((x, y - 1), jitter(rng, BRASS, 14))
            image.putpixel((x, y), jitter(rng, (18, 16, 20), 6))
    return image


def paint_all():
    rng = random.Random(4127)
    os.makedirs(TEX, exist_ok=True)
    out = {
        "bronze": swatch(rng, BRONZE, 18, "metal"), "brass": swatch(rng, BRASS, 18, "metal"), "iron": swatch(rng, IRON, 14, "metal"),
        "wood": swatch(rng, WOOD, 12, "wood"), "slate": swatch(rng, SLATE, 12, "slate"), "paper": swatch(rng, (232, 220, 192), 10),
        "leaf": swatch(rng, (82, 132, 58), 22), "stem": swatch(rng, (96, 140, 66), 14), "honey": swatch(rng, (232, 160, 40), 20),
        "wax": swatch(rng, (226, 186, 92), 16, "slate"), "puff": swatch(rng, (226, 214, 196), 16), "puff_spot": swatch(rng, (170, 150, 196), 20),
        "glass": swatch(rng, (150, 200, 230), 20), "red": swatch(rng, (150, 40, 40), 16),
        "door": door(rng, False), "door_lit": door(rng, True), "cog": cog(rng), "wheel": wheel(rng), "gong_disk": gong_disk(rng),
        "glyph": rune_face(rng, SLATE, CORE, False), "glyph_lit": rune_face(rng, SLATE, CORE, True),
        "plate": rune_face(rng, SLATE_DARK, BRASS, False), "plate_lit": rune_face(rng, SLATE_DARK, BRASS_LIGHT, True),
        "resonator": crystal(rng, (200, 210, 236), False), "resonator_lit": crystal(rng, (200, 210, 236), True),
        "petals": petals(rng, (238, 140, 186)), "petals_side": swatch(rng, (232, 132, 178), 22), "core": crystal(rng, CORE, True),
    }
    for i, colour in enumerate(NOTES):
        out[f"chime_{i}"] = crystal(rng, colour, False)
        out[f"chime_{i}_lit"] = crystal(rng, colour, True)
    # the traps' own (a seed of their own, so the textures before them stay as they were)
    traps = random.Random(5521)
    out["spike_plate"] = spike_plate(traps)
    out["dart_face"] = dart_face(traps)
    for name, image in out.items():
        image.save(os.path.join(TEX, name + ".png"))
    dart_item().save(os.path.join(ASSETS, "textures", "item", "spire_dart_bolt.png"))
    return out


def dart_item():
    # a dart wall's shot as it flies: a brass point, a slate-dark shaft, a tuft of red at the back, corner to corner
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 13):
        img.putpixel((i, 15 - i), (58, 50, 46, 255) if i % 2 else (78, 68, 60, 255))
        img.putpixel((i + 1, 15 - i), (36, 30, 28, 255))
    for (x, y, c) in ((13, 2, BRASS_LIGHT), (14, 1, BRASS_LIGHT), (12, 2, BRASS), (13, 3, BRASS), (14, 2, BRASS), (15, 0, (255, 236, 180))):
        img.putpixel((x, y), tuple(c) + (255,))
    for (x, y) in ((1, 12), (2, 12), (1, 13), (3, 14), (3, 13), (2, 14), (1, 11), (4, 14), (2, 11), (4, 13)):
        img.putpixel((x, y), (168, 44, 40, 255) if (x + y) % 2 else (210, 70, 60, 255))
    return img


# ---- models ---------------------------------------------------------------------------------------------------------

def box(frm, to, tex, faces="nsewud", rot=None, uv=None, emission=None):
    """A cuboid; each face samples its texture where it falls (vanilla's own uv), or `uv` for all the faces."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    def face_uv(f):
        if uv:
            return uv
        return {"n": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "s": [x1, 16 - y2, x2, 16 - y1], "e": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
                "w": [z1, 16 - y2, z2, 16 - y1], "u": [x1, z1, x2, z2], "d": [x1, 16 - z2, x2, 16 - z1]}[f]
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    element = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        element["faces"][names[f]] = {"uv": [round(v, 3) for v in face_uv(f)], "texture": "#" + tex}
    if rot:
        element["rotation"] = {"angle": rot[0], "axis": rot[1], "origin": list(rot[2])}
    if emission:
        element["light_emission"] = emission
    return element


def model(name, textures, elements, particle=None):
    os.makedirs(MODELS, exist_ok=True)
    data = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": NS + (particle or next(iter(textures.values())))} | {k: NS + v for k, v in textures.items()},
            "elements": elements}
    with open(os.path.join(MODELS, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)
    return "villager_life:block/spire/mech/" + name


ANGLES = [0, 22.5, 45, -22.5]


def door_models():
    for lit in (False, True):
        for row in range(4):
            for col in range(3):
                # the leaf's picture is 48x64 in the sheet's top left: a panel is 16x16 of it (12x16 of the sheet's 16 units)
                u1, u2 = col * 4, col * 4 + 4
                v1, v2 = (3 - row) * 4, (4 - row) * 4
                front = [u1, v1, u2, v2]
                back = [u2, v1, u1, v2]
                edge = [0, 0, 1, 4]
                elements = [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
                    "north": {"uv": front, "texture": "#d"}, "south": {"uv": back, "texture": "#d"},
                    "east": {"uv": edge, "texture": "#d"}, "west": {"uv": edge, "texture": "#d"},
                    "up": {"uv": [0, 0, 4, 1], "texture": "#d"}, "down": {"uv": [0, 0, 4, 1], "texture": "#d"}}}]
                if lit:
                    elements[0]["light_emission"] = 9
                model(f"door_{row}_{col}{'_lit' if lit else ''}", {"d": "door_lit" if lit else "door"}, elements)


def wall_models():
    # lever: a brass plate on the wall (the model's wall is at its south side), a slate block, the handle up or down
    for on in (False, True):
        tilt = -45 if on else 45
        model(f"lever{'_on' if on else ''}", {"p": "brass", "s": "slate", "h": "iron", "k": "red"}, [
            box((4, 3, 14), (12, 13, 16), "p"),
            box((6, 6, 12), (10, 10, 14), "s"),
            box((7.25, 8, 6), (8.75, 9.5, 13), "h", rot=(tilt, "x", (8, 8, 12))),
            box((6.5, 7.5, 4.5), (9.5, 10, 7), "k", rot=(tilt, "x", (8, 8, 12))),
        ])
    for lit in (False, True):
        model(f"glyph{'_lit' if lit else ''}", {"g": "glyph_lit" if lit else "glyph", "f": "brass"}, [
            box((2, 2, 13), (14, 14, 16), "f", faces="sewud"),
            {"from": [2, 2, 13], "to": [14, 14, 16], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#g"}}, **({"light_emission": 12} if lit else {})},
        ])
    # cog: a toothed disk standing off the wall on an axle, turned a little a frame
    for f, angle in enumerate(ANGLES):
        model(f"cog_{f}", {"c": "cog", "a": "iron"}, [
            box((7, 7, 14), (9, 9, 16), "a"),
            {"from": [0, 0, 13], "to": [16, 16, 13.01], "rotation": {"angle": angle, "axis": "z", "origin": [8, 8, 13]},
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#c"}, "south": {"uv": [16, 0, 0, 16], "texture": "#c"}}},
        ])
    # valve: a pipe out of the wall bending down to a mouth, a red wheel on its stem
    for f, angle in enumerate(ANGLES):
        model(f"valve_{f}", {"p": "bronze", "w": "wheel", "i": "iron"}, [
            box((5, 5, 9), (11, 11, 16), "p"),
            box((5, 0, 9), (11, 5, 15), "p"),
            box((4.5, 4, 8.5), (11.5, 5, 15.5), "i"),
            box((7.5, 7.5, 7), (8.5, 8.5, 10), "i"),
            {"from": [0, 0, 6.5], "to": [16, 16, 6.51], "rotation": {"angle": angle, "axis": "z", "origin": [8, 8, 6.5]},
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#w"}, "south": {"uv": [16, 0, 0, 16], "texture": "#w"}}},
        ])
    # honey tap: a waxen keg bound in brass on the wall, a spout and its little lever, honey running on frame 1
    for f in (0, 1):
        model(f"honey_tap_{f}", {"w": "wax", "b": "brass", "h": "honey"}, [
            box((2, 4, 9), (14, 16, 16), "w"),
            box((1.5, 6, 8.5), (14.5, 7, 16), "b"), box((1.5, 13, 8.5), (14.5, 14, 16), "b"),
            box((6.5, 4.5, 5), (9.5, 7.5, 9), "b"),
            box((6.5, 2.5, 5), (9.5, 4.5, 7), "b"),
            box((7.5, 7.5, 6), (8.5, 11, 7), "b", rot=(-45 if f else 0, "x", (8, 7.5, 6.5))),
        ] + ([box((7.25, 0, 5.5), (8.75, 2.5, 6.5), "h")] if f else []))


def trap_models():
    model("spikes", {"p": "spike_plate", "s": "slate"}, [box((0, 0, 0), (16, 1, 16), "s", uv=None) | {"faces": {
        "up": {"uv": [0, 0, 16, 16], "texture": "#p"}, "north": {"uv": [0, 15, 16, 16], "texture": "#s"}, "south": {"uv": [0, 15, 16, 16], "texture": "#s"},
        "east": {"uv": [0, 15, 16, 16], "texture": "#s"}, "west": {"uv": [0, 15, 16, 16], "texture": "#s"}, "down": {"uv": [0, 0, 16, 16], "texture": "#s"}}}])
    spikes = [box((0, 0, 0), (16, 1, 16), "s") | {"faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#p"}, "north": {"uv": [0, 15, 16, 16], "texture": "#s"},
              "south": {"uv": [0, 15, 16, 16], "texture": "#s"}, "east": {"uv": [0, 15, 16, 16], "texture": "#s"},
              "west": {"uv": [0, 15, 16, 16], "texture": "#s"}, "down": {"uv": [0, 0, 16, 16], "texture": "#s"}}}]
    for i in range(3):
        for j in range(3):
            x, z = 2 + i * 5, 2 + j * 5
            spikes.append(box((x, 1, z), (x + 1.5, 8, z + 1.5), "i"))
            spikes.append(box((x + 0.25, 8, z + 0.25), (x + 1.25, 10.5, z + 1.25), "t"))
    model("spikes_out", {"p": "spike_plate", "s": "slate", "i": "iron", "t": "brass"}, spikes)
    model("dart", {"f": "dart_face", "s": "slate"}, [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#f"}, "south": {"uv": [0, 0, 16, 16], "texture": "#s"}, "east": {"uv": [0, 0, 16, 16], "texture": "#s"},
        "west": {"uv": [0, 0, 16, 16], "texture": "#s"}, "up": {"uv": [0, 0, 16, 16], "texture": "#s"}, "down": {"uv": [0, 0, 16, 16], "texture": "#s"}}}])


def floor_models():
    # chime: a wide iron arch on a round foot, a big crystal of its note hanging in it
    for note in range(4):
        for lit in (False, True):
            c = f"chime_{note}{'_lit' if lit else ''}"
            model(f"chime_{note}{'_lit' if lit else ''}", {"s": "iron", "c": c, "b": "brass"}, [
                box((2, 0, 2), (14, 1.5, 14), "s"),
                box((3, 1.5, 3), (13, 2, 13), "b"),
                box((2.5, 2, 7), (4, 16, 9), "s"),
                box((12, 2, 7), (13.5, 16, 9), "s"),
                box((2.5, 14.5, 7), (13.5, 16, 9), "b"),
                box((7.5, 13, 7.5), (8.5, 14.5, 8.5), "b"),
                box((6, 9, 6), (10, 13, 10), "c", emission=12 if lit else None),
                box((6.5, 5, 6.5), (9.5, 9, 9.5), "c", emission=12 if lit else None),
                box((7.25, 3.5, 7.25), (8.75, 5, 8.75), "c", emission=12 if lit else None),
            ])
    for lit in (False, True):
        model(f"plate{'_lit' if lit else ''}", {"p": "plate_lit" if lit else "plate", "s": "slate"}, [
            {"from": [1, 0, 1], "to": [15, 1, 15], **({"light_emission": 12} if lit else {}), "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#p"}, "north": {"uv": [1, 15, 15, 16], "texture": "#s"},
                "south": {"uv": [1, 15, 15, 16], "texture": "#s"}, "east": {"uv": [1, 15, 15, 16], "texture": "#s"},
                "west": {"uv": [1, 15, 15, 16], "texture": "#s"}}},
        ])
    # the roots' bell: a wooden yoke on two posts, a bronze bell in it (round from any side), swinging over the frames
    for f, angle in enumerate([0, 22.5, 0, -22.5]):
        swing = (angle, "x", (8, 14.5, 8))
        model(f"gong_{f}", {"w": "wood", "b": "bronze", "l": "brass", "i": "iron"}, [
            box((0.5, 0, 6.5), (3, 16, 9.5), "w"), box((13, 0, 6.5), (15.5, 16, 9.5), "w"), box((0, 14, 6), (16, 16, 10), "w"),
            box((0, 0, 5), (3.5, 1.5, 11), "w"), box((12.5, 0, 5), (16, 1.5, 11), "w"),
            box((7, 13, 7), (9, 14, 9), "i", rot=swing),
            box((6, 11.5, 6), (10, 13, 10), "b", rot=swing),
            box((5, 8, 5), (11, 11.5, 11), "b", rot=swing),
            box((4, 5, 4), (12, 8, 12), "b", rot=swing),
            box((3.5, 4, 3.5), (12.5, 5, 12.5), "l", rot=swing),
            box((7.5, 2.5, 7.5), (8.5, 4, 8.5), "i", rot=swing),
        ])
    # lectern: a wooden stand, a slanted desk, an open book whose page turns over the frames
    for f, angle in enumerate([0, -22.5, -45, -45]):
        page = [box((8, 11.6, 4.5), (13, 11.7, 11.5), "p", faces="ud", rot=(angle, "z", (8, 11.5, 8)))] if f else []
        model(f"lectern_{f}", {"w": "wood", "p": "paper", "b": "brass", "r": "red"}, [
            box((4, 0, 4), (12, 2, 12), "w"),
            box((6.5, 2, 6.5), (9.5, 10, 9.5), "w"),
            box((2, 10, 3), (14, 11, 13), "w", rot=(-22.5, "x", (8, 10.5, 8))),
            box((3, 11, 4), (13, 11.5, 12), "r", rot=(-22.5, "x", (8, 10.5, 8))),
            box((3, 11.5, 4.5), (7.9, 12, 11.5), "p", rot=(-22.5, "x", (8, 10.5, 8))),
            box((8.1, 11.5, 4.5), (13, 12, 11.5), "p", rot=(-22.5, "x", (8, 10.5, 8))),
            box((7.9, 11.5, 3.5), (8.1, 12.2, 12.5), "b", rot=(-22.5, "x", (8, 10.5, 8))),
        ] + page)
    # crank: an iron post on a brass foot, an arm with a wooden grip, a quarter turn over the frames
    for f, angle in enumerate(ANGLES):
        model(f"crank_{f}", {"i": "iron", "b": "brass", "w": "wood"}, [
            box((2, 0, 2), (14, 2, 14), "b"),
            box((5, 2, 5), (11, 10, 11), "w"),
            box((4.5, 3, 4.5), (11.5, 4, 11.5), "i"), box((4.5, 8, 4.5), (11.5, 9, 11.5), "i"),
            box((7, 10, 7), (9, 12, 9), "i"),
            box((7.5, 11, 0.5), (8.5, 12, 8), "i", rot=(angle, "y", (8, 11.5, 8))),
            box((7, 12, 0.5), (9, 16, 2.5), "w", rot=(angle, "y", (8, 11.5, 8))),
        ])
    # bud: a stout stem in broad leaves, a big bud closed (0) opening into a flower as wide as the block (3)
    for f in range(4):
        elements = [box((7, 0, 7), (9, 9, 9), "s"),
                    box((1, 0.5, 6), (7, 0.6, 10), "l", faces="ud", rot=(-22.5, "z", (7, 0.5, 8))),
                    box((9, 0.5, 6), (15, 0.6, 10), "l", faces="ud", rot=(22.5, "z", (9, 0.5, 8))),
                    box((6, 0.5, 1), (10, 0.6, 7), "l", faces="ud", rot=(22.5, "x", (8, 0.5, 7))),
                    box((6, 0.5, 9), (10, 0.6, 15), "l", faces="ud", rot=(-22.5, "x", (8, 0.5, 9)))]
        if f == 0:
            elements.append(box((5.5, 9, 5.5), (10.5, 15, 10.5), "p"))
            elements.append(box((6.5, 15, 6.5), (9.5, 16, 9.5), "p"))
        elif f < 3:
            a = 22.5 if f == 1 else 45
            elements += [box((5, 9, 8), (11, 16, 8.1), "p", faces="ns", rot=(a, "x", (8, 9, 8))),
                         box((5, 9, 7.9), (11, 16, 8), "p", faces="ns", rot=(-a, "x", (8, 9, 8))),
                         box((8, 9, 5), (8.1, 16, 11), "p", faces="ew", rot=(a, "z", (8, 9, 8))),
                         box((7.9, 9, 5), (8, 16, 11), "p", faces="ew", rot=(-a, "z", (8, 9, 8))),
                         box((7, 9, 7), (9, 11, 9), "y")]
        else:
            elements += [{"from": [0, 10, 0], "to": [16, 10.01, 16], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#f"},
                                                                              "down": {"uv": [0, 0, 16, 16], "texture": "#f"}}},
                         box((7, 9, 7), (9, 11, 9), "y")]
        model(f"bud_{f}", {"s": "stem", "l": "leaf", "p": "petals_side", "f": "petals", "y": "honey"}, elements)
    # puffball: a dome of stacked rings on a mossy patch, a darker crown with spots (0), spent flat (1), growing back (2, 3)
    for f, scale in enumerate([1.0, 0.0, 0.45, 0.75]):
        elements = [box((1, 0, 1), (15, 0.5, 15), "g", faces="u")]
        if scale == 0.0:
            elements += [box((2, 0.5, 2), (14, 2, 14), "p"), box((3, 2, 3), (13, 2.5, 13), "s", faces="u")]
        else:
            layers = [(6.0, 0.0, 4.5), (5.0, 4.5, 3.0), (3.5, 7.5, 2.0), (2.0, 9.5, 1.0)]
            for k, (half, y0, h) in enumerate(layers):
                hw = half * scale + 0.5
                elements.append(box((8 - hw, 0.5 + y0 * scale, 8 - hw), (8 + hw, 0.5 + (y0 + h) * scale, 8 + hw), "s" if k >= 2 else "p"))
        model(f"puffball_{f}", {"p": "puff", "s": "puff_spot", "g": "leaf"}, elements)
    # orrery: a brass foot, a sun of the Core on a rod, two rings with their planets, turned over the frames
    for f, angle in enumerate(ANGLES):
        model(f"orrery_{f}", {"b": "brass", "w": "wood", "c": "core", "n": "chime_0", "g": "chime_2"}, [
            box((3, 0, 3), (13, 2, 13), "w"),
            box((7.25, 2, 7.25), (8.75, 7, 8.75), "b"),
            box((5.5, 7, 5.5), (10.5, 12, 10.5), "c", emission=12),
            box((0, 9.25, 7.5), (16, 9.75, 8.5), "b", rot=(angle, "y", (8, 9.5, 8))),
            box((0, 8.25, 7), (2.5, 10.75, 9), "n", rot=(angle, "y", (8, 9.5, 8))),
            box((7.5, 13.25, 1), (8.5, 13.75, 15), "b", rot=(-angle, "y", (8, 13.5, 8))),
            box((7, 12.5, 12.5), (9, 14.5, 14.5), "g", rot=(-angle, "y", (8, 13.5, 8))),
            box((7.5, 12, 7.5), (8.5, 13.5, 8.5), "b"),
        ])
    # resonator: a slate foot, a crystal obelisk, glowing as it hums (frames 1..3)
    for f in range(4):
        lit = f > 0
        model(f"resonator_{f}", {"s": "slate", "c": "resonator_lit" if lit else "resonator", "b": "brass"}, [
            box((1, 0, 1), (15, 2, 15), "s"),
            box((2.5, 2, 2.5), (13.5, 3, 13.5), "b"),
            box((4.5, 3, 4.5), (11.5, 11, 11.5), "c", emission=[0, 8, 12, 15][f] or None),
            box((5.5, 11, 5.5), (10.5, 14, 10.5), "c", emission=[0, 8, 12, 15][f] or None),
            box((6.5, 14, 6.5), (9.5, 16, 9.5), "c", emission=[0, 8, 12, 15][f] or None),
        ])


# ---- blockstates ----------------------------------------------------------------------------------------------------

Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def state_file(block, variants):
    os.makedirs(STATES, exist_ok=True)
    with open(os.path.join(STATES, block + ".json"), "w") as f:
        json.dump({"variants": variants}, f, indent=1)


def blockstates():
    v = {}
    for facing in Y:
        for row in range(4):
            for col in range(3):
                for opening in ("false", "true"):
                    v[f"col={col},facing={facing},open={opening},row={row}"] = {
                        "model": f"villager_life:block/spire/mech/door_{row}_{col}{'_lit' if opening == 'true' else ''}", "y": Y[facing]}
    state_file("spire_door", v)

    def puzzle(block, pick):
        out = {}
        for facing in Y:
            for lit in ("false", "true"):
                for note in range(4):
                    m = pick(lit == "true", note)
                    out[f"facing={facing},lit={lit},note={note}"] = {"model": "villager_life:block/spire/mech/" + m, "y": Y[facing]}
        state_file(block, out)

    puzzle("spire_lever", lambda lit, note: "lever_on" if lit else "lever")
    puzzle("spire_glyph", lambda lit, note: "glyph_lit" if lit else "glyph")
    puzzle("spire_chime", lambda lit, note: f"chime_{note}{'_lit' if lit else ''}")
    puzzle("spire_plate", lambda lit, note: "plate_lit" if lit else "plate")

    # a dart wall's face is its north face, turned to where it looks (the model faces north at y 0)
    for block, pick in (("spire_spikes", lambda stage: "spikes_out" if stage == 2 else "spikes"), ("spire_dart", lambda stage: "dart")):
        out = {}
        for facing in Y:
            for stage in range(3):
                out[f"facing={facing},stage={stage}"] = {"model": "villager_life:block/spire/mech/" + pick(stage), "y": Y[facing]}
        state_file(block, out)

    frames = {"spire_gong": "gong", "spire_lectern": "lectern", "spire_crank": "crank", "spire_cog": "cog", "spire_bud": "bud",
              "spire_valve": "valve", "spire_puffball": "puffball", "spire_honey_tap": "honey_tap", "spire_orrery": "orrery",
              "spire_resonator": "resonator"}
    for block, name in frames.items():
        out = {}
        for facing in Y:
            for frame in range(4):
                f = min(frame, 1) if name == "honey_tap" else frame
                out[f"facing={facing},frame={frame}"] = {"model": f"villager_life:block/spire/mech/{name}_{f}", "y": Y[facing]}
        state_file(block, out)


def main():
    paint_all()
    door_models()
    wall_models()
    floor_models()
    trap_models()
    blockstates()
    print("mechanisms:", TEX, MODELS)


if __name__ == "__main__":
    main()

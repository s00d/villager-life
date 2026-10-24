#!/usr/bin/env python3
"""The megastructures' blocks and keystones (mega/MegaBlocks): textures, models, blockstates, item models, drawn here.

    textures/block/mega/{frame,part_colosseum,part_path,part_nameless,quarry_front[_on],gate_front[_on],field}.png
    textures/item/gate_key_<colour>.png
    models/block/mega/*.json, models/item/{mega_*,gate_key_*}.json, blockstates/mega_*.json

The frame is the Builders' dark iron with brass bolts; a part is a plate of its portal's colour with its mark (the
Colosseum's crossed blades, the Path's cloud spiral, the Nameless's mask); a controller's face shows its work (a drill
bit, a keystone socket), lit when its structure stands whole; a gate's field is a pale swirl the block colour tints by
the keystone (four frames). A keystone is a cut gem in a brass setting, recoloured to each of the sixteen dyes.

    python3 tools/art/paint_mega.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life")
TEX = os.path.join(ASSETS, "textures", "block", "mega")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "block", "mega")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")
STATES = os.path.join(ASSETS, "blockstates")
NS = "villager_life:block/mega/"

IRON = (70, 74, 82)
IRON_DARK = (44, 46, 54)
IRON_LIGHT = (112, 118, 128)
BRASS = (196, 152, 76)
BRASS_LIGHT = (240, 206, 128)
BRASS_DARK = (112, 78, 38)
DYES = {  # vanilla's dye colours (DyeColor.getTextureDiffuseColor)
    "white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA, "yellow": 0xFED83D,
    "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52, "light_gray": 0x9D9D97, "cyan": 0x169C9C,
    "purple": 0x8932B8, "blue": 0x3C44AA, "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21,
}


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def plate(base, light, dark, seed):
    """A riveted plate: a bevelled rim, brass bolts in the corners, a little wear."""
    rng = random.Random(seed)
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = base
            if x == 0 or y == 0:
                c = light
            elif x == 15 or y == 15:
                c = dark
            elif x in (1, 14) or y in (1, 14):
                c = mix(base, dark, 0.35)[:3]
            jitter = (rng.random() - 0.5) * 10
            image.putpixel((x, y), tuple(max(0, min(255, int(v + jitter))) for v in c) + (255,))
    for bx, by in ((2, 2), (13, 2), (2, 13), (13, 13)):
        image.putpixel((bx, by), BRASS_LIGHT + (255,))
        image.putpixel((bx + 1 if bx < 8 else bx - 1, by), BRASS + (255,))
        image.putpixel((bx, by + 1 if by < 8 else by - 1), BRASS_DARK + (255,))
    return image


def frame():
    image = plate(IRON, IRON_LIGHT, IRON_DARK, 1)
    # the groove of the Builders round the middle
    for i in range(4, 12):
        for (x, y) in ((i, 4), (i, 11), (4, i), (11, i)):
            image.putpixel((x, y), IRON_DARK + (255,))
    for i in range(5, 11):
        image.putpixel((i, 5), IRON_LIGHT + (255,))
    return image


def mark(image, pixels, colour, glow):
    for (x, y) in pixels:
        image.putpixel((x, y), glow + (255,))
        if y + 1 < 16 and (x, y + 1) not in pixels:
            image.putpixel((x, y + 1), colour + (255,))


def colosseum():
    red, light, dark = (150, 46, 36), (214, 96, 70), (82, 22, 18)
    image = plate(red, light, dark, 2)
    blades = [(4 + i, 4 + i) for i in range(8)] + [(11 - i, 4 + i) for i in range(8)]
    mark(image, blades, BRASS_DARK, BRASS_LIGHT)
    for (x, y) in ((4, 10), (5, 11), (11, 10), (10, 11)):
        image.putpixel((x, y), BRASS + (255,))
    return image


def path():
    blue, light, dark = (74, 132, 190), (150, 204, 240), (34, 70, 110)
    image = plate(blue, light, dark, 3)
    spiral = []
    for i in range(40):
        a = i * 0.42
        r = 1 + i * 0.11
        spiral.append((int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))))
    mark(image, sorted(set(spiral)), (190, 220, 240), (245, 252, 255))
    return image


def nameless():
    violet, light, dark = (70, 40, 100), (140, 96, 190), (34, 18, 52)
    image = plate(violet, light, dark, 4)
    # a mask: brow, two eye slits that glow, a mouth line
    for x in range(4, 12):
        image.putpixel((x, 4), (180, 150, 220, 255))
    for (x, y) in ((5, 6), (6, 6), (9, 6), (10, 6)):
        image.putpixel((x, y), (236, 214, 255, 255))
    for (x, y) in ((5, 7), (6, 7), (9, 7), (10, 7)):
        image.putpixel((x, y), (178, 132, 236, 255))
    for x in range(6, 10):
        image.putpixel((x, 10), (120, 86, 160, 255))
    for (x, y) in ((4, 5), (11, 5), (4, 9), (11, 9), (5, 11), (10, 11), (6, 12), (9, 12), (7, 12), (8, 12)):
        image.putpixel((x, y), (120, 86, 160, 255))
    return image


GLOWS = {"quarry": (255, 214, 120), "gate": (236, 214, 255), "smelter": (255, 150, 60), "calm": (200, 240, 255), "dome": (150, 240, 120),
         "well": (120, 220, 140), "drain": (110, 180, 255), "sawmill": (230, 190, 120)}


def face(kind, lit):
    image = frame()
    glow = GLOWS[kind]
    dim = tuple(int(c * 0.35) for c in glow)
    c = glow if lit else dim
    if kind not in ("quarry", "gate"):
        return emblem(image, kind, c)
    if kind == "quarry":
        # a drill bit, point down
        for y in range(4, 13):
            half = max(0, (12 - y) // 2)
            for x in range(8 - half - 1, 8 + half + 1):
                image.putpixel((x, y), (mix(c, (255, 255, 255), 0.3) if x < 8 else c[:3] + (255,)) if (x + y) % 3 else BRASS_DARK + (255,))
    else:
        # the keystone socket: a ring and the gem's seat
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 3.6 <= d <= 4.8:
                    image.putpixel((x, y), BRASS_LIGHT + (255,) if x + y < 15 else BRASS_DARK + (255,))
                elif d < 3.0:
                    image.putpixel((x, y), c + (255,))
    return image


def emblem(image, kind, c):
    """The face of the later controllers: a grate of fire, a calm sun, a sprout, an eddy of lore, a pipe's mouth."""
    px = lambda x, y, col: image.putpixel((x, y), tuple(col[:3]) + (255,))
    light = mix(c, (255, 255, 255), 0.45)[:3]
    for y in range(4, 12):
        for x in range(4, 12):
            px(x, y, IRON_DARK)
    if kind == "smelter":
        for x in range(4, 12):
            for y in range(5, 11):
                if x % 2 == 0:
                    px(x, y, (40, 36, 34))
        for (x, y) in ((5, 10), (7, 9), (9, 10), (6, 8), (8, 8), (10, 9), (7, 7), (9, 7)):
            px(x, y, c if y > 8 else light)
    elif kind == "calm":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 2.2:
                    px(x, y, light)
                elif d < 3.4 and (x + y) % 2 == 0:
                    px(x, y, c)
    elif kind == "dome":
        for y in range(6, 12):
            px(7, y, (90, 140, 60))
        for (x, y) in ((5, 6), (6, 6), (6, 5), (9, 7), (10, 7), (10, 6), (8, 5), (8, 4)):
            px(x, y, c)
    elif kind == "well":
        for i in range(30):
            a = i * 0.5
            r = 0.6 + i * 0.12
            x, y = int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))
            if 4 <= x < 12 and 4 <= y < 12:
                px(x, y, c if i % 3 else light)
    elif kind == "sawmill":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                if d <= 3.6 and (d < 1.2 or int((a + math.pi) * 8 / math.pi) % 2 == 0):
                    px(x, y, light if d < 1.2 else c)
    elif kind == "drain":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 2.0 <= d <= 3.6:
                    px(x, y, BRASS_LIGHT if y < 8 else BRASS_DARK)
                elif d < 2.0:
                    px(x, y, c)
    return image


def field():
    """Four frames of a slow pale swirl, a column of them for the .mcmeta animation; tinted by the block colour."""
    sheet = Image.new("RGBA", (16, 64))
    for f in range(4):
        for y in range(16):
            for x in range(16):
                a = math.atan2(y - 7.5, x - 7.5)
                r = math.hypot(x - 7.5, y - 7.5)
                v = 0.5 + 0.5 * math.sin(a * 3 + r * 0.9 - f * math.pi / 2)
                level = int(150 + 105 * v)
                sheet.putpixel((x, f * 16 + y), (level, level, level, int(140 + 80 * v)))
    return sheet


def key(colour):
    base = rgb(colour)
    light = mix(base, (255, 255, 255), 0.55)[:3]
    dark = mix(base, (0, 0, 0), 0.45)[:3]
    image = Image.new("RGBA", (16, 16))
    # the brass setting: a ring with a loop on top
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            if 4.6 <= d <= 5.6:
                image.putpixel((x, y), (BRASS_LIGHT if y < 8 else BRASS_DARK) + (255,))
    for (x, y) in ((7, 1), (8, 1), (6, 2), (9, 2)):
        image.putpixel((x, y), BRASS + (255,))
    # the cut gem: facets lit from the upper left
    for y in range(4, 14):
        for x in range(3, 13):
            if abs(x - 7.5) + abs(y - 8.5) <= 4.6:
                facet = light if x < 7 and y < 8 else base if (x < 8) == (y < 9) else dark
                image.putpixel((x, y), facet + (255,))
    image.putpixel((6, 6), (255, 255, 255, 255))
    # an outline, the way items read
    out = image.copy()
    for y in range(16):
        for x in range(16):
            if image.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and image.getpixel((x + dx, y + dy))[3]
                                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), (30, 22, 14, 255))
    return out


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    os.makedirs(TEX, exist_ok=True)
    frame().save(os.path.join(TEX, "frame.png"))
    colosseum().save(os.path.join(TEX, "part_colosseum.png"))
    path().save(os.path.join(TEX, "part_path.png"))
    nameless().save(os.path.join(TEX, "part_nameless.png"))
    for kind in GLOWS:
        face(kind, False).save(os.path.join(TEX, f"{kind}_front.png"))
        face(kind, True).save(os.path.join(TEX, f"{kind}_front_on.png"))
    field().save(os.path.join(TEX, "field.png"))
    write(os.path.join(TEX, "field.png.mcmeta"), {"animation": {"frametime": 4, "interpolate": True}})
    for name, colour in DYES.items():
        key(colour).save(os.path.join(ITEM_TEX, f"gate_key_{name}.png"))
        write(os.path.join(ITEM_MODELS, f"gate_key_{name}.json"), {"parent": "minecraft:item/generated",
                                                                    "textures": {"layer0": f"villager_life:item/gate_key_{name}"}})
    # plain cubes
    for block, texture in (("mega_frame", "frame"), ("mega_part_colosseum", "part_colosseum"), ("mega_part_path", "part_path"),
                           ("mega_part_nameless", "part_nameless")):
        write(os.path.join(MODELS, f"{texture}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": NS + texture}})
        write(os.path.join(STATES, f"{block}.json"), {"variants": {"": {"model": NS + texture}}})
        write(os.path.join(ITEM_MODELS, f"{block}.json"), {"parent": NS + texture})
    # controllers: frame all round, their face to the front; lit when formed
    for kind in GLOWS:
        for lit in (False, True):
            name = f"{kind}{'_on' if lit else ''}"
            write(os.path.join(MODELS, f"{name}.json"), {"parent": "minecraft:block/orientable",
                                                         "textures": {"front": NS + f"{kind}_front{'_on' if lit else ''}", "side": NS + "frame",
                                                                      "top": NS + "frame"}})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for formed in ("false", "true"):
                variant = {"model": NS + (f"{kind}_on" if formed == "true" else kind)}
                if y:
                    variant["y"] = y
                variants[f"facing={facing},formed={formed}"] = variant
        write(os.path.join(STATES, f"mega_{kind}.json"), {"variants": variants})
        write(os.path.join(ITEM_MODELS, f"mega_{kind}.json"), {"parent": NS + kind})
    # the field: a sheet in the frame's plane, tinted (tintindex 0) by the keystone's colour
    sheet = {"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
        "north": {"texture": "#field", "tintindex": 0}, "south": {"texture": "#field", "tintindex": 0}}}
    write(os.path.join(MODELS, "field.json"), {"render_type": "minecraft:translucent", "textures": {"field": NS + "field", "particle": NS + "field"},
                                               "elements": [sheet]})
    variants = {}
    for colour in range(16):
        variants[f"axis=x,colour={colour}"] = {"model": NS + "field"}
        variants[f"axis=z,colour={colour}"] = {"model": NS + "field", "y": 90}
    write(os.path.join(STATES, "mega_gate_field.json"), {"variants": variants})
    outer()
    print("mega: blocks, controllers, field, 16 keystones; outer gates, crumbling stone")


OUTER = {"arena": (224, 80, 58), "isles": (138, 208, 244), "rush": (120, 52, 190), "exit": (240, 230, 200)}


def outer():
    """The Outer Gates' portals: the spire portal's own swirl (its frames) recoloured to each gate; the Sky Path's
    crumbling stone whole and cracked."""
    source = Image.open(os.path.join(ASSETS, "textures", "block", "spire", "portal.png")).convert("RGBA")
    meta = os.path.join(ASSETS, "textures", "block", "spire", "portal.png.mcmeta")
    out_dir = os.path.join(ASSETS, "textures", "block", "outer")
    os.makedirs(out_dir, exist_ok=True)
    for kind, colour in OUTER.items():
        image = Image.new("RGBA", source.size)
        for y in range(source.height):
            for x in range(source.width):
                r, g, b, a = source.getpixel((x, y))
                lum = (r * 0.3 + g * 0.55 + b * 0.15) / 255
                tint = tuple(int(min(255, c * (0.35 + lum * 0.9) + 255 * max(0, lum - 0.75))) for c in colour)
                image.putpixel((x, y), tint + (a,))
        image.save(os.path.join(out_dir, f"gate_{kind}.png"))
        if os.path.exists(meta):
            with open(meta, encoding="utf-8") as src, open(os.path.join(out_dir, f"gate_{kind}.png.mcmeta"), "w", encoding="utf-8") as dst:
                dst.write(src.read())
        sheet = {"from": [0, 0, 7], "to": [16, 16, 9], "shade": False, "light_emission": 12,
                 "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "south": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}
        write(os.path.join(ASSETS, "models", "block", "outer", f"gate_{kind}.json"),
              {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
               "textures": {"particle": f"villager_life:block/outer/gate_{kind}", "portal": f"villager_life:block/outer/gate_{kind}"},
               "elements": [sheet]})
    variants = {}
    for kind in OUTER:
        variants[f"axis=x,kind={kind}"] = {"model": f"villager_life:block/outer/gate_{kind}"}
        variants[f"axis=z,kind={kind}"] = {"model": f"villager_life:block/outer/gate_{kind}", "y": 90}
    write(os.path.join(STATES, "outer_gate.json"), {"variants": variants})
    # crumbling stone: tuff with a fine crack net; cracked wider at stage 1; nothing at 2
    rng = random.Random(9)
    for stage in (0, 1):
        image = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                base = (104, 106, 96) if (x * 7 + y * 3) % 5 else (88, 90, 82)
                j = (rng.random() - 0.5) * 14
                image.putpixel((x, y), tuple(max(0, min(255, int(c + j))) for c in base) + (255,))
        cracks = [(x, int(7 + 3 * math.sin(x * 0.7))) for x in range(16)] + [(int(4 + 2 * math.sin(y)), y) for y in range(16)]
        if stage:
            cracks += [(int(11 + 2 * math.cos(y * 0.9)), y) for y in range(16)] + [(x, int(12 + math.sin(x))) for x in range(16)]
        for (x, y) in cracks:
            if 0 <= x < 16 and 0 <= y < 16:
                image.putpixel((x, y), (40, 38, 34, 255))
        image.save(os.path.join(out_dir, f"crumble{'_cracked' if stage else ''}.png"))
        write(os.path.join(ASSETS, "models", "block", "outer", f"crumble{'_cracked' if stage else ''}.json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"villager_life:block/outer/crumble{'_cracked' if stage else ''}"}})
    write(os.path.join(ASSETS, "models", "block", "outer", "crumble_gone.json"), {"textures": {"particle": "villager_life:block/outer/crumble"}})
    write(os.path.join(STATES, "outer_crumble.json"), {"variants": {
        "stage=0": {"model": "villager_life:block/outer/crumble"}, "stage=1": {"model": "villager_life:block/outer/crumble_cracked"},
        "stage=2": {"model": "villager_life:block/outer/crumble_gone"}}})


if __name__ == "__main__":
    main()

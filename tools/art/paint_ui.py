"""The mod's window kit (client/ui/Ui): vanilla GUI sprites, one folder per theme with the same names, each with its
scaling in a .mcmeta beside it, so a window of any size comes out of one small picture and a resource pack can
restyle a theme without touching code. Drawn here, no generated images.

textures/gui/sprites/<theme>/   theme = village (oak frame, parchment) | spire (slate, brass, the Core's vein)
    panel        48x48 nine-slice 8   the window
    inset        16x16 nine-slice 3   a sunken card in it
    header       16x14 nine-slice 4   the title band
    slot         18x18                an item slot
    slot_big     26x26                the main slot (a ring)
    button       40x20 nine-slice 4   and button_hover, button_disabled
    tab          24x24 nine-slice 4   and tab_open (joins the panel below it)
    bar          16x7  nine-slice 2   a bar's frame; bar_fill 16x5 stretched
    scroll       12x16 nine-slice 3   a scroll track; scroll_knob 10x15
    arrow        24x16                and arrow_fill (a process arrow and how far it is)
    flame        14x14                and flame_fill
    icon/{coin,shard,seal,star,plus}   9x9
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SPRITES = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life", "textures", "gui", "sprites")


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def jitter(rng, colour, amount):
    return tuple(max(0, min(255, int(c + (rng.random() - 0.5) * amount))) for c in colour)


class Theme:
    def __init__(self, name, frame, frame_light, frame_dark, face, face_dark, face_light, button, button_light, button_dark,
                 disabled, slot, slot_light, slot_dark, accent, accent_light, grain):
        self.__dict__.update(locals())


VILLAGE = Theme("village",
                frame=(122, 84, 48), frame_light=(168, 120, 72), frame_dark=(70, 46, 26),
                face=(232, 218, 188), face_dark=(206, 186, 148), face_light=(246, 236, 212),
                button=(148, 102, 58), button_light=(190, 140, 88), button_dark=(88, 58, 32),
                disabled=(150, 140, 126),
                slot=(150, 124, 92), slot_light=(214, 196, 162), slot_dark=(96, 74, 50),
                accent=(92, 150, 64), accent_light=(170, 214, 120), grain=True)
SPIRE = Theme("spire",
              frame=(184, 142, 70), frame_light=(232, 196, 120), frame_dark=(110, 78, 36),
              face=(52, 56, 66), face_dark=(34, 37, 45), face_light=(78, 84, 96),
              button=(184, 142, 70), button_light=(232, 196, 120), button_dark=(110, 78, 36),
              disabled=(92, 88, 82),
              slot=(40, 42, 50), slot_light=(78, 84, 96), slot_dark=(24, 26, 32),
              accent=(178, 132, 236), accent_light=(232, 210, 255), grain=False)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(image, x, y, colour):
    image.putpixel((x, y), tuple(colour) + (255,))


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(image, x, y, colour)


def bevel(image, x0, y0, x1, y1, light, dark):
    for x in range(x0, x1):
        put(image, x, y0, light)
        put(image, x, y1 - 1, dark)
    for y in range(y0, y1):
        put(image, x0, y, light)
        put(image, x1 - 1, y, dark)


def save(image, theme, name, scaling=None):
    path = os.path.join(SPRITES, theme.name, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    meta = path + ".mcmeta"
    if scaling:
        with open(meta, "w") as f:
            json.dump({"gui": {"scaling": scaling}}, f, indent=2)
            f.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)


def nine(w, h, border):
    return {"type": "nine_slice", "width": w, "height": h, "border": border}


def surface(image, rng, theme, x0, y0, x1, y1, base):
    """The inside of the panel: parchment with fibres (village) or slate in big slabs (spire)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            if theme.grain:
                fibre = 0.5 + 0.5 * math.sin((x * 0.9 + y * 0.15) * 1.7 + rng.random() * 0.6)
                colour = mix(base, theme.face_dark, 0.10 * fibre)
                put(image, x, y, jitter(rng, colour, 8))
            else:
                seam = (y - y0) % 8 == 0 or (x - x0 + ((y - y0) // 8) * 8) % 16 == 0
                put(image, x, y, mix(base, theme.face_dark, 0.45) if seam else jitter(rng, base, 12))


def panel(rng, theme):
    s = 48
    image = new(s, s)
    surface(image, rng, theme, 0, 0, s, s, theme.face)
    if theme.grain:
        # an oak frame five pixels wide, the grain running along each side, a nail in each corner
        for i in range(5):
            for x in range(s):
                for y in (i, s - 1 - i):
                    put(image, x, y, jitter(rng, mix(theme.frame, theme.frame_dark, 0.3 if (x + i * 3) % 7 == 0 else 0.0), 10))
            for y in range(s):
                for x in (i, s - 1 - i):
                    put(image, x, y, jitter(rng, mix(theme.frame, theme.frame_dark, 0.3 if (y + i * 3) % 7 == 0 else 0.0), 10))
        bevel(image, 0, 0, s, s, theme.frame_light, theme.frame_dark)
        bevel(image, 4, 4, s - 4, s - 4, theme.frame_dark, theme.frame_light)
        for cx, cy in ((2, 2), (s - 3, 2), (2, s - 3), (s - 3, s - 3)):
            put(image, cx, cy, (210, 200, 180))
            put(image, cx + 1, cy + 1, (90, 80, 70))
        # a soft shadow of the frame on the parchment
        for i in range(5, 7):
            for x in range(i, s - i):
                put(image, x, i, mix(theme.face, theme.face_dark, 0.6 if i == 5 else 0.3))
            for y in range(i, s - i):
                put(image, i, y, mix(theme.face, theme.face_dark, 0.6 if i == 5 else 0.3))
    else:
        # brass, three pixels, rivets in the corners
        for i in range(3):
            bevel(image, i, i, s - i, s - i, theme.frame_light if i == 0 else theme.frame, theme.frame_dark)
        for cx, cy in ((5, 5), (s - 6, 5), (5, s - 6), (s - 6, s - 6)):
            for dy in range(-1, 2):
                for dx in range(-1, 2):
                    put(image, cx + dx, cy + dy, theme.frame_light if dx + dy < 0 else theme.frame_dark if dx + dy > 0 else theme.frame)
    save(image, theme, "panel", nine(s, s, 8))


def inset(rng, theme):
    image = new(16, 16)
    base = mix(theme.face, theme.face_dark, 0.55)
    for y in range(16):
        for x in range(16):
            put(image, x, y, jitter(rng, base, 6))
    bevel(image, 0, 0, 16, 16, theme.face_dark if theme.grain else theme.slot_dark, theme.face_light)
    bevel(image, 1, 1, 15, 15, mix(base, theme.face_dark, 0.5), base)
    save(image, theme, "inset", nine(16, 16, 3))


def header(rng, theme):
    image = new(16, 14)
    for y in range(14):
        for x in range(16):
            colour = mix(theme.frame, theme.frame_dark, 0.35) if theme.grain else mix(theme.face_dark, (0, 0, 0), 0.25)
            put(image, x, y, jitter(rng, colour, 6))
    for x in range(16):
        put(image, x, 0, theme.frame_light if theme.grain else theme.face_dark)
        put(image, x, 13, theme.frame_dark if theme.grain else theme.accent)
    for y in range(14):
        put(image, 0, y, theme.frame_light if theme.grain else theme.face_dark)
        put(image, 15, y, theme.frame_dark if theme.grain else theme.face_dark)
    save(image, theme, "header", nine(16, 14, 4))


def slot(theme):
    image = new(18, 18)
    fill(image, 0, 0, 18, 18, theme.slot)
    for i in range(18):
        put(image, i, 0, theme.slot_dark)
        put(image, 0, i, theme.slot_dark)
        put(image, i, 17, theme.slot_light)
        put(image, 17, i, theme.slot_light)
    put(image, 0, 17, theme.slot)
    put(image, 17, 0, theme.slot)
    save(image, theme, "slot")


def slot_big(theme):
    image = new(26, 26)
    for y in range(26):
        for x in range(26):
            d = math.hypot(x - 12.5, y - 12.5)
            if d > 12.5:
                continue
            if d > 10.5:
                colour = theme.frame_light if x + y < 26 else theme.frame_dark
            elif d > 9.5:
                colour = theme.frame_dark
            else:
                colour = mix(theme.slot_dark, theme.accent, max(0.0, 0.35 - d / 30.0)) if not theme.grain else mix(theme.slot, theme.slot_dark, d / 12.0)
            put(image, x, y, colour)
    save(image, theme, "slot_big")


def button(theme, name, face, light, dark):
    image = new(40, 20)
    fill(image, 0, 0, 40, 20, face)
    bevel(image, 0, 0, 40, 20, (20, 16, 12), (20, 16, 12))
    bevel(image, 1, 1, 39, 19, light, dark)
    for x in range(2, 38):
        put(image, x, 17, mix(face, dark, 0.5))
    save(image, theme, name, nine(40, 20, 4))


def tab(rng, theme, name, open_):
    image = new(24, 24)
    base = theme.face if open_ else mix(theme.face, theme.face_dark, 0.6)
    for y in range(24):
        for x in range(24):
            put(image, x, y, jitter(rng, base, 6))
    frame = theme.frame if theme.grain else theme.frame
    for i in range(24):
        put(image, i, 0, theme.frame_light)
        put(image, 0, i, theme.frame_light)
        put(image, 23, i, theme.frame_dark)
    for i in range(1, 23):
        put(image, i, 1, frame)
        put(image, 1, i, frame)
        put(image, 22, i, mix(frame, theme.frame_dark, 0.5))
    if not open_:
        for x in range(24):
            put(image, x, 23, theme.frame_dark)
    save(image, theme, name, nine(24, 24, 4))


def bar(theme):
    image = new(16, 7)
    fill(image, 0, 0, 16, 7, theme.slot_dark)
    bevel(image, 0, 0, 16, 7, theme.frame_dark, theme.frame_light if not theme.grain else theme.face_light)
    save(image, theme, "bar", nine(16, 7, 2))
    image = new(16, 5)
    for x in range(16):
        for y in range(5):
            put(image, x, y, theme.accent_light if y == 0 else mix(theme.accent, theme.accent_light, x / 15.0 * 0.5))
    save(image, theme, "bar_fill", {"type": "stretch"})


def scroll(theme):
    image = new(12, 16)
    fill(image, 0, 0, 12, 16, theme.slot_dark)
    bevel(image, 0, 0, 12, 16, theme.slot_dark, theme.slot_light)
    save(image, theme, "scroll", nine(12, 16, 3))
    image = new(10, 15)
    fill(image, 0, 0, 10, 15, theme.button)
    bevel(image, 0, 0, 10, 15, theme.button_light, theme.button_dark)
    for y in (5, 7, 9):
        for x in range(3, 7):
            put(image, x, y, theme.button_dark)
    save(image, theme, "scroll_knob")


def arrow(theme):
    rows = ["..............#.........",
            "..............##........",
            "..............###.......",
            "..............####......",
            "..##################....",
            "..###################...",
            "..####################..",
            "..#####################.",
            "..#####################.",
            "..####################..",
            "..###################...",
            "..##################....",
            "..............####......",
            "..............###.......",
            "..............##........",
            "..............#........."]
    for name, colour in (("arrow", mix(theme.slot, theme.slot_dark, 0.3)), ("arrow_fill", theme.accent)):
        image = new(24, 16)
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c == "#":
                    edge = y in (4, 11) or x == 2
                    put(image, x, y, mix(colour, (255, 255, 255), 0.25) if edge and name == "arrow_fill" else colour)
        save(image, theme, name)
    flame = ["......#.......",
             ".....##.......",
             ".....###......",
             "....####......",
             "....#####.#...",
             "...######.##..",
             "..##########..",
             "..###########.",
             ".############.",
             ".############.",
             ".############.",
             "..##########..",
             "...########...",
             ".....####....."]
    for name, colour in (("flame", mix(theme.slot, theme.slot_dark, 0.3)), ("flame_fill", None)):
        image = new(14, 14)
        for y, row in enumerate(flame):
            for x, c in enumerate(row):
                if c == "#":
                    put(image, x, y, colour if colour else mix((255, 220, 90), (230, 80, 30), y / 13.0))
        save(image, theme, name)


ICONS = {
    "shard": ([".....V...", "....VLV..", "....VLV..", "...VLLV..", "...VLVV..", "..VLVVV..", "..VVVV...", "...VV....", "........."],
              {"V": (178, 132, 236), "L": (232, 210, 255)}),
    "coin": ([".........", "..GGGGG..", ".GLLLLGG.", ".GLGGGLG.", ".GLGLGLG.", ".GLGGGLG.", ".GGLLLGG.", "..GGGGG..", "........."],
             {"G": (196, 110, 60), "L": (236, 170, 110)}),
    "seal": ([".........", "..RRRRR..", ".RRLRLRR.", ".RLLLLLR.", ".RRLLLRR.", ".RRRLRRR.", ".RRRRRRR.", "..RRRRR..", "........."],
             {"R": (170, 40, 44), "L": (226, 120, 110)}),
    "star": (["....Y....", "....Y....", "...YYY...", "YYYYYYYYY", ".YYYYYYY.", "..YYYYY..", "..YY.YY..", ".YY...YY.", "........."],
             {"Y": (250, 214, 90)}),
    "plus": ([".........", "....P....", "....P....", "....P....", ".PPPPPPP.", "....P....", "....P....", "....P....", "........."],
             {}),
}


def icons(theme):
    for name, (rows, colours) in ICONS.items():
        image = new(9, 9)
        palette = dict(colours)
        palette.setdefault("P", theme.frame_dark if theme.grain else theme.frame)
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c in palette:
                    put(image, x, y, palette[c])
        save(image, theme, "icon/" + name)


def main():
    for theme in (VILLAGE, SPIRE):
        rng = random.Random(theme.name)
        panel(rng, theme)
        inset(rng, theme)
        header(rng, theme)
        slot(theme)
        slot_big(theme)
        button(theme, "button", theme.button, theme.button_light, theme.button_dark)
        button(theme, "button_hover", mix(theme.button, theme.button_light, 0.5), (255, 244, 214), theme.button)
        button(theme, "button_disabled", theme.disabled, mix(theme.disabled, (255, 255, 255), 0.2), mix(theme.disabled, (0, 0, 0), 0.3))
        tab(rng, theme, "tab", False)
        tab(rng, theme, "tab_open", True)
        bar(theme)
        scroll(theme)
        arrow(theme)
        icons(theme)


if __name__ == "__main__":
    main()

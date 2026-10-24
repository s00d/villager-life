"""Clothes villagers bring back from the spire (textures/entity/outfit/spire_<name>.png, 64x64, over the villager's own
UV like the other outfits): drawn here, no generated images. The villager model's parts and where their faces lie:

    head 8x10x8 at (0,0): front face x 8..15, y 8..17      robe 8x20x6 at (0,38): top x 6..13 y 38..43,
    body 8x12x6 at (16,20)                                  right x 0..5, front x 6..13, left x 14..19, back x 20..27, y 44..63
    arms 4x8x4 at (44,22) and (40,38) 8x4x4                 legs 4x12x4 at (0,22)

    spire_cloak    the Builders' cloak: deep teal over the back and shoulders, an amethyst vein along its edge
    spire_scarf    the archivist's scarf: a red knitted scarf round the neck, one end down the front
    spire_apron    the clockwork apron: a leather apron with copper buckles, brass goggles on the brow
    spire_sash     the gardener's sash: a green sash across the chest with pink blossoms
    spire_coat     the deep coat: a long sea-blue coat with silver buttons
    spire_mantle   the star mantle: a night-purple mantle with stars, a silver circlet
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "neoforge", "src", "main", "resources", "assets", "villager_life", "textures", "entity", "outfit")


def put(image, x, y, colour):
    if 0 <= x < 64 and 0 <= y < 64:
        image.putpixel((x, y), colour + (255,) if len(colour) == 3 else colour)


def shade(colour, amount):
    return tuple(max(0, min(255, int(c * amount))) for c in colour)


def cloth(image, xs, ys, base, rng, wear=0.12):
    for y in ys:
        for x in xs:
            put(image, x, y, shade(base, 1.0 + (rng.random() - 0.5) * wear))


def robe_faces():
    return {"right": range(0, 6), "front": range(6, 14), "left": range(14, 20), "back": range(20, 28)}


def cloak(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    teal, vein = (34, 92, 98), (176, 132, 228)
    faces = robe_faces()
    cloth(image, faces["back"], range(44, 63), teal, rng)
    cloth(image, faces["right"], range(44, 61), teal, rng)
    cloth(image, faces["left"], range(44, 61), teal, rng)
    cloth(image, range(6, 14), range(38, 44), teal, rng)
    for y in range(44, 62):
        put(image, 6, y, teal)
        put(image, 13, y, teal)
    # the vein: along the hem and up the front edges
    for x in list(faces["back"]) + list(faces["right"]) + list(faces["left"]):
        put(image, x, 62 if x in faces["back"] else 60, vein)
    for y in range(44, 62):
        put(image, 7, y, vein if y % 3 else shade(vein, 1.2))
        put(image, 12, y, vein if y % 3 else shade(vein, 1.2))
    return image


def scarf(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    red, dark = (172, 46, 40), (120, 30, 28)
    cloth(image, range(6, 14), range(38, 44), red, rng, 0.2)
    for x in range(0, 28):
        for y in (44, 45, 46):
            put(image, x, y, red if (x + y) % 2 else dark)
    for y in range(47, 59):
        for x in (8, 9, 10):
            put(image, x, y, red if (x + y) % 2 else dark)
    for x in (8, 9, 10):
        put(image, x, 59, (230, 210, 170))
        put(image, x, 60, (230, 210, 170) if x != 9 else (0, 0, 0, 0))
    return image


def apron(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    leather, copper, brass, glass = (112, 74, 44), (196, 110, 70), (200, 160, 60), (120, 200, 210)
    cloth(image, range(7, 13), range(46, 62), leather, rng, 0.18)
    for x in range(6, 14):
        put(image, x, 46, shade(leather, 0.8))
    put(image, 7, 47, copper)
    put(image, 12, 47, copper)
    for y in range(52, 55):
        for x in range(8, 12):
            put(image, x, y, shade(leather, 0.75) if y == 52 else shade(leather, 0.9))
    # goggles on the brow of the head's front face, the strap round its sides
    for x in range(8, 16):
        put(image, x, 9, brass)
    for x in (9, 10, 13, 14):
        put(image, x, 10, glass)
    for x in (0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31):
        put(image, x, 9, shade(leather, 0.6))
    return image


def sash(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    green, blossom, centre = (72, 132, 60), (236, 150, 190), (250, 220, 120)
    for i in range(0, 14):
        x = 6 + i * 8 // 14
        y = 44 + i
        for dx in (0, 1, 2):
            put(image, x + dx, y, shade(green, 1.0 + (rng.random() - 0.5) * 0.15))
        bx = 27 - i * 8 // 14
        for dx in (0, 1, 2):
            put(image, bx - dx, y, shade(green, 0.9))
    for (x, y) in ((7, 46), (9, 50), (11, 54), (12, 57), (26, 47), (24, 52), (22, 56)):
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            put(image, x + dx, y + dy, blossom)
        put(image, x, y, centre)
    return image


def coat(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    blue, silver = (38, 70, 132), (210, 214, 224)
    for face in robe_faces().values():
        cloth(image, face, range(44, 64), blue, rng, 0.14)
    cloth(image, range(6, 14), range(38, 44), shade(blue, 0.9), rng)
    for y in range(46, 62, 3):
        put(image, 9, y, silver)
        put(image, 10, y, silver)
    for x in range(0, 28):
        put(image, x, 63, shade(blue, 0.7))
    for y in range(44, 64):
        put(image, 6, y, shade(blue, 0.8))
        put(image, 13, y, shade(blue, 0.8))
    return image


def mantle(rng):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    night, star, silver = (58, 38, 98), (250, 236, 170), (214, 220, 232)
    faces = robe_faces()
    for name in ("back", "right", "left"):
        cloth(image, faces[name], range(44, 58), night, rng)
    cloth(image, range(6, 14), range(38, 44), night, rng)
    for x in range(6, 14):
        put(image, x, 44, night)
        put(image, x, 45, night)
    for _ in range(14):
        x = rng.choice(list(faces["back"]) + list(faces["right"]) + list(faces["left"]))
        put(image, x, rng.randrange(45, 57), star)
    # the circlet round the head
    for x in range(0, 32):
        put(image, x, 8, silver)
    put(image, 11, 8, star)
    put(image, 12, 8, star)
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, painter in (("cloak", cloak), ("scarf", scarf), ("apron", apron), ("sash", sash), ("coat", coat), ("mantle", mantle)):
        painter(random.Random(name)).save(os.path.join(OUT, "spire_" + name + ".png"))


if __name__ == "__main__":
    main()

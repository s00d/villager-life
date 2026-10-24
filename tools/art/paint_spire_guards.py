"""The spire guards' textures (procedural pixel art, no generation): for every geo/spire/<model>.geo.json three
sheets on its box UVs, painted by the bone's name —
  <model>.png       the whole guard in neutral colours (the hurt flash shows on it),
  <model>_tint.png  only its body, light grey: the renderer tints it with the genome's colour over the first,
  <model>_glow.png  only its eyes, cores and gems ("eye", "core", "gem" in the bone's name), drawn full bright.
Bones named plate*/shield* are bronze fittings, left untinted. Run after changing a model's cubes or UVs."""
import json, pathlib, random
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/villager_life"
GEO = ROOT / "geo/spire"
OUT = ROOT / "textures/entity/spire"
CLEAR = (0, 0, 0, 0)
BRONZE, BRONZE_DK, BRONZE_HI = (150, 112, 62), (98, 70, 40), (204, 162, 96)
SOCKET = (34, 32, 44)

# how each model's body is worked: stone courses, riveted plates, leaf veins, wing eyes, speckles, scales, runes
PATTERN = {"sentinel": "courses", "hound": "plates", "vine": "veins", "moth": "eyes", "ray": "speckle",
           "wisp": "speckle", "fish": "scales", "gatekeeper": "courses", "archivist": "runes", "warden": "plates",
           "gardener": "bark", "serpent": "scales", "colossus": "frost", "spore_queen": "caps", "hive_master": "stripes",
           "stargazer": "robe", "mind": "facets", "mason": "courses", "mason_alone": "courses", "surveyor": "robe",
           "surveyor_alone": "robe", "root_wyrm": "plates", "wyrm_segment": "plates", "binder": "runes", "chronarch": "plates",
           "heartwood": "bark", "frost_mirror": "frost", "mycel_hydra": "caps", "hydra_head": "caps", "honey_golem": "stripes",
           "gravitor": "facets", "planet": "speckle", "reflection": "facets"}


def material(bone):
    if any(word in bone for word in ("eye", "core", "gem")):
        return "glow"
    if bone.startswith("plate") or bone.startswith("shield"):
        return "metal"
    return "body"


def faces(cube):
    """The six face rectangles (x, y, w, h, name) of a box-UV cube."""
    u, v = cube["uv"]
    w, h, d = (int(-(-s // 1)) for s in cube["size"])
    return [(u + d, v, w, d, "top"), (u + d + w, v, w, d, "bottom"), (u, v + d, d, h, "side"),
            (u + d, v + d, w, h, "front"), (u + d + w, v + d, d, h, "side"), (u + 2 * d + w, v + d, w, h, "back")]


def body_value(pattern, rnd, x, y, w, h, face):
    """A grey 0..255 for a body pixel at (x, y) of a face w by h."""
    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
    value = 196 + rnd.randint(-10, 10)
    if face == "bottom":
        value -= 34
    elif face == "top":
        value += 14
    if pattern == "courses" and (y % 4 == 3 or (x + (y // 4) * 3) % 6 == 5):
        value -= 40
    elif pattern == "plates" and (y % 5 == 4 or x % 7 == 6):
        value -= 46
    elif pattern == "plates" and y % 5 == 1 and x % 7 == 1:
        value += 40
    elif pattern == "veins" and x == w // 2:
        value -= 36
    elif pattern == "veins" and abs(x - w // 2) == (y % 4) + 1:
        value -= 22
    elif pattern == "eyes" and face in ("top", "bottom") and w > 6:
        r = ((x - w * 0.6) ** 2 + (y - h * 0.5) ** 2) ** 0.5
        value += 50 if r < 1.2 else -50 if r < 2.4 else 20 if r < 3.4 else 0
    elif pattern == "speckle" and rnd.random() < 0.12:
        value += 44
    elif pattern == "scales" and (x + (y % 2)) % 2 == 0 and y % 2 == 0:
        value -= 30
    elif pattern == "runes" and face == "front" and 1 < x < w - 2 and y % 3 == 1 and rnd.random() < 0.6:
        value -= 60
    elif pattern == "bark" and (x % 3 == 0 or (y + x // 3) % 7 == 0):
        value -= 34 if x % 3 == 0 else 18
    elif pattern == "frost" and (x + y) % 5 == 0:
        value += 36
    elif pattern == "frost" and (x - y) % 7 == 0:
        value -= 26
    elif pattern == "caps" and face in ("top", "side", "front", "back") and ((x * 7 + y * 13) % 17 < 2):
        value += 54
    elif pattern == "stripes" and (y // 3) % 2 == 1:
        value -= 70
    elif pattern == "robe" and (x % 4 == 0 or y % 9 == 8):
        value -= 28
    elif pattern == "robe" and rnd.random() < 0.04:
        value += 50
    elif pattern == "facets" and (abs(x - w / 2) + abs(y - h / 2)) % 4 < 1:
        value += 40
    if edge:
        value -= 30
    return max(40, min(255, value))


def paint(name):
    model = json.loads((GEO / f"{name}.geo.json").read_text())["minecraft:geometry"][0]
    size = (model["description"]["texture_width"], model["description"]["texture_height"])
    base, tint, glow = (Image.new("RGBA", size, CLEAR) for _ in range(3))
    rnd = random.Random(name)
    for bone in model["bones"]:
        kind = material(bone["name"])
        for cube in bone.get("cubes", []):
            for fx, fy, w, h, face in faces(cube):
                for y in range(h):
                    for x in range(w):
                        at = (fx + x, fy + y)
                        edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
                        if kind == "body":
                            g = body_value(PATTERN[name], rnd, x, y, w, h, face)
                            base.putpixel(at, (g * 3 // 4, g * 3 // 4, g * 4 // 5, 255))
                            tint.putpixel(at, (g, g, g, 255))
                        elif kind == "metal":
                            rivet = (x % 4 == 1 and y % 4 == 1) and not edge
                            base.putpixel(at, (*(BRONZE_DK if edge else BRONZE_HI if rivet else BRONZE), 255))
                        else:
                            middle = 1.0 - (abs(x - (w - 1) / 2) / max(1, w) + abs(y - (h - 1) / 2) / max(1, h))
                            g = int(170 + 85 * middle)
                            base.putpixel(at, (*SOCKET, 255))
                            glow.putpixel(at, (g, g, g, 255))
    OUT.mkdir(parents=True, exist_ok=True)
    base.save(OUT / f"{name}.png")
    tint.save(OUT / f"{name}_tint.png")
    glow.save(OUT / f"{name}_glow.png")


if __name__ == "__main__":
    for geo in sorted(GEO.glob("*.geo.json")):
        paint(geo.name.removesuffix(".geo.json"))
        print("painted", geo.name)

"""Mod icon from the guide's item sprite, drawn without generative AI (Modrinth forbids AI icons).

Writes assets/villager_life/icon.png (512) and docs/media/icon-400.png (CurseForge avatar size).
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "neoforge/src/main/resources/assets/villager_life"
SPRITE = ASSETS / "textures/item/handbook.png"

GRASS = (93, 140, 72)
GRASS_DARK = (64, 104, 52)
DIRT = (121, 85, 58)
DIRT_DARK = (94, 64, 42)


def icon(size: int) -> Image.Image:
    px = size // 32  # a 32x32 grid of blocks, like a block face
    img = Image.new("RGBA", (size, size), DIRT)
    d = ImageDraw.Draw(img)
    for y in range(32):
        for x in range(32):
            h = (x * 73856093 ^ y * 19349663) & 7
            if y < 9 or (y == 9 and h < 5):
                c = GRASS_DARK if h < 2 else GRASS
            else:
                c = DIRT_DARK if h < 2 else DIRT
            d.rectangle((x * px, y * px, (x + 1) * px - 1, (y + 1) * px - 1), fill=c)
    sprite = Image.open(SPRITE).convert("RGBA")
    scale = (size * 3 // 4) // 16
    book = sprite.resize((16 * scale, 16 * scale), Image.NEAREST)
    shadow = Image.new("RGBA", book.size, (0, 0, 0, 0))
    shadow.putalpha(book.getchannel("A").point(lambda a: 110 if a else 0))
    at = ((size - book.width) // 2, (size - book.height) // 2)
    img.alpha_composite(shadow, (at[0] + scale, at[1] + scale))
    img.alpha_composite(book, at)
    return img


if __name__ == "__main__":
    icon(512).save(ASSETS / "icon.png")
    media = ROOT / "docs/media"
    media.mkdir(parents=True, exist_ok=True)
    icon(512).resize((400, 400), Image.NEAREST).save(media / "icon-400.png")

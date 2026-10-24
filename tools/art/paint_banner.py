"""README / publication banner from an in-game screenshot, the icon and the title, drawn without generative AI.

Writes docs/media/banner.png (1600x500) from docs/media/gallery/01-town-and-spire.jpg: scaled up and shifted so the
spire stands right of the title.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
MEDIA = ROOT / "docs/media"
FONT = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
W, H = 1600, 500


def main() -> None:
    shot = Image.open(MEDIA / "gallery/01-town-and-spire.jpg").convert("RGB")
    wide = 2600
    shot = shot.resize((wide, round(shot.height * wide / shot.width)), Image.LANCZOS)
    left, top = 150, 360
    img = shot.crop((left, top, left + W, top + H)).convert("RGBA")

    # a dark band on the left, fading out, so the title reads over the village
    shade = Image.new("L", (W, H))
    d = ImageDraw.Draw(shade)
    for x in range(W):
        d.line([(x, 0), (x, H)], fill=max(0, min(215, int(330 - x * 0.27))))
    img.alpha_composite(Image.merge("RGBA", (*[Image.new("L", (W, H), 12)] * 3, shade)))

    icon = Image.open(MEDIA / "icon-400.png").convert("RGBA").resize((200, 200), Image.NEAREST)
    img.alpha_composite(icon, (70, (H - 200) // 2))

    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(FONT, 96)
    sub = ImageFont.truetype(FONT, 34)
    x = 310
    small = ImageFont.truetype(FONT, 26)
    line = "NeoForge  ·  Minecraft 1.21.1 – 26.3  ·  English and Russian voices"
    for text, font, y in (("Villager Life", title, 150), ("Villagers who live, build and talk", sub, 275), (line, small, 330)):
        glow = Image.new("RGBA", (W, H))
        ImageDraw.Draw(glow).text((x + 4, y + 4), text, font=font, fill=(0, 0, 0, 200))
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(4)))
        d.text((x, y), text, font=font, fill=(255, 255, 255, 255))
    img.convert("RGB").save(MEDIA / "banner.png")


if __name__ == "__main__":
    main()

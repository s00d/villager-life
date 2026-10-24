"""The family nameplate's face (hand-drawn pixel art, no generation): textures/block/town/house_sign.png, 16x16.
The board the models use is x 1-14, y 4-11 (models/block/town/house_sign*.json): a dark frame, light planks with
grain, two nail heads; the names are drawn over it by client/HouseSignRenderer."""
import pathlib
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / "neoforge/src/main/resources/assets/villager_life/textures/block/town"
FRAME, FRAME_HI = (66, 43, 24, 255), (92, 62, 36, 255)
WOOD, WOOD_DK, WOOD_HI = (196, 156, 104, 255), (176, 136, 88, 255), (212, 176, 124, 255)
NAIL = (60, 60, 64, 255)


def main():
    img = Image.new("RGBA", (16, 16), WOOD)
    for y in range(16):
        for x in range(16):
            # grain: long streaks along the board, a little different on each row
            streak = (x * 3 + y * 7 + (x // 5) * y) % 11
            img.putpixel((x, y), WOOD_DK if streak == 0 else WOOD_HI if streak == 5 else WOOD)
    for x in range(1, 15):
        img.putpixel((x, 4), FRAME_HI)
        img.putpixel((x, 11), FRAME)
    for y in range(4, 12):
        img.putpixel((1, y), FRAME_HI)
        img.putpixel((14, y), FRAME)
    for x in (2, 13):
        img.putpixel((x, 5), NAIL)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "house_sign.png")


if __name__ == "__main__":
    main()

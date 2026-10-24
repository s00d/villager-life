#!/usr/bin/env python3
"""Checks the guide book as players will see it: assets/villager_life/guide/{en_us,ru_ru}.json.

  python3 tools/guide/check_guide.py

The books are edited by hand. This only reads them: both languages have the same chapters, entries and pages, every
recipe, schematic and picture exists, every link leads to an entry, and every page fits — laid out like GuideScreen
with the widths of the vanilla font (font_widths.json, read from ascii.png and nonlatin_european.png of the game).
Exit 1 on a problem.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
NS = "villager_life"
GUIDE = ROOT / f"neoforge/src/main/resources/assets/{NS}/guide"


def text_of(value):
    return "\n".join(value) if isinstance(value, list) else (value or "")


WIDTHS = json.loads((pathlib.Path(__file__).parent / "font_widths.json").read_text(encoding="utf-8"))
PAGE_W, PAGE_H = 116, 145
STRUCTURE = 84


def runs(text):
    """(chars, bold) runs of a line of the guide's markup."""
    out, bold, i, run = [], False, 0, ""
    while i < len(text):
        two = text[i:i + 2]
        if two in ("**", "[[", "]]", "//"):
            out.append((run, bold))
            run = ""
            if two == "**":
                bold = not bold
            i += 2
            continue
        run += text[i]
        i += 1
    out.append((run, bold))
    return out


def width(word, bold):
    return sum(WIDTHS.get(c, 6) + (1 if bold else 0) for c in word)


def wrap(text, limit=PAGE_W):
    """Heights of a text as GuideScreen draws it, by paragraph: each a list of 10 per line, or [4] for a blank line."""
    blocks = []
    for paragraph in text.split("\n"):
        if not paragraph.strip():
            blocks.append([4])
            continue
        bullet = paragraph.startswith("- ")
        room = limit - (8 if bullet else 0)
        words = []
        for chars, bold in runs(paragraph[2:] if bullet else paragraph):
            for k, word in enumerate(chars.split(" ")):
                if k > 0:
                    words.append((" ", bold))
                if word:
                    words.append((word, bold))
        used, count = 0, 1
        for word, bold in words:
            w = width(word, bold)
            if word == " ":
                used += w
                continue
            if used + w > room and used > 0:
                count += 1
                used = 0
            used += w
        blocks.append([10] * count)
    return blocks


def heights(text):
    return [h for block in wrap(text) for h in block]


def ribbon_height(title, scale=1.0):
    """A title on its ribbon, as GuideScreen.ribbon draws it, with the gap under it."""
    return 7 + round(10 * scale) * lines_of(title, (PAGE_W - 6) / scale, True) + 2


def lines_of(text, limit, bold=False, scale=1.0):
    words, used, count = text.split(" "), 0, 1
    for word in words:
        w = width(word, bold) * scale
        if used + w > limit and used > 0:
            count, used = count + 1, 0
        used += w + width(" ", bold) * scale
    return count


def text_pages(page, room):
    """A text page as GuideScreen.textPages breaks it: the line heights of each page it takes."""
    blocks = ([[10], [4]] if page.get("title") else []) + wrap(text_of(page.get("text")))
    pages, used, limit = [[]], 0, room
    for block in blocks:
        if used + sum(block) > limit and pages[-1] and sum(block) <= PAGE_H:
            pages.append([])
            used, limit = 0, PAGE_H
            if block == [4]:
                continue
        for h in block:
            if used + h > limit and pages[-1]:
                pages.append([])
                used, limit = 0, PAGE_H
            pages[-1].append(h)
            used += h
    return pages


def spreads(entry):
    """How many spreads an entry takes in the book."""
    count = 0
    for page in entry["pages"]:
        room = PAGE_H - ribbon_height(entry["title"]) - 5 if count == 0 else PAGE_H
        count += len(text_pages(page, room)) if page["type"] == "text" else 1
    return max(1, (count + 1) // 2)


def fit(book, lang):
    problems = []
    # the landing page: the title's ribbon, subtitle, sprig, the landing text above the progress line
    landing = ribbon_height(book["title"], 1.3) + 4 + 10 * lines_of(book["subtitle"], PAGE_W) + 11
    landing += sum(heights(text_of(book["landing"])))
    if landing > PAGE_H - 18:
        problems.append(f"{lang} landing page: {landing} px of {PAGE_H - 18}, cut off")
    for category in book["categories"]:
        if lines_of(category["title"], PAGE_W - 6, True) > 2:
            problems.append(f"{lang} chapter title {category['title']!r}: more than two lines on its ribbon")
        description = 28 + ribbon_height(category["title"]) + 6 + sum(heights(text_of(category["description"])))
        if description > PAGE_H:
            problems.append(f"{lang} chapter {category['id']}: description {description} px of {PAGE_H}, cut off")
    for category in book["categories"]:
        for title in [category["title"]] + [entry["title"] for entry in category["entries"]]:
            if lines_of(title, PAGE_W - 30) > 2:
                problems.append(f"{lang} list title {title!r}: more than two lines")
    for category in book["categories"]:
        for item in category["entries"]:
            if lines_of(item["title"], PAGE_W - 6, True) > 2:
                problems.append(f"{lang} entry title {item['title']!r}: more than two lines on its ribbon")
            first = True
            for number, page in enumerate(item["pages"], 1):
                room = (PAGE_H - ribbon_height(item["title"]) - 5) if first else PAGE_H
                first = False
                kind = page["type"]
                body = sum(heights(text_of(page.get("text"))))
                head = 12 if page.get("title") or kind == "crafting" else 0
                where = f"{lang} {item['id']} page {number} ({kind})"
                if kind == "text":
                    pages = text_pages(page, room)
                    if len(pages) > 1 and sum(1 for h in pages[-1] if h == 10) <= 2:
                        problems.append(f"{where}: {len(pages)} pages, the last one only {sum(1 for h in pages[-1] if h == 10)} lines")
                    continue
                used = {"spotlight": 44 + 10 + 3, "crafting": 60, "items": 4 + 19 * ((len(page.get("items", [])) + 5) // 6),
                        "structure": STRUCTURE + 5, "screen": 24, "links": 11 + 18 * len(page.get("links", [])),
                        "image": 5 + round(PAGE_W * page.get("height", 144) / page.get("width", 256))}.get(kind, 0)
                if head + used + body > room + 1:
                    problems.append(f"{where}: {head + used + body} px of {room}, cut off")
    return problems


def shape(book):
    out = []
    for category in book["categories"]:
        out.append(("category", category["id"], category["icon"]))
        for entry in category["entries"]:
            out.append(("entry", entry["id"], entry["icon"]))
            for page in entry["pages"]:
                out.append(("page", page["type"], page.get("item"), page.get("recipe"), page.get("image"),
                            page.get("schematic"), tuple(page.get("items", [])), tuple(page.get("links", []))))
    return out


def resources(book):
    problems = []
    ids = {entry["id"] for category in book["categories"] for entry in category["entries"]}
    for category in book["categories"]:
        for entry in category["entries"]:
            for page in entry["pages"]:
                where = f"{entry['id']} ({page['type']})"
                for target in page.get("links", []):
                    if target not in ids:
                        problems.append(f"{where}: link to unknown entry {target}")
                if "recipe" in page:
                    if not (ROOT / f"neoforge/src/main/resources/data/{NS}/recipe/{page['recipe'].split(':', 1)[1]}.json").exists():
                        problems.append(f"{where}: no recipe {page['recipe']}")
                if "schematic" in page:
                    if not page["schematic"].startswith("mega:") and not (ROOT / f"neoforge/src/main/resources/data/{NS}/structure/village/{page['schematic']}.nbt").exists():
                        problems.append(f"{where}: no schematic {page['schematic']}")
                if "image" in page:
                    if not (ROOT / f"neoforge/src/main/resources/assets/{NS}" / page["image"].split(":", 1)[1]).exists():
                        problems.append(f"{where}: no picture {page['image']}")
    return problems


def main():
    books = {lang: json.loads((GUIDE / f"{lang}.json").read_text(encoding="utf-8")) for lang in ("en_us", "ru_ru")}
    problems = []
    if shape(books["en_us"]) != shape(books["ru_ru"]):
        problems.append("en_us and ru_ru differ in chapters, entries, pages, items, recipes, pictures or links")
    problems += resources(books["en_us"])
    for lang, book in books.items():
        problems += fit(book, lang[:2])
    for problem in problems:
        print("problem:", problem)
    print(f"check_guide: {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

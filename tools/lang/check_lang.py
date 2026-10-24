#!/usr/bin/env python3
"""Both languages complete: every key of en_us.json is in ru_ru.json and back (the same for each
tools/lang/*.lang.json), no Cyrillic in English, no English left in Russian (terms that stay as they are —
KEEP — aside). Also:
- every key the code names, whole (translatable("a.b.c")) or built of a known family (a board build's label, a
  chronicle line, a tier's or a theme's name, a tab, an anvil's outcome) — the families the mod builds keys of
  (translatable("prefix." + ...)) must have keys at all;
- no player-facing English hard-coded in Component.literal (debug commands and numbers aside);
- the spire's texts (spire/notes, spire/fragments) in both languages, the same entries.
Prints the problems, exit code 1 if any.

  python3 tools/lang/check_lang.py
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LANG = ROOT / "neoforge/src/main/resources/assets/villager_life/lang"
CYRILLIC = re.compile(r"[А-Яа-яЁё]")
# words that stay Latin in Russian text: names, mods, units, formats
KEEP = {"JEI", "EMI", "Villager", "Life", "Tocsin", "Mekanism", "TACZ", "Minecraft", "NeoForge", "MCA", "Create",
        "MineColonies", "Millénaire", "OK", "Shift", "Ctrl", "Alt", "LMB", "RMB", "F3", "T", "HP", "XP", "ID", "NBT",
        "UUID", "GUI", "TTS", "EN", "RU", "Fish", "S2", "s00d", "MIT"}
WORD = re.compile(r"[A-Za-z]{2,}")
FORMAT = re.compile(r"%(\d+\$)?[sd]|§.|\[[a-z ,\-]+\]")


def latin_words(text: str) -> list[str]:
    return [w for w in WORD.findall(FORMAT.sub(" ", text)) if w not in KEEP]


def check(name: str, en: dict, ru: dict) -> list[str]:
    out = [f"{name}: {k} only in English" for k in sorted(set(en) - set(ru))]
    out += [f"{name}: {k} only in Russian" for k in sorted(set(ru) - set(en))]
    for k, v in en.items():
        if isinstance(v, str) and CYRILLIC.search(v):
            out.append(f"{name}: {k} English text has Cyrillic: {v!r}")
    for k, v in ru.items():
        if not isinstance(v, str) or not v.strip():
            continue
        # English words and not a letter of Russian: left untranslated (mod names inside Russian text are fine)
        if not CYRILLIC.search(v) and latin_words(v):
            out.append(f"{name}: {k} Russian text is English: {v!r}")
    return out


def main() -> int:
    problems = []
    en = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    ru = json.loads((LANG / "ru_ru.json").read_text(encoding="utf-8"))
    problems += check("lang", en, ru)
    for part in sorted((ROOT / "tools/lang").glob("*.lang.json")):
        data = json.loads(part.read_text(encoding="utf-8"))
        problems += check(part.name, data.get("en", {}), data.get("ru", {}))
    # keys the mod names: in code (whole literal keys), sound subtitles, data files (advancements, songs)
    assets = LANG.parent
    used = []
    for path in (ROOT / "neoforge/src/main/java").rglob("*.java"):
        used += [(k, path.name) for k in re.findall(r'translatable(?:WithFallback)?\(\s*"([^"]+)"', path.read_text(encoding="utf-8"))
                 if not k.endswith(".")]
    sounds = json.loads((assets / "sounds.json").read_text(encoding="utf-8"))
    used += [(v["subtitle"], "sounds.json") for v in sounds.values() if "subtitle" in v]
    for path in (ROOT / "neoforge/src/main/resources/data").rglob("*.json"):
        used += [(k, path.name) for k in re.findall(r'"translate"\s*:\s*"([^"]+)"', path.read_text(encoding="utf-8"))]
    for key, where in used:
        if key.split(".")[0] in ("gui", "options", "container", "item", "block", "entity") and "villager_life" not in key:
            continue  # the game's own
        for name, table in (("en_us", en), ("ru_ru", ru)):
            if key not in table:
                problems.append(f"{where}: {key} missing in {name}.json")
    problems += families(en, ru)
    problems += literals()
    problems += spire_texts()
    problems += advancements(en)
    for line in problems:
        print(line)
    print(f"check_lang: {len(problems)} problems; {len(en)} keys")
    return 1 if problems else 0


JAVA = ROOT / "neoforge/src/main/java"


def sources() -> dict:
    return {path: path.read_text(encoding="utf-8") for path in JAVA.rglob("*.java")}


def need(keys, en, ru, where) -> list[str]:
    out = []
    for key in sorted(set(keys)):
        for name, table in (("en_us", en), ("ru_ru", ru)):
            if key not in table:
                out.append(f"{where}: {key} missing in {name}.json")
    return out


def families(en: dict, ru: dict) -> list[str]:
    """Keys the code builds of a prefix and a name: each family known, each name of it translated."""
    code = sources()
    text = "\n".join(code.values())
    out = []
    # a family with no key at all (translatable("prefix." + ...))
    for prefix in sorted(set(re.findall(r'translatable(?:WithFallback)?\(\s*"([a-z_.]+\.)"\s*\+', text))):
        if ".minecraft." in "." + prefix or prefix.startswith(("entity.minecraft.", "item.minecraft.", "block.minecraft.")):
            continue  # the game's own
        if not any(k.startswith(prefix) for k in en):
            out.append(f"code: no key of the family {prefix}* in en_us.json")
    # the board's builds: every label a stretch is queued under, and the houses'
    labels = re.findall(r'\.queue(?:For)?\([^;]*?,\s*"([a-z_]+)"\s*\)', text)
    labels += ["hall", "stall", "mending", "house"]
    out += need(["board.villager_life.build." + label for label in labels], en, ru, "board builds")
    # the chronicle's lines
    kinds = re.findall(r'Chronicle\.note\([^,]+,[^,]+,\s*"([a-z_]+)"', text)
    kinds += re.findall(r'Chronicle\.note\([^;]*?\?\s*"([a-z_]+)"', text) + re.findall(r':\s*"([a-z_]+)"\s*(?:,|\))', "")
    out += need(["chronicle.villager_life." + kind for kind in kinds], en, ru, "chronicle")
    # a decor's message to the player: say(player, "key", ...) is message.villager_life.<key>
    decor = code.get(JAVA / "com/vnap/block/decor/DecorMechanics.java", "")
    out += need(["message.villager_life." + key for key in re.findall(r'\bsay\(player,\s*"([a-z_.]+)"', decor)], en, ru, "decor messages")
    # the tiers and the spire's themes
    tiers = re.findall(r'\b([A-Z]+)\(\d+\)', code.get(JAVA / "com/vnap/spire/gear/GearTier.java", ""))
    out += need(["spire_gear.villager_life.property." + t.lower() for t in tiers], en, ru, "gear tiers")
    themes = re.search(r'THEMES = List\.of\(([^;]+)\);', code.get(JAVA / "com/vnap/spire/Genome.java", ""))
    if themes:
        out += need(["spire.villager_life.theme." + t for t in re.findall(r'"([a-z_]+)"', themes.group(1))], en, ru, "spire themes")
    # tabs
    for screen, prefix in (("com/vnap/client/ShopScreen.java", "vnap.shop.tab."), ("com/vnap/client/NoticeBoardScreen.java", "board.villager_life.tab.")):
        tabs = re.search(r'TABS = \{([^}]+)\}', code.get(JAVA / screen, ""))
        if tabs:
            names = re.findall(r'"([a-z_]+)"', tabs.group(1))
            if screen.endswith("NoticeBoardScreen.java"):
                names = [n for n in names if n not in ("chronicle", "flag")]  # icon tabs, no label
            out += need([prefix + n for n in names], en, ru, screen.split("/")[-1])
    # the anvil's outcomes said to the player (DONE and NOTHING say nothing)
    outcomes = re.search(r'enum Outcome \{([^}]+)\}', code.get(JAVA / "com/vnap/spire/gear/CoreAnvilMenu.java", ""))
    if outcomes:
        names = [n.strip().lower() for n in outcomes.group(1).split(",") if n.strip() not in ("DONE", "NOTHING")]
        out += need(["spire_gear.villager_life.anvil." + n for n in names], en, ru, "anvil outcomes")
    out += need(["spire_gear.villager_life.anvil.slot." + str(i) for i in range(3)], en, ru, "anvil slots")
    return out


# where hard-coded English is fine: debug commands, the log, tests of the dev tools
LITERAL_FREE = ("command/", "Command.java", "DevChecks.java", "/gametest/")


def advancements(en: dict) -> list[str]:
    """Every advancement the mod awards by hand (minecraft:impossible) is awarded somewhere in the code, and named."""
    out = []
    code = "\n".join(sources().values())
    base = ROOT / "neoforge/src/main/resources/data/villager_life/advancement"
    for path in sorted(base.rglob("*.json")):
        rid = path.relative_to(base).with_suffix("").as_posix()
        data = json.loads(path.read_text(encoding="utf-8"))
        key = rid.replace("/", ".")
        for part in ("title", "description"):
            if f"advancements.villager_life.{key}.{part}" not in en:
                out.append(f"advancement {rid}: no {part}")
        triggers = [c.get("trigger") for c in data.get("criteria", {}).values()]
        if "minecraft:impossible" in triggers and f'"{rid}"' not in code:
            out.append(f"advancement {rid}: nothing in the code awards it")
    return out


def literals() -> list[str]:
    out = []
    for path, src in sources().items():
        rel = str(path.relative_to(JAVA))
        if any(part in rel for part in LITERAL_FREE):
            continue
        for match in re.finditer(r'Component\.literal\(\s*"([^"]*)"', src):
            words = [w for w in WORD.findall(FORMAT.sub(" ", match.group(1))) if w not in KEEP]
            if words:
                line = src.count("\n", 0, match.start()) + 1
                out.append(f"{rel}:{line}: player-facing English in Component.literal: {match.group(1)!r}")
    return out


def spire_texts() -> list[str]:
    out = []
    base = LANG.parent / "spire"
    for kind in sorted(p.name for p in base.iterdir() if p.is_dir()):
        en = json.loads((base / kind / "en_us.json").read_text(encoding="utf-8"))
        ru_path = base / kind / "ru_ru.json"
        if not ru_path.exists():
            out.append(f"spire/{kind}: no ru_ru.json")
            continue
        ru = json.loads(ru_path.read_text(encoding="utf-8"))
        out += shape(f"spire/{kind}", en, ru)
    return out


def shape(where: str, en, ru) -> list[str]:
    """The same entries in both: dict keys and list lengths alike, no empty or untranslated Russian string."""
    if isinstance(en, dict):
        if not isinstance(ru, dict):
            return [f"{where}: not an object in ru_ru"]
        out = [f"{where}.{k} only in English" for k in sorted(set(en) - set(ru))]
        out += [f"{where}.{k} only in Russian" for k in sorted(set(ru) - set(en))]
        for k in set(en) & set(ru):
            out += shape(f"{where}.{k}", en[k], ru[k])
        return out
    if isinstance(en, list):
        if not isinstance(ru, list) or len(ru) != len(en):
            return [f"{where}: {len(en)} entries in English, {len(ru) if isinstance(ru, list) else '?'} in Russian"]
        out = []
        for i, (a, b) in enumerate(zip(en, ru)):
            out += shape(f"{where}[{i}]", a, b)
        return out
    if isinstance(en, str) and isinstance(ru, str) and en.strip():
        if not ru.strip():
            return [f"{where}: empty in Russian"]
        if not CYRILLIC.search(ru) and latin_words(ru):
            return [f"{where}: Russian text is English: {ru[:60]!r}"]
    return []


if __name__ == "__main__":
    sys.exit(main())

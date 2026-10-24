#!/usr/bin/env python3
"""Strings for JEI/EMI categories, info pages and the handbook "Village Mood" section -> tools/lang/compat.lang.json."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "tools/lang/compat.lang.json"
NS = "villager_life"

RITES = {
    "build_festoon": (
        "Festoon", "Гирлянда",
        "The first sign of a content village: banners, potted flowers and lanterns strung over the square.",
        "Первый знак довольной деревни: флажки, цветы в горшках и фонари над площадью.",
    ),
    "build_festival": (
        "Harvest Festival", "Праздник урожая",
        "Barrels, cakes and a festive totem. Villagers gather to dance and trade a little cheaper.",
        "Бочки, торты и праздничный тотем. Жители собираются плясать и торгуют чуть дешевле.",
    ),
    "build_monument": (
        "Monument", "Памятник",
        "A stone monument to the player the village loves. Its plaque remembers the deeds that raised it.",
        "Каменный памятник любимому игроку. Табличка помнит дела, за которые его поставили.",
    ),
    "build_pavilion": (
        "Pavilion", "Шатёр",
        "A striped pavilion around the bell: the village is as happy as it gets.",
        "Полосатый шатёр вокруг колокола: деревня счастлива как никогда.",
    ),
    "build_vigil": (
        "Vigil", "Бдение",
        "White candles and soul lanterns. The village is worried and keeps watch at night.",
        "Белые свечи и фонари душ. Деревня встревожена и не спит по ночам.",
    ),
    "build_mourning": (
        "Mourning", "Траур",
        "Black banners, wither roses and a mourning totem: villagers grieve for what they lost.",
        "Чёрные флаги, розы иссушения и траурный тотем: жители оплакивают потерю.",
    ),
    "build_altar": (
        "Tocsin Forge", "Набатная кузня",
        "Angry villagers build a forge around the bell. Fed with iron, it wakes the Tocsin Golem.",
        "Озлобленные жители строят кузню вокруг колокола. Накормленная железом, она будит Набатного голема.",
    ),
    "build_pyre": (
        "Pyre", "Костёр",
        "Logs, crying obsidian and a soul campfire: the village burns its grudges in the open.",
        "Брёвна, плачущий обсидиан и костёр душ: деревня открыто сжигает обиды.",
    ),
}

FLAT = {
    "rite.{ns}.needs_above": ("Mood %s or higher", "Настроение %s и выше"),
    "rite.{ns}.needs_below": ("Mood %s or lower", "Настроение %s и ниже"),
    "jei.{ns}.village_rites": ("Village Rites", "Обряды деревни"),
    "jei.{ns}.tocsin_forge": ("Tocsin Forge", "Набатная кузня"),
    "emi.category.{ns}.village_rites": ("Village Rites", "Обряды деревни"),
    "emi.category.{ns}.tocsin_forge": ("Tocsin Forge", "Набатная кузня"),
    "jei.{ns}.materials": ("Built from", "Строят из"),
    "jei.{ns}.forge_feed": ("Villagers feed", "Жители кормят"),
    "jei.{ns}.forge_spoils": ("Golem drops", "С голема"),
    "jei.{ns}.spoil_core": ("One per fighter", "Каждому бойцу"),
    "jei.{ns}.spoil_iron": ("6-10 per fighter", "6-10 каждому бойцу"),
    "jei.{ns}.spoil_bell": ("35% per fighter", "35% каждому бойцу"),
    "jei.{ns}.spoil_staff": ("One per golem", "Один на голема"),
    "info.{ns}.staff": (
        "Hold use to charge the little bell for a second, then release: the village mood rises by 10. "
        "Sneak to curse it by 10 instead. Tolling it while the Tocsin Golem raises its own bell stuns the golem.",
        "Удерживайте ПКМ секунду, чтобы зарядить колокольчик, и отпустите: настроение деревни +10. "
        "Присев, вы проклинаете её на −10. Звон посоха, пока Набатный голем поднимает свой колокол, оглушает голема.",
    ),
    "info.{ns}.tocsin_core": (
        "The cooled heart of the Tocsin Golem. Every player who fought the golem gets one.",
        "Остывшее сердце Набатного голема. Достаётся каждому, кто сражался с големом.",
    ),
    "info.{ns}.altar_core": (
        "The furnace heart of the Tocsin Forge. It glows hotter as villagers feed it iron and toll the bell.",
        "Горн Набатной кузни. Раскаляется, когда жители кормят его железом и бьют в колокол.",
    ),
    "info.{ns}.handbook": (
        "Opens the Villager Life guide, including the Village Mood section with every rite and its build.",
        "Открывает справочник «Жизни деревни», включая раздел «Настроение деревни» со всеми обрядами и постройками.",
    ),
    "info.{ns}.forge_steps": (
        "1. Villagers gather in a ring around the forge.\n2. They throw six iron ingots into the furnace.\n"
        "3. The bell tolls three times and an iron frame rises.\n4. The Tocsin Golem awakens. Strike its open back, "
        "ring village bells to stun it, or answer its toll with the elder's staff.\n"
        "Break the forge or flood it during the chant to stop the rite.",
        "1. Жители встают кольцом вокруг кузни.\n2. Бросают в горн шесть железных слитков.\n"
        "3. Колокол бьёт трижды, поднимается железный каркас.\n4. Просыпается Набатный голем. Бейте в открытую спину, "
        "звоните в колокола деревни, чтобы оглушить его, или отвечайте на его звон посохом старосты.\n"
        "Сломайте или залейте кузню во время напева, чтобы сорвать обряд.",
    ),
    "subtitles.{ns}.golem.step": ("Tocsin Golem stomps", "Набатный голем топает"),
    "subtitles.{ns}.golem.hurt": ("Tocsin Golem clangs", "Набатный голем лязгает"),
    "subtitles.{ns}.golem.death": ("Tocsin Golem falls silent", "Набатный голем смолкает"),
    "subtitles.{ns}.golem.toll": ("Tocsin tolls", "Бьёт набат"),
    "subtitles.{ns}.golem.clang": ("Iron plates clang", "Лязгают железные плиты"),
    "subtitles.{ns}.golem.slam": ("Tocsin Golem slams", "Набатный голем бьёт оземь"),
    "subtitles.{ns}.golem.steam": ("Steam hisses", "Шипит пар"),
    "subtitles.{ns}.golem.crack": ("Bell cracks", "Трескается колокол"),
    "subtitles.{ns}.golem.awaken": ("Tocsin Golem awakens", "Пробуждается Набатный голем"),
    "subtitles.{ns}.turret.shot": ("Wall crossbow fires", "Стреляет настенный самострел"),
    "subtitles.{ns}.turret.load": ("Wall crossbow loaded", "Заряжают самострел"),
    "subtitles.{ns}.clock.strike": ("Tower clock strikes", "Бьют башенные часы"),
    "subtitles.{ns}.clock.wind": ("Clock wound", "Заводят часы"),
    "subtitles.{ns}.machine.work": ("Workstation works", "Работает станок"),
    "subtitles.{ns}.machine.done": ("Workstation finishes", "Станок закончил"),
    "subtitles.{ns}.golem.roar": ("Tocsin Golem roars", "Ревёт Набатный голем"),
    "subtitles.{ns}.golem.swing": ("Iron arm swings", "Свистит железная рука"),
    "subtitles.{ns}.shop.buy": ("Coins clink on the counter", "Звенят монеты на прилавке"),
    "subtitles.{ns}.shop.sell": ("Coins counted out", "Отсчитывают монеты"),
    "subtitles.{ns}.market.wage": ("Wages paid", "Выдают зарплату"),
    "subtitles.{ns}.market.caravan": ("Caravan bells jingle", "Звенят бубенцы каравана"),
    "subtitles.{ns}.family.wedding": ("Wedding bells ring", "Звонят свадебные колокола"),
    "subtitles.{ns}.staff.charge": ("Staff gathers a blessing", "Посох набирает благословение"),
    "subtitles.{ns}.staff.fizzle": ("Staff fizzles", "Посох гаснет"),
    "subtitles.{ns}.rite.bright": ("Rite shimmers", "Звучит светлый обряд"),
    "subtitles.{ns}.rite.mourn": ("Mourning hum", "Звучит траурный напев"),
    "subtitles.{ns}.rite.dark": ("Dark rite rumbles", "Гудит мрачный обряд"),
    "subtitles.{ns}.rite.shatter": ("Altar shatters", "Раскалывается алтарь"),
    "subtitles.{ns}.chapel.bells": ("Chapel bells ring", "Звонят колокола часовни"),
    "subtitles.{ns}.chapel.toll": ("Chapel bell tolls", "Звонит погребальный колокол"),
    "subtitles.{ns}.post.mailbox": ("Letter delivered", "Принесли письмо"),
    "subtitles.{ns}.post.letter": ("Letter opened", "Открывают письмо"),
    "subtitles.{ns}.board.pin": ("Notice pinned", "Прикалывают объявление"),
    "subtitles.{ns}.watch.alarm": ("Watchman sounds the alarm", "Стражник бьёт тревогу"),
    "subtitles.{ns}.town.donation": ("Coins donated", "Пожертвование в казну"),
    "subtitles.{ns}.music.fair": ("Fair music plays", "Играет ярмарочная музыка"),
    "subtitles.{ns}.golem.chain": ("Chain rattles", "Гремит цепь"),
    "subtitles.{ns}.crawler.step": ("Plate Crawler scuttles", "Ползёт плита"),
    "subtitles.{ns}.forge.ingot": ("Ingot hits the forge", "Слиток падает в горн"),
    "subtitles.{ns}.forge.toll": ("Forge bell tolls", "Бьёт колокол кузни"),
    "subtitles.{ns}.staff.toll": ("Elder's staff rings", "Звенит посох старосты"),
    "item.{ns}.villager_blessing_staff": ("Elder's Staff", "Посох старосты"),
    "handbook.{ns}.mood": ("Village Mood", "Настроение деревни"),
    "handbook.{ns}.mood.intro": (
        "Each village keeps a mood from -100 to +100. When it crosses a threshold, villagers build the strongest "
        "rite it allows first, then the lesser ones. Pick a rite to see its build.",
        "У каждой деревни есть настроение от −100 до +100. Пересекая порог, жители сначала строят самый сильный "
        "доступный обряд, затем младшие. Выберите обряд, чтобы увидеть постройку.",
    ),
}


def main():
    lang = {"en": {}, "ru": {}}
    for key, (en, ru, en_d, ru_d) in RITES.items():
        base = f"rite.{NS}.{key}"
        lang["en"][base], lang["ru"][base] = en, ru
        lang["en"][base + ".desc"], lang["ru"][base + ".desc"] = en_d, ru_d
    for key, (en, ru) in FLAT.items():
        k = key.format(ns=NS)
        lang["en"][k], lang["ru"][k] = en, ru
    OUT.write_text(json.dumps(lang, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{OUT.name}: {len(lang['en'])} keys")


if __name__ == "__main__":
    main()

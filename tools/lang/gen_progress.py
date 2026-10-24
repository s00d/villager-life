#!/usr/bin/env python3
"""Advancements, loot tables, tags and boss-core recipes for Villager Life 1.2.

Writes data files under neoforge/src/main/resources/data/villager_life and the
matching strings to tools/lang/progress.lang.json (merge with merge_lang.py).
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
NS = "villager_life"
DATA = ROOT / f"neoforge/src/main/resources/data/{NS}"
LANG = ROOT / "tools/lang/progress.lang.json"

ADVANCEMENTS = [
    # path, parent, icon, frame, hidden, en title, en desc, ru title, ru desc
    ("rites/festival", "root", "minecraft:cake", "task", False,
     "Festival", "See villagers raise a festival square",
     "Праздник", "Увидеть, как жители ставят праздничную площадь"),
    ("rites/monument", "rites/festival", f"{NS}:monument_plaque", "goal", False,
     "Monument", "Be there when the village consecrates its monument",
     "Памятник", "Быть рядом, когда деревня освящает памятник"),
    ("tocsin/awaken", "root", "minecraft:bell", "task", False,
     "Tocsin", "Witness the Tocsin Golem wake in a despairing village",
     "Набат", "Увидеть, как в отчаявшейся деревне просыпается Набатный голем"),
    ("tocsin/heart", "tocsin/awaken", "minecraft:iron_sword", "task", False,
     "Right in the Heart", "Hit the golem's open back while its fist is stuck",
     "В самое сердце", "Ударить голема в открытую спину, пока его кулак застрял"),
    ("tocsin/counter_toll", "tocsin/awaken", f"{NS}:villager_blessing_staff", "task", False,
     "Counter-Toll", "Break the golem's toll with the elder's staff",
     "Встречный звон", "Сбить удар колокола голема посохом старосты"),
    ("tocsin/bell", "tocsin/awaken", "minecraft:bell", "task", True,
     "Bell Against Bell", "Stagger the golem by ringing a village bell",
     "Колокол против колокола", "Оглушить голема, позвонив в деревенский колокол"),
    ("tocsin/silence", "tocsin/awaken", f"{NS}:tocsin_core", "challenge", False,
     "Silence", "Silence the Tocsin Golem",
     "Тишина", "Заставить Набатного голема замолчать"),
    ("family/wedding", "root", "minecraft:poppy", "task", False,
     "Witness a Wedding", "Be at the bell when two villagers marry",
     "Свидетель свадьбы", "Быть у колокола, когда двое жителей женятся"),
    ("family/baby", "family/wedding", "minecraft:white_bed", "task", False,
     "A New Neighbor", "Be near when a village family has a baby",
     "Новый сосед", "Быть рядом, когда в семье жителей рождается малыш"),
    ("family/matchmaker", "family/wedding", f"{NS}:wedding_ring", "task", False,
     "Matchmaker", "Find a single villager a match",
     "Сват", "Найти одинокому жителю пару"),
    ("family/guest", "family/wedding", f"{NS}:home_meal", "task", False,
     "Welcome Guest", "Be invited to dinner by a village family",
     "Желанный гость", "Получить приглашение на ужин от семьи жителей"),
    ("family/kids_friend", "family/baby", f"{NS}:wooden_toy", "task", False,
     "Friend of the Children", "Give a village child a toy",
     "Друг детей", "Подарить деревенскому ребёнку игрушку"),
    ("family/room", "family/baby", f"{NS}:cradle", "goal", False,
     "Housewarming", "Be there when a family finishes a new room",
     "Новоселье", "Быть рядом, когда семья достроила новую комнату"),
    ("family/hamlet", "family/wedding", "minecraft:bell", "goal", False,
     "New Land", "Find the hamlet that settlers from a full village founded",
     "Новая земля", "Найти хутор, основанный переселенцами из переполненной деревни"),
    ("family/coming_of_age", "family/baby", "minecraft:cake", "task", False,
     "All Grown Up", "Bring a present to a village child's coming of age",
     "Совсем взрослый", "Принести подарок на совершеннолетие деревенского ребёнка"),
    ("family/farewell", "family/wedding", "minecraft:wither_rose", "task", False,
     "Farewell", "Stand with a village at a funeral",
     "Прощание", "Быть с деревней на похоронах"),
    ("family/godparent", "family/baby", "minecraft:name_tag", "task", False,
     "Godparent", "Name a newborn village baby",
     "Крёстный", "Дать имя новорождённому деревенскому малышу"),
    ("family/best_man", "family/wedding", "minecraft:firework_rocket", "goal", False,
     "Best Man", "Grant a groom's wedding wish",
     "Шафер", "Исполнить свадебную просьбу жениха"),
    ("family/neighbour", "family/wedding", "minecraft:red_bed", "task", False,
     "Neighbour", "Sleep a night in a village",
     "Сосед", "Переночевать в деревне"),
    ("family/pet", "family/baby", f"{NS}:pet_bowl", "task", False,
     "Best Friend", "See a village family adopt a pet",
     "Лучший друг", "Увидеть, как семья жителей заводит питомца"),
    ("family/peacemaker", "family/wedding", "minecraft:cornflower", "task", False,
     "Peacemaker", "Help a quarrelling couple make up",
     "Миротворец", "Помирить поссорившихся супругов"),
]

# tabs of their own: a root each, earned when the player first comes upon a town / a spire (progress/Discoveries)
ROOTS = [
    # path, icon, background, en title, en desc, ru title, ru desc
    ("town/root", "minecraft:bell", "minecraft:textures/block/stone_bricks.png",
     "Town Life", "Come into a town that grew round a bell",
     "Жизнь города", "Прийти в город, выросший вокруг колокола"),
    ("spire/root", f"{NS}:spire_journal", f"{NS}:textures/block/spire/cracked_wall.png",
     "The Builders' Spire", "Find the spire that rose over a finished town",
     "Шпиль Зодчих", "Найти шпиль, выросший над достроенным городом"),
    ("mega/root", f"{NS}:mega_quarry", f"{NS}:textures/block/mega/frame.png",
     "Megastructures", "Get a part of the Outer Gates or a Builders' Frame",
     "Мегаконструкции", "Добыть деталь Врат Вне или каркас Зодчих"),
]

TOWN_AND_SPIRE = [
    ("town/shop", "town/root", f"{NS}:copper_coin", "task", False,
     "First Purchase", "Buy something at a town's stall",
     "Первая покупка", "Купить что-нибудь в лавке города"),
    ("town/sell", "town/shop", f"{NS}:silver_coin", "task", False,
     "Market Day", "Sell something to a town's stall",
     "Базарный день", "Продать что-нибудь лавке города"),
    ("town/errand", "town/root", "minecraft:writable_book", "task", False,
     "Errand Runner", "Bring a villager what the notice board asked for",
     "На посылках", "Принести жителю то, что просила доска объявлений"),
    ("town/lot", "town/shop", "minecraft:oak_fence", "goal", False,
     "Landholder", "Buy a lot from the notice board",
     "Землевладелец", "Купить участок у доски объявлений"),
    ("town/deed", "town/lot", f"{NS}:house_deed", "goal", False,
     "A Home of Your Own", "Buy an empty house with a deed",
     "Свой дом", "Купить пустой дом по грамоте"),
    ("town/flag", "town/root", "minecraft:white_banner", "task", False,
     "Our Colours", "Raise a town's flag from its notice board",
     "Наши цвета", "Поднять флаг города с доски объявлений"),
    ("town/letter", "town/root", "minecraft:written_book", "task", False,
     "You've Got Mail", "Take a villager's letter out of your mailbox",
     "Вам письмо", "Достать письмо жителя из своего почтового ящика"),
    ("town/map", "town/root", f"{NS}:town_map", "task", False,
     "Lay of the Land", "Draw a map of a town",
     "Как на ладони", "Нарисовать карту города"),
    ("town/traveller", "town/errand", "minecraft:honey_bottle", "task", False,
     "Stay a While", "Talk a traveller at the tavern into staying",
     "Оставайтесь", "Уговорить путника в таверне остаться"),
    ("town/caravan", "town/shop", "minecraft:chest", "task", False,
     "The Caravan Comes", "Be in town when the caravan arrives",
     "Караван пришёл", "Быть в городе, когда приходит караван"),
    ("town/chapel", "town/root", "minecraft:bell", "goal", False,
     "Bells on Sunday", "See a town finish its chapel",
     "Воскресный звон", "Увидеть, как город достроил часовню"),
    ("town/wall", "town/chapel", "minecraft:stone_bricks", "goal", False,
     "Walled Town", "See a town ring itself with a wall",
     "Город за стеной", "Увидеть, как город обнёс себя стеной"),
    ("spire/floor", "spire/root", "minecraft:cracked_stone_bricks", "task", False,
     "The First Step", "Climb onto the first floor of a spire",
     "Первая ступень", "Подняться на первый этаж Шпиля"),
    ("spire/notes", "spire/floor", f"{NS}:spire_journal", "task", False,
     "What the Builders Wrote", "Read a tablet of the Spire",
     "Что писали Зодчие", "Прочитать скрижаль Шпиля"),
    ("spire/trial", "spire/floor", f"{NS}:spire_seal_deep", "task", False,
     "Trial by Stone", "Pass the trial of the fifth floor",
     "Испытание камнем", "Пройти испытание пятого этажа"),
    ("spire/keeper", "spire/trial", f"{NS}:spire_seal_root", "goal", False,
     "Past the Gatekeeper", "Beat the keeper of the tenth floor",
     "Мимо Привратника", "Победить хранителя десятого этажа"),
    ("spire/schism", "spire/keeper", f"{NS}:spire_seal_spore", "goal", False,
     "The Schism", "Beat the Mason and the Surveyor together",
     "Раскол", "Победить Каменщика и Чертёжницу"),
    ("spire/halfway", "spire/schism", f"{NS}:spire_seal_ice", "goal", False,
     "Halfway to the Sky", "Climb past the fiftieth floor",
     "Полпути до неба", "Подняться выше пятидесятого этажа"),
    ("spire/summit", "spire/halfway", f"{NS}:spire_seal_summit", "challenge", False,
     "The Summit", "Beat the Mind on the hundredth floor",
     "Вершина", "Победить Разум на сотом этаже"),
    ("spire/beyond", "spire/summit", f"{NS}:core_shard", "challenge", True,
     "There Is No Top", "Climb past the two hundredth floor",
     "Вершины нет", "Подняться выше двухсотого этажа"),
    ("spire/secret", "spire/floor", "minecraft:mossy_stone_bricks", "task", False,
     "Behind the Crack", "Find a secret room's cache",
     "За трещиной", "Найти тайник тайной комнаты"),
    ("spire/riddle", "spire/secret", "minecraft:lever", "task", False,
     "Riddle Solved", "Solve a floor's riddle",
     "Загадка решена", "Разгадать загадку этажа"),
    ("spire/hone", "spire/floor", f"{NS}:core_anvil", "task", False,
     "Sharper", "Hone a piece of gear at the Core Anvil",
     "Острее", "Заточить вещь на наковальне Ядра"),
    ("spire/raise", "spire/hone", f"{NS}:spire_root_sword", "goal", False,
     "Builders' Steel", "Raise a piece of gear to a spire tier",
     "Сталь Зодчих", "Поднять вещь до тира Шпиля"),
    ("spire/honed", "spire/raise", f"{NS}:spire_star_sword", "challenge", False,
     "Honed to Shine", "Hone a piece to its third stage",
     "До блеска", "Заточить вещь до третьей ступени"),
    ("spire/outer", "spire/summit", f"{NS}:mega_part_colosseum", "goal", False,
     "Beyond the Hundredth", "Step through one of the Outer Gates",
     "За сотым", "Пройти в одни из Врат Вне"),
    ("spire/arena", "spire/outer", "minecraft:iron_sword", "challenge", False,
     "Gladiator", "Hold the Colosseum for twenty waves",
     "Гладиатор", "Продержаться в Колизее двадцать волн"),
    ("spire/isles", "spire/outer", "minecraft:feather", "challenge", False,
     "Sky Walker", "Reach twenty waystones on the Sky Path",
     "Небоход", "Дойти до двадцати вех на Небесной тропе"),
    ("spire/rush", "spire/outer", f"{NS}:mega_part_nameless", "challenge", False,
     "No Name Too Many", "Fell ten of the Nameless in one run",
     "Безымянных не бывает много", "Победить десять Безымянных за один забег"),
    ("mega/formed", "mega/root", f"{NS}:mega_frame", "task", False,
     "It Moves", "Bring a megastructure to life",
     "Ожило", "Собрать мегаконструкцию, чтобы она ожила"),
    ("mega/gate", "mega/formed", f"{NS}:gate_key_red", "goal", False,
     "Thinking with Waygates", "Step through a Waygate to its twin",
     "Через врата", "Пройти через врата к их паре"),
    ("mega/quarry", "mega/formed", "minecraft:diamond_pickaxe", "goal", False,
     "Down to the Bedrock", "Let a Quarry dig all the way down",
     "До самого бедрока", "Дать буру докопать до самого низа"),
    ("mega/all", "mega/formed", f"{NS}:mega_calm", "challenge", False,
     "Master Builder", "Bring every kind of megastructure to life",
     "Мастер Зодчий", "Оживить мегаконструкцию каждого вида"),
    ("spire/draught", "spire/keeper", f"{NS}:spire_draught_archive", "task", False,
     "A Chapter's Ward", "Drink a chapter's draught",
     "Оберег главы", "Выпить зелье главы"),
]


def write(path: pathlib.Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def advancements(lang):
    write(DATA / "advancement/root.json", {
        "display": {
            "icon": {"id": f"{NS}:handbook"},
            "title": {"translate": f"advancements.{NS}.root.title"},
            "description": {"translate": f"advancements.{NS}.root.description"},
            "background": "minecraft:textures/block/spruce_planks.png",
            "show_toast": False,
            "announce_to_chat": False,
        },
        "criteria": {"tick": {"trigger": "minecraft:tick"}},
    })
    lang[f"advancements.{NS}.root.title"] = {"en": "Villager Life", "ru": "Жизнь деревни"}
    lang[f"advancements.{NS}.root.description"] = {
        "en": "Villages have moods. Some of them ring.",
        "ru": "У деревень есть настроение. Иногда оно звонит."}
    for path, icon, background, en_t, en_d, ru_t, ru_d in ROOTS:
        key = path.replace("/", ".")
        write(DATA / f"advancement/{path}.json", {
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.{NS}.{key}.title"},
                "description": {"translate": f"advancements.{NS}.{key}.description"},
                "background": background,
                "show_toast": True,
                "announce_to_chat": False,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
        lang[f"advancements.{NS}.{key}.title"] = {"en": en_t, "ru": ru_t}
        lang[f"advancements.{NS}.{key}.description"] = {"en": en_d, "ru": ru_d}
    for path, parent, icon, frame, hidden, en_t, en_d, ru_t, ru_d in ADVANCEMENTS + TOWN_AND_SPIRE:
        key = path.replace("/", ".")
        write(DATA / f"advancement/{path}.json", {
            "parent": f"{NS}:{parent}",
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.{NS}.{key}.title"},
                "description": {"translate": f"advancements.{NS}.{key}.description"},
                "frame": frame,
                "hidden": hidden,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
        lang[f"advancements.{NS}.{key}.title"] = {"en": en_t, "ru": ru_t}
        lang[f"advancements.{NS}.{key}.description"] = {"en": en_d, "ru": ru_d}


def loot():
    write(DATA / "loot_table/entities/tocsin_golem.json", {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:tocsin_core"}]},
            {"rolls": 1, "entries": [{
                "type": "minecraft:item", "name": "minecraft:iron_ingot",
                "functions": [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": 6, "max": 10}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}],
             "entries": [{"type": "minecraft:item", "name": "minecraft:bell"}]},
        ],
    })
    write(DATA / "loot_table/entities/plate_crawler.json", {
        "type": "minecraft:entity",
        "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:item", "name": "minecraft:iron_nugget",
            "functions": [{"function": "minecraft:set_count",
                           "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]}]}],
    })


def tags():
    write(DATA / "tags/block/rite_blocks.json", {"values": [
        f"{NS}:{name}" for name in (
            "monument_base", "monument_pillar", "monument_plaque", "festival_totem", "mourning_totem",
            "altar_base", "altar_pillar", "altar_core")]})


def main():
    lang = {}
    advancements(lang)
    loot()
    # the rite_blocks tag is the datagen's now (LifeDataGen)
    lang["item.villager_life.tocsin_core"] = {"en": "Tocsin Core", "ru": "Сердце набата"}
    lang["entity.villager_life.tocsin_golem"] = {"en": "Tocsin Golem", "ru": "Набатный голем"}
    lang["entity.villager_life.plate_crawler"] = {"en": "Plate Crawler", "ru": "Ползучая плита"}
    lang["entity.villager_life.tocsin_anvil"] = {"en": "Falling Anvil", "ru": "Падающая наковальня"}
    lang["tag.block.villager_life.rite_blocks"] = {"en": "Rite Blocks", "ru": "Блоки обрядов"}
    write(LANG, {side: {key: value[side] for key, value in sorted(lang.items())} for side in ("en", "ru")})
    print(f"progress: {len(ADVANCEMENTS) + len(TOWN_AND_SPIRE) + len(ROOTS) + 1} advancements, {len(lang)} lang keys")


if __name__ == "__main__":
    main()

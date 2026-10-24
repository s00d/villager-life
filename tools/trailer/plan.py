#!/usr/bin/env python3
"""The second film's cut as a plan: shots with their player's lines (seconds into the shot), written out as
edit/edl.json for edit.py (which wants absolute times). Part 2 cuts on the beat of epic.mp3 (1.6 s from 12.79 s).
  plan.py && edit.py ../edit/edl.json en --vo"""
import json
from pathlib import Path

HOME = Path.home() / "Movies/VillagerLife/film2"
BEAT = 1.6

# (clip, in, dur, transition, its length, [(line, at in shot)], extra)
PART1 = [
    ("card_intro", 0.0, 4.2, "fadeblack", 0.6, [], {}),
    ("p1_s01_arrive", 0.3, 13.5, "fadeblack", 0.8, [("P01", 0.6), ("P02", 11.2)], {}),
    ("p1_s03_leave", 0.3, 8.0, "fadeblack", 1.0, [("P03", 0.6)], {}),
    ("p1_s04_mine", 1.0, 7.5, "fadeblack", 1.2, [], {"shared": True}),
    ("p1_s06_return", 0.3, 8.4, "fade", 0.7, [("P04", 3.4)], {"shared": True}),
    ("p1_s07_build", 0.3, 10.5, "fade", 0.7, [("P05", 2.5)], {"shared": True}),
    ("p1_s08_door", 0.3, 7.4, "fade", 0.7, [("P06", 4.8)], {}),
    ("p1_s09_shop", 0.3, 10.8, "fade", 0.7, [("P07", 4.0)], {}),
    ("p1_s10_wedding", 0.3, 12.4, "fade", 0.7, [("P08", -1.6)], {}),
    ("p1_s11_cradle", 0.3, 9.4, "fade", 0.7, [("P09", 5.4)], {}),
    ("p1_s12_board", 0.3, 11.2, "fadeblack", 0.9, [("P10", 3.0)], {}),
    ("p1_s13_dusk", 0.3, 9.4, "fadeblack", 1.0, [("P11", 3.0)], {"shared": True}),
    # the turn round at 7 s, the stone up from 15 s, the beam at 28 s: a white flash into part two
    ("p1_s14_spire", 2.0, 28.0, "fadewhite", 0.25, [("P12", 11.0)], {"shared": True}),
]

# part two: one beat a shot, two for the ones that tell more
F = "fade"
PART2 = [
    ("p2_summit", 5.0, 2 * BEAT, F, 0.12, [], {"shared": True}),
    ("p2_portal", 0.4, 2 * BEAT, "fadewhite", 0.12, [("P13", 0.3)], {"shared": True}),
    ("p2_f003_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f005_keeper", 3.0, BEAT, F, 0.1, [], {}),
    ("p2_f010_keeper", 3.0, BEAT, F, 0.1, [], {}),
    ("p2_f013_pack", 3.0, BEAT, F, 0.1, [], {}),
    ("p2_f015_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f020_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f023_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f025_keeper", 6.0, BEAT, F, 0.1, [], {}),
    ("p2_f030_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f033_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f035_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f040_keeper", 5.0, BEAT, F, 0.1, [], {}),
    ("p2_f045_keeper", 4.0, 2 * BEAT, F, 0.1, [], {}),
    ("p2_f050_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f053_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f055_keeper", 3.0, BEAT, "fadewhite", 0.1, [], {}),
    # the death: the colossus's blow, the red screen, a beat to say it
    ("p2_f060_die", 2.6, 3 * BEAT, "fadeblack", 0.2, [("P14", 3.0)], {}),
    ("p2_anvil_0", 1.9, BEAT / 2, F, 0.08, [], {}),
    ("p2_anvil_1", 1.9, BEAT / 2, F, 0.08, [], {}),
    ("p2_anvil_2", 1.9, BEAT / 2, F, 0.08, [], {}),
    ("p2_anvil_3", 1.9, BEAT / 2, "fadewhite", 0.1, [], {}),
    ("p2_f060_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f063_pack", 3.0, BEAT, F, 0.1, [], {}),
    ("p2_f070_keeper", 5.0, BEAT, F, 0.1, [], {}),
    ("p2_f073_pack", 3.0, BEAT, F, 0.1, [], {}),
    ("p2_f080_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f083_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f090_keeper", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f093_pack", 4.0, BEAT, F, 0.1, [], {}),
    ("p2_f100_keeper", 3.0, 2 * BEAT, F, 0.1, [], {}),
    # a won floor: the keeper falls, the chest comes up
    ("p2_f005_keeper", 15.0, 2 * BEAT, F, 0.15, [], {}),
    ("p2_heroes", 5.6, 2 * BEAT, "fadeblack", 0.4, [], {}),
    ("p2_summit", 0.3, 8.0, "fadeblack", 1.0, [("P15", 4.0)], {"shared": True}),
    ("card_outro", 0.0, 7.0, "fade", 0.5, [], {}),
]


# where each Russian fight take is worth cutting in (its own takes: the bot fought differently), by occurrence
IN_RU = {"p2_f003_pack": [4.0], "p2_f005_keeper": [12.0, 26.0], "p2_f010_keeper": [1.0], "p2_f013_pack": [12.0],
         "p2_f015_keeper": [26.0], "p2_f020_keeper": [1.5], "p2_f023_pack": [3.0], "p2_f025_keeper": [4.0],
         "p2_f030_keeper": [4.0], "p2_f033_pack": [2.0], "p2_f035_keeper": [4.0], "p2_f040_keeper": [4.0],
         "p2_f045_keeper": [3.0], "p2_f050_keeper": [13.0], "p2_f053_pack": [7.0], "p2_f055_keeper": [7.0],
         "p2_f060_die": [1.0], "p2_f060_keeper": [4.0], "p2_f063_pack": [3.0], "p2_f070_keeper": [5.0],
         "p2_f073_pack": [1.5], "p2_f080_keeper": [4.0], "p2_f083_pack": [4.0], "p2_f090_keeper": [4.0],
         "p2_f093_pack": [7.0], "p2_f100_keeper": [3.0]}


def main():
    shots, vo, t = [], [], 0.0
    seen = {}
    part2_at = None
    for i, (clip, cin, dur, x, tr, lines, extra) in enumerate(PART1 + PART2):
        if i == len(PART1):
            part2_at = t
        shot = {"clip": clip, "in": cin, "dur": round(dur, 3), "x": x, "t": tr, **extra}
        if clip in IN_RU:
            k = seen.get(clip, 0)
            seen[clip] = k + 1
            shot["in_ru"] = IN_RU[clip][min(k, len(IN_RU[clip]) - 1)]
        shots.append(shot)
        vo += [{"id": line, "at": round(t + at, 3)} for line, at in lines]
        t += dur
    total = t
    starts = {}
    acc = 0.0
    for s in shots:
        starts.setdefault(s["clip"], acc)
        acc += s["dur"]
    return_at = starts["p1_s06_return"]
    edl = {
        "game": 1.5, "music_vol": 0.16, "vo_vol": 1.7, "fade_out": 3.0,
        "shots": shots,
        "vo": vo,
        "titles": [
            {"at": return_at + 0.2, "dur": 2.6, "style": "chapter", "en": "Three days later", "ru": "Три дня спустя"},
            {"at": part2_at + 0.15, "dur": 3.0, "style": "logo", "en": "THE BUILDERS' SPIRE", "ru": "ШПИЛЬ ЗОДЧИХ"},
        ],
        "music": [
            # quiet under the village: it ends as the ground starts to shake
            {"file": "cozy", "from": 0.0, "at": 0.0, "dur": part2_at - 14.0, "vol": 1.0, "fin": 2.0, "fout": 5.0},
            # the beat that starts at 12.79 s lands on the cut into part two
            {"file": "epic", "from": 12.79, "at": part2_at, "dur": total - part2_at, "vol": 3.2, "fin": 0.05, "fout": 4.0},
        ],
    }
    sfx_dir = Path.home() / "Downloads/FireShot/villager-news-ru-port/neoforge/src/main/resources/assets/villager_life/sounds/sfx"
    spire = starts["p1_s14_spire"] - 2.0  # times below are in the clip
    edl["sfx"] = [{"file": str(sfx_dir / "spire_grow.ogg"), "at": round(spire + c, 2), "vol": round(0.25 + 0.04 * i, 2)}
                  for i, c in enumerate(range(9, 29, 2))]
    edl["sfx"] += [{"file": str(sfx_dir / "wall_rise.ogg"), "at": round(spire + c, 2), "vol": 0.7} for c in (16.5, 21.0, 25.0, 28.6)]
    out = HOME / "edit/edl.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(edl, indent=1, ensure_ascii=False), encoding="utf-8")
    print(out, "total", round(total, 2), "part two at", round(part2_at, 2))


if __name__ == "__main__":
    main()

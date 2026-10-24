#!/usr/bin/env python3
"""The mod's own sound events (golem, forge, staff, turret, tower clock, machines): their files and loudness.

Merges into neoforge/.../sounds.json, keeping every other entry as is.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "neoforge/src/main/resources/assets/villager_life/sounds.json"
SUB = "subtitles.villager_life."


def s(name, pitch=1.0, volume=1.0, weight=1, attenuation=None):
    """A vanilla file."""
    return own(f"minecraft:{name}", pitch, volume, weight, attenuation)


def own(name, pitch=1.0, volume=1.0, weight=1, attenuation=None, stream=False):
    entry = {"name": name if ":" in name else f"villager_life:sfx/{name}"}
    if stream:
        entry["stream"] = True
    if pitch != 1.0:
        entry["pitch"] = pitch
    if volume != 1.0:
        entry["volume"] = volume
    if weight != 1:
        entry["weight"] = weight
    if attenuation:
        entry["attenuation_distance"] = attenuation
    return entry


# sounds/sfx/*.ogg: made with fal (ElevenLabs SFX v2) by tools/art/sfx (batch.tsv, convert.py); the
# fal manifest with the prompts and request ids is tools/art/fal/sfx/fal_manifest.jsonl (local, not in git)
MACHINES = ["greenhouse_box", "smokehouse", "dye_vat", "fish_trap", "reading_desk", "survey_table", "font",
            "tanning_vat", "kiln", "armor_bench", "grinding_wheel", "weapon_forge"]

EVENTS = {
    "golem.step": [own("golem_step1", 0.95), own("golem_step1", 1.05), own("golem_step2", 0.95), own("golem_step2", 1.05)],
    "golem.hurt": [own("golem_hurt1"), own("golem_hurt2"), own("golem_hurt1", 0.9)],
    "golem.death": [own("golem_death", attenuation=48)],
    "golem.toll": [own("golem_toll1", volume=2.0, attenuation=64), own("golem_toll2", volume=2.0, attenuation=64)],
    "golem.clang": [own("golem_clang1", 1.0, 0.8), own("golem_clang2", 1.0, 0.8)],
    "golem.slam": [own("golem_slam", 1.0, 1.2, attenuation=40)],
    "golem.steam": [own("golem_steam", 1.0, 0.9)],
    "golem.crack": [own("golem_crack", 1.0, 1.4, attenuation=48)],
    "golem.awaken": [own("golem_awaken", 1.0, 2.0, attenuation=64)],
    "golem.chain": [own("golem_chain1"), own("golem_chain2")],
    "crawler.step": [own("crawler_step1", 1.0, 0.6), own("crawler_step2", 1.0, 0.6), own("crawler_step1", 1.15, 0.6)],
    "forge.ingot": [own("forge_ingot", 1.0, 0.8)],
    "forge.toll": [own("golem_toll1", 1.2, 2.0, attenuation=96), own("golem_toll2", 1.2, 2.0, attenuation=96)],
    "staff.toll": [own("staff_toll", 1.0, 1.2)],
    "turret.shot": [own("turret_shot", 1.0, 0.9), own("turret_shot", 1.1, 0.9)],
    "turret.load": [own("turret_load", 1.0, 0.8)],
    "clock.strike": [own("clock_strike", 1.0, 3.0, attenuation=96)],
    "clock.wind": [own("clock_wind", 1.0, 0.8)],
    "cart.roll": [own("cart_roll", 1.0, 0.6, attenuation=12)],
    "cart.load": [own("cart_load", 1.0, 0.8, attenuation=12)],
    "door_bell.ring": [own("door_bell", 1.0, 0.8, attenuation=16)],
    "pot.bubble": [own("pot_bubble", 1.0, 0.5, attenuation=10)],
    "sign.creak": [own("sign_creak", 1.0, 0.5, attenuation=12)],
    "golem.roar": [own("golem_roar", attenuation=48)],
    "golem.swing": [own("golem_swing", 1.0, 1.0), own("golem_swing", 1.1, 1.0)],
    "shop.buy": [own("shop_buy", 1.0, 0.8), own("shop_buy", 1.08, 0.8)],
    "shop.sell": [own("shop_sell", 1.0, 0.8)],
    "market.wage": [own("wage_paid", 1.0, 0.7)],
    "market.caravan": [own("caravan_bells", 1.0, 1.0, attenuation=48)],
    "family.wedding": [own("wedding_bells", 1.0, 1.5, attenuation=64)],
    "staff.charge": [own("staff_charge", 1.0, 0.6)],
    "staff.fizzle": [own("staff_fizzle", 1.0, 0.7)],
    "rite.bright": [own("rite_bright", attenuation=32)],
    "rite.mourn": [own("rite_mourn", attenuation=32)],
    "rite.dark": [own("rite_dark", attenuation=32)],
    "rite.shatter": [own("rite_shatter", attenuation=32)],
    "chapel.bells": [own("chapel_bells", attenuation=96)],
    "chapel.toll": [own("chapel_toll", attenuation=96)],
    "post.mailbox": [own("mailbox", 1.0, 0.8)],
    "post.letter": [own("letter_open", 1.0, 0.8)],
    "board.pin": [own("notice_pin", 1.0, 0.9)],
    "watch.alarm": [own("watch_alarm", attenuation=64)],
    "town.donation": [own("donation", 1.0, 0.8)],
    # the Spire (fal ElevenLabs SFX v2, batch5.tsv and batch6.tsv; Lyria for its music)
    "spire.grow": [own("spire_grow", 1.0, 3.0, attenuation=96)],
    "spire.portal_in": [own("spire_portal_in")],
    "spire.portal_out": [own("spire_portal_out")],
    "spire.floor_clear": [own("spire_floor_clear", 1.0, 1.5, attenuation=48)],
    "spire.chest": [own("spire_chest")],
    "spire.secret_break": [own("spire_secret_break", attenuation=24)],
    "spire.cache": [own("spire_cache")],
    "spire.tablet": [own("spire_tablet", 1.0, 0.8)],
    "spire.hone": [own("spire_hone")],
    "spire.raise": [own("spire_raise")],
    "spire.stage": [own("spire_stage")],
    "spire.guard.death": [own("guard_death")],
    "spire.guard.tell": [own("guard_tell", 1.0, 0.8)],
    "spire.guard.bolt": [own("guard_bolt", 1.0, 0.7)],
    "spire.guard.elite": [own("elite_roar", 1.0, 1.2, attenuation=24)],
    "spire.keeper.appear": [own("keeper_appear", 1.0, 2.0, attenuation=64)],
    "spire.keeper.phase": [own("keeper_phase", 1.0, 2.0, attenuation=64)],
    "spire.keeper.death": [own("keeper_death", 1.0, 2.0, attenuation=64)],
    "spire.keeper.stun": [own("keeper_stun", 1.0, 1.4, attenuation=32)],
    "spire.mark.warn": [own("mark_warn", 1.0, 0.8, attenuation=32)],
    "spire.mark.impact": [own("mark_impact", 1.0, 1.0, attenuation=32)],
    "spire.beam.charge": [own("beam_charge", 1.0, 1.0, attenuation=32)],
    "spire.beam.fire": [own("beam_fire", 1.0, 1.0, attenuation=32)],
    "spire.victory": [own("victory", 1.0, 1.0, attenuation=48)],
    "spire.wyrm.burrow": [own("wyrm_burrow", 1.0, 1.2, attenuation=32)],
    "spire.wyrm.erupt": [own("wyrm_erupt", 1.0, 1.4, attenuation=32)],
    "spire.pages": [own("pages_storm")],
    "spire.ink": [own("ink_splash")],
    "spire.clock.stop": [own("clock_stop", 1.0, 1.2, attenuation=32)],
    "spire.clock.rewind": [own("clock_rewind", 1.0, 1.4, attenuation=32)],
    "spire.echo": [own("echo_swing")],
    "spire.wood_morph": [own("wood_morph")],
    "spire.roots": [own("roots_burst")],
    "spire.ice_form": [own("ice_form")],
    "spire.frost_breath": [own("frost_breath", 1.0, 1.2, attenuation=32)],
    "spire.spores": [own("spore_puff")],
    "spire.hydra": [own("hydra_hiss")],
    "spire.honey_seal": [own("honey_seal")],
    "spire.bees": [own("bee_swarm", 1.0, 1.2, attenuation=32)],
    "spire.gravity": [own("gravity_flip", 1.0, 1.2, attenuation=32)],
    "spire.starfall": [own("starfall")],
    "spire.glass_split": [own("glass_split")],
    "spire.wall_rise": [own("wall_rise")],
    "spire.jolt": [own("jolt")],
    "spire.door_unlock": [own("door_unlock")],
    "spire.door_grind": [own("door_grind", 1.0, 1.0, attenuation=24)],
    "spire.lever": [own("lever")],
    "spire.glyph": [own("glyph")],
    "spire.chime": [own("chime")],
    "spire.puzzle_solved": [own("puzzle_solved", 1.0, 1.0, attenuation=24)],
    "spire.puzzle_wrong": [own("puzzle_wrong")],
    "spire.trap_click": [own("trap_click", 1.0, 1.0, attenuation=12)],
    "mega.formed": [own("mega_formed", 1.0, 1.0, attenuation=32)],
    "mega.broken": [own("mega_broken", 1.0, 1.0, attenuation=24)],
    "mega.quarry_start": [own("quarry_start", 1.0, 1.0, attenuation=24)],
    "mega.quarry_work": [own("quarry_work", 0.6, 1.0, attenuation=20)],
    "mega.quarry_done": [own("quarry_done", 1.0, 1.0, attenuation=24)],
    "mega.gate_open": [own("gate_open", 1.0, 1.0, attenuation=24)],
    "mega.gate_cross": [own("gate_cross", 1.0, 1.0, attenuation=16)],
    "mega.keystone": [own("keystone_set", 1.0, 1.0, attenuation=12)],
    "mega.sawmill_start": [own("sawmill_start", 1.0, 1.0, attenuation=24)],
    "mega.sawmill_work": [own("sawmill_work", 0.8, 1.0, attenuation=24)],
    "mega.smelter_start": [own("smelter_start", 1.0, 1.0, attenuation=24)],
    "mega.smelter_work": [own("smelter_work", 0.6, 1.0, attenuation=16)],
    "mega.calm_start": [own("calm_start", 1.0, 1.0, attenuation=24)],
    "mega.dome_start": [own("dome_start", 1.0, 1.0, attenuation=24)],
    "mega.well_start": [own("well_start", 1.0, 1.0, attenuation=24)],
    "mega.well_gather": [own("well_gather", 0.6, 1.0, attenuation=16)],
    "mega.drain_start": [own("drain_start", 1.0, 1.0, attenuation=24)],
    "mega.drain_work": [own("drain_work", 0.6, 1.0, attenuation=16)],
    "spire.spikes": [own("spikes", 1.0, 1.0, attenuation=16)],
    "spire.dart": [own("dart", 1.0, 1.0, attenuation=16)],
    "spire.gong": [own("gong", 1.0, 1.0, attenuation=24)],
    "spire.gears": [own("gears")],
    "spire.bloom": [own("bloom")],
    "spire.valve": [own("valve")],
    "spire.puff": [own("puff")],
    "spire.honey_drip": [own("honey_drip")],
    "spire.orrery": [own("orrery")],
    "spire.resonator": [own("resonator")],
    "spire.plate": [own("plate")],
    "spire.frost_burst": [own("frost_burst")],
    "spire.doom": [own("doom", 1.0, 1.0, attenuation=24)],
    "spire.star_mark": [own("star_mark")],
    "spire.afflict": [own("afflict", 0.8)],
    "spire.chalk": [own("grid_chalk")],
    "spire.power": [own("power_transfer", 1.0, 2.0, attenuation=64)],
    "spire.steam": [own("steam_vent")],
    "spire.dive": [own("serpent_dive")],
    "spire.rockfall": [own("rockfall")],
    "spire.whisper": [own("mind_whisper", 1.0, 1.4, attenuation=48)],
    "spire.guard.hit": [own("guard_hit1"), own("guard_hit2"), own("guard_hit1", 1.15)],
    "spire.sentinel.idle": [own("sentinel_idle", 1.0, 0.7, attenuation=16), own("sentinel_idle", 1.12, 0.7, attenuation=16)],
    "spire.sentinel.attack": [own("sentinel_attack", 1.0, 0.9), own("sentinel_attack", 1.1, 0.9)],
    "spire.hound.idle": [own("hound_idle", 1.0, 0.7, attenuation=16), own("hound_idle", 1.12, 0.7, attenuation=16)],
    "spire.hound.attack": [own("hound_attack", 1.0, 0.9), own("hound_attack", 1.1, 0.9)],
    "spire.vine.idle": [own("vine_idle", 1.0, 0.7, attenuation=16), own("vine_idle", 1.12, 0.7, attenuation=16)],
    "spire.vine.attack": [own("vine_attack", 1.0, 0.9), own("vine_attack", 1.1, 0.9)],
    "spire.moth.idle": [own("moth_idle", 1.0, 0.7, attenuation=16), own("moth_idle", 1.12, 0.7, attenuation=16)],
    "spire.moth.attack": [own("moth_attack", 1.0, 0.9), own("moth_attack", 1.1, 0.9)],
    "spire.ray.idle": [own("ray_idle", 1.0, 0.7, attenuation=16), own("ray_idle", 1.12, 0.7, attenuation=16)],
    "spire.ray.attack": [own("ray_attack", 1.0, 0.9), own("ray_attack", 1.1, 0.9)],
    "spire.wisp.idle": [own("wisp_idle", 1.0, 0.7, attenuation=16), own("wisp_idle", 1.12, 0.7, attenuation=16)],
    "spire.wisp.attack": [own("wisp_attack", 1.0, 0.9), own("wisp_attack", 1.1, 0.9)],
    "spire.fish.idle": [own("fish_idle", 1.0, 0.7, attenuation=16), own("fish_idle", 1.12, 0.7, attenuation=16)],
    "spire.fish.attack": [own("fish_attack", 1.0, 0.9), own("fish_attack", 1.1, 0.9)],
    "music.spire_keeper": [own("villager_life:music/spire_keeper", 1.0, 0.9, attenuation=48, stream=True)],
    "music.spire_trial": [own("villager_life:music/spire_trial", 1.0, 0.9, attenuation=48, stream=True)],
    "music.spire_mind": [own("villager_life:music/spire_mind", 1.0, 1.0, attenuation=48, stream=True)],
    "music.spire_floor": [own("villager_life:music/spire_floor", 1.0, 0.5, attenuation=64, stream=True)],
    "music.fair": [own("villager_life:music/fair", 1.0, 0.6, attenuation=40, stream=True)],
    # the town's own records (fal google/lyria-3.5): the feast, the tavern, the chapel's hymn, an evening waltz
    "music.feast": [own("villager_life:music/feast", 1.0, 0.8, attenuation=40, stream=True)],
    "music.tavern": [own("villager_life:music/tavern", 1.0, 0.7, attenuation=24, stream=True)],
    "music.hymn": [own("villager_life:music/hymn", 1.0, 0.7, attenuation=40, stream=True)],
    "music.evening": [own("villager_life:music/evening", 1.0, 0.7, attenuation=32, stream=True)],
}
for machine in MACHINES:
    EVENTS[f"machine.{machine}.work"] = [own(f"{machine}_work", 1.0, 0.45, attenuation=12)]
    EVENTS[f"machine.{machine}.done"] = [own(f"{machine}_done", 1.0, 0.7, attenuation=16)]
SUBTITLES = {f"machine.{m}.{k}": f"machine.{k}" for m in MACHINES for k in ("work", "done")}


def main():
    files = SOUNDS.parent / "sounds"
    missing = [e["name"] for sounds in EVENTS.values() for e in sounds
               if e["name"].startswith("villager_life:") and not (files / (e["name"].split(":", 1)[1] + ".ogg")).is_file()]
    if missing:
        raise SystemExit(f"no file for: {sorted(set(missing))}")
    data = json.loads(SOUNDS.read_text(encoding="utf-8"))
    for key, sounds in EVENTS.items():
        data[key] = {"subtitle": SUB + SUBTITLES.get(key, key), "sounds": sounds}
    SOUNDS.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"sounds.json: {len(EVENTS)} sound events, {len(data)} total")


if __name__ == "__main__":
    main()

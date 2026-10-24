#!/usr/bin/env python3
"""Records fights on floors of the spire for part 2: per floor, enter, gear of its chapter on, (keeper floors: guards
gone, stand before the keeper), the duel bot fights for SECONDS while the recorder runs.
  fights.py FLOOR[:keeper|:pack|:die] ... [--seconds N]"""
import json, subprocess, sys, time
from pathlib import Path
REPO = Path.home() / "Downloads/FireShot/villager-news-ru-port"
HOME = Path.home() / "Movies/VillagerLife/film2"
TIERS = ["root", "archive", "clockwork", "garden", "deep", "ice", "spore", "hive", "star", "summit"]


sys.path.insert(0, str(REPO / "tools/dev"))
import mcp  # noqa: E402  in-process calls (a python per command was most of a take's setup)

LANG = (HOME / "work/.lang").read_text().strip() if (HOME / "work/.lang").exists() else "en"
# English takes stay where they were made (work/), every other language in its own folder
OUT = HOME / "work" if LANG == "en" else HOME / "work" / LANG


def mcc(*cmds):
    out = []
    for c in cmds:
        if c.startswith("sleep "):
            time.sleep(float(c.split()[1]))
            continue
        try:
            r = json.loads(mcp.tool("mc.server.command.run", {"command": c.lstrip("/"), "as": "Dev"}))
            out.append(" | ".join(r.get("output") or []))
        except (RuntimeError, ValueError) as error:
            out.append(str(error))
    return "\n".join(out)


def passed(out):
    # /execute if says it in the game's language
    return "Test passed" in out or "Проверка пройдена" in out


def client(cmd):
    mcp.tool("mc.client.command.run", {"command": cmd})


def gear(floor, hone, naked=False):
    tier = TIERS[min(9, (floor - 1) // 10)]
    if naked:
        mcc("/clear @s", "/item replace entity @s weapon.mainhand with minecraft:wooden_sword")
        return
    for slot, piece in [("weapon.mainhand", "sword"), ("armor.head", "helmet"), ("armor.chest", "chestplate"),
                        ("armor.legs", "leggings"), ("armor.feet", "boots")]:
        mcc(f"/item replace entity @s {slot} with villager_life:spire_{tier}_{piece}[custom_data={{vnap_hone:{hone}}}]")


def take(floor, kind, seconds):
    name = f"p2_f{floor:03d}_{kind}"
    for attempt in range(3):
        mcc("/difficulty normal", "/effect clear @s", "/gamemode survival", "/vnap spire leave", "sleep 3",
            "/execute in minecraft:overworld run tp @s -551 64 441", "sleep 3", f"/vnap spire floor {floor}", "/vnap spire enter")
        # until the floor stands with its guards (they come with it, after the player)
        here = ""
        for _ in range(30):
            time.sleep(1.5)
            here = mcc("/vnap spire")
            if f"floor {floor} of" in here and ((" guards," in here and ": 0 guards" not in here) or "cleared" in here):
                break
        if f"floor {floor} of" in here:
            break
        print("  retry: landed on", here.strip()[:90], flush=True)
    time.sleep(2)
    if kind in ("keeper", "die"):
        mcc("/kill @e[type=villager_life:spire_walker,distance=..200]", "/kill @e[type=villager_life:spire_flyer,distance=..200]",
            "/kill @e[type=villager_life:spire_swimmer,distance=..200]",
            "/execute at @e[type=villager_life:spire_keeper,limit=1,sort=nearest] run tp Dev ~ ~ ~-6 facing entity @e[type=villager_life:spire_keeper,limit=1,sort=nearest] eyes")
        # a floor still open from an earlier visit stays cleared: its keeper is called up in front instead
        if not passed(mcc("/execute if entity @e[type=villager_life:spire_keeper,distance=..200]")):
            mcc(f"/vnap spire keeper {floor}")
        # still in the waiting lock while the slot builds: the keeper is not near yet
        for _ in range(20):
            if passed(mcc("/execute if entity @e[type=villager_life:spire_keeper,distance=..40]")):
                break
            time.sleep(2)
            mcc("/execute at @e[type=villager_life:spire_keeper,limit=1,sort=nearest] run tp Dev ~ ~ ~-6 facing entity @e[type=villager_life:spire_keeper,limit=1,sort=nearest] eyes")
    if kind == "pack":
        # the entrance is a corridor: start where the guards are
        g = "@e[type=villager_life:spire_walker,limit=1,sort=nearest]"
        mcc(f"/execute at {g} positioned ^ ^ ^-2 run tp Dev ~ ~ ~ facing entity {g} eyes")
    gear(floor, 14, naked=kind == "die")
    if kind == "die":
        # low already: at full health the keeper's blows did not always end it within the take
        mcc("/effect give @s instant_damage 1 1 true")
    if kind != "die":
        mcc("/effect give @s regeneration infinite 1 true", "/effect give @s instant_health 1 5 true")
    client("vnaprec hud true")
    client(f"vnaprec start {name}")
    client(f"vnapbot duel {seconds}")
    if kind == "die":
        # the keeper's blow that ends it, five seconds in (its own swings did not always land in time)
        time.sleep(5)
        mcc("/damage Dev 200 minecraft:mob_attack by @e[type=villager_life:spire_keeper,limit=1,sort=nearest]")
        time.sleep(seconds - 4)
    else:
        time.sleep(seconds + 1)
    client("vnaprec stop")
    client("vnapbot stop")
    time.sleep(3)
    OUT.mkdir(parents=True, exist_ok=True)
    subprocess.run(["python3", str(Path(__file__).resolve().parent / "mux.py"),
                    str(REPO / "neoforge/versions/1.21.1/run/recordings"), name, str(OUT)], check=True)
    if kind == "die":
        client("vnaprec hud true")
        mcp.tool("mc.client.player.respawn")


def main():
    args = sys.argv[1:]
    seconds = 35
    if "--seconds" in args:
        i = args.index("--seconds")
        seconds = int(args[i + 1])
        del args[i:i + 2]
    for a in args:
        floor, _, kind = a.partition(":")
        take(int(floor), kind or "pack", seconds)
        print("done", a, flush=True)


if __name__ == "__main__":
    main()

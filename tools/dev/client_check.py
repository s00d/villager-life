#!/usr/bin/env python3
"""A version's client looked at end to end: a dev server with the seed's savanna village, a production client with
the MCP mod (prod_client.py), and screenshots of what only a client shows.

  python3 tools/dev/client_check.py 26.3            # builds the jar, starts server 8 and the client, takes the shots
  python3 tools/dev/client_check.py 26.3 --keep     # leaves both running afterwards

Shots land in neoforge/versions/<v>/run-prod/screenshots/<v>-<scene>.png: the village and its shopkeeper, the guide
(contents page and a page with a turning schematic), decor that shows items, the notice board, a machine, the shop
window (with JEI beside it) and the Tocsin Golem. The world is server 7's (server.py start 7 --seed 20261020 once);
the scenes are set by commands, so the village's own state does not matter. Look at every picture.
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEV = ROOT / "tools/dev"
N = "8"


def run(*args: str, env: dict[str, str] | None = None) -> str:
    return subprocess.run([sys.executable, *args], cwd=ROOT, env=env, capture_output=True, text=True).stdout


def main(args: list[str]) -> int:
    mc = args[0]
    env = dict(os.environ, VL_MC=mc)
    server = str(DEV / "server.py")
    mcp_env = dict(env, VL_MCP=run(str(DEV / "prod_client.py"), "mcp", mc, env=env).strip())

    def cmd(*commands: str) -> str:
        return run(server, "cmd", N, *commands, env=env)

    def tool(name: str, arguments: dict | None = None) -> str:
        return run(str(DEV / "mcp.py"), name, json.dumps(arguments or {}), env=mcp_env)

    def close() -> None:
        if json.loads(tool("mc.client.state") or "{}").get("screen"):
            tool("mc.client.keyboard.press", {"key": "escape"})

    def shot(scene: str) -> None:
        time.sleep(2)
        tool("mc.client.screenshot.take", {"name": f"{mc}-{scene}"})
        print(f"{mc}-{scene}: {ROOT}/neoforge/versions/{mc}/run-prod/screenshots/{mc}-{scene}.png")

    subprocess.run(["./gradlew", "--no-configuration-cache", "--offline", f":neoforge:{mc}:jar"], cwd=ROOT, env=env,
                   stdout=subprocess.DEVNULL, check=True)
    run(server, "build", env=env)
    world = ROOT / "neoforge/versions" / mc / "run/servers" / N / "world"
    run(server, "start", N, *([] if world.exists() else ["--seed", "20261020"]), env=env)
    try:
        cmd("whitelist off", "op Tester")
        options = ROOT / "neoforge/versions" / mc / "run-prod/options.txt"
        options.parent.mkdir(parents=True, exist_ok=True)
        keep = [line for line in (options.read_text().splitlines() if options.exists() else [])
                if line.split(":")[0] not in ("tutorialStep", "onboardAccessibility", "pauseOnLostFocus", "soundCategory_master")]
        options.write_text("\n".join(keep + ["tutorialStep:none", "onboardAccessibility:false", "pauseOnLostFocus:false",
                                             "soundCategory_master:0.0"]) + "\n")
        info = Path(mcp_env["VL_MCP"])
        info.unlink(missing_ok=True)
        print(run(str(DEV / "prod_client.py"), "start", mc, env=env).strip())
        for _ in range(200):
            if info.exists():
                break
            time.sleep(3)
        time.sleep(15)
        print(tool("mc.client.connection.connect", {"address": "127.0.0.1:25608", "wait": True})[:160])
        for _ in range(20):
            if json.loads(tool("mc.client.state") or "{}").get("inWorld"):
                break
            time.sleep(3)
        else:
            print("the client did not get into the world: " + tool("mc.client.state")[:300])
            return 1

        cmd("gamemode creative Tester", "time set noon", "weather clear", "clear Tester")
        tool("mc.client.hotbar.select", {"slot": 5})
        # the village by its bell, a villager in front
        villagers = json.loads(tool("mc.client.entities.nearby", {"radius": 200, "type": "minecraft:villager"}) or "[]")
        cmd("tp Tester 262 66 215")
        time.sleep(3)
        near = json.loads(tool("mc.client.entities.nearby", {"radius": 48, "type": "minecraft:villager"}) or "[]")
        if isinstance(near, dict):
            near = near.get("entities", [])
        if near:
            v = near[0]
            cmd(f"tp Tester {v['x'] + 3:.1f} {v['y']:.1f} {v['z'] + 3:.1f}")
            time.sleep(2)
            tool("mc.client.player.look_at", {"x": v["x"], "y": v["y"] + 1.5, "z": v["z"]})
        shot("villager")
        del villagers
        # the guide
        tool("mc.client.command.run", {"command": "/vnapguide"})
        shot("guide")
        close()
        tool("mc.client.command.run", {"command": "/vnapguide houses"})
        shot("guide-houses")
        close()
        # decor that shows items, a notice board and a machine on a stone floor
        cmd("fill 255 70 240 263 75 248 air", "fill 255 69 240 263 69 248 stone",
            "setblock 257 70 244 villager_life:harvest_basket[facing=south]",
            "setblock 258 70 244 villager_life:fish_rack[facing=south]",
            "setblock 259 70 244 villager_life:sword_barrel[facing=south]",
            "setblock 260 70 244 villager_life:chopping_block[facing=south]",
            "setblock 261 70 244 villager_life:notice_board[facing=south]",
            "setblock 262 70 244 villager_life:smokehouse[facing=south]",
            "tp Tester 259.5 70 248.5 180 15")
        shot("decor")
        cmd("tp Tester 261.5 70 246.5 180 20")
        time.sleep(1)
        tool("mc.client.block.interact", {"x": 261, "y": 70, "z": 244, "face": "south", "hand": "main"})
        shot("board")
        close()
        tool("mc.client.block.interact", {"x": 262, "y": 70, "z": 244, "face": "south", "hand": "main"})
        shot("machine")
        close()
        # the shop: its counter (set down if the stall house is not finished yet) with the shopkeeper by it
        info_line = cmd("execute as Tester at @s run vnap shop info")
        if "counter " not in info_line:
            # a fresh village opens its shop on its first morning: bring a shopkeeper, build the stall house, let a day pass
            cmd("execute as Tester at @s run vnap shop choose", "execute as Tester at @s run vnap shop stall", "time add 24000")
            time.sleep(8)
            info_line = cmd("execute as Tester at @s run vnap shop info")
        counter = None
        if "counter " in info_line:
            counter = [int(c) for c in info_line.split("counter ")[1].split(",")[:3]]
        if counter:
            x, y, z = counter
            # the stall opens with its keeper by the counter: one walked off for the day is brought back
            keeper = info_line.split(": ", 1)[1].split(",")[0].replace(" (not loaded)", "")
            cmd(f"setblock {x} {y} {z} villager_life:market_counter[facing=south]", f"tp Tester {x}.5 {y} {z + 3}.5 180 10",
                f'tp @e[type=minecraft:villager,name="{keeper}",limit=1] {x}.5 {y} {z - 1}.5')
            time.sleep(2)
            tool("mc.client.block.interact", {"x": x, "y": y, "z": z, "face": "south", "hand": "main"})
            shot("shop")
            close()
        # the golem
        cmd("gamemode spectator Tester", "summon villager_life:tocsin_golem 259 70 236 {NoAI:1b}", "tp Tester 259.5 72 246.5 180 5")
        shot("golem")
        cmd("kill @e[type=villager_life:tocsin_golem]", "gamemode creative Tester")
        errors = [line for line in Path(f"/tmp/vl-prod-{mc}.log").read_text(errors="replace").splitlines()
                  if ("villager" in line.lower() or "vnap" in line) and ("ERROR" in line or "Rejecting" in line or "Missing" in line)]
        print(f"client log lines about the mod: {len(errors)}")
        for line in errors[:20]:
            print("  " + line[:220])
    finally:
        if "--keep" not in args:
            run(str(DEV / "prod_client.py"), "stop", mc, env=env)
            run(server, "stop", N, env=env)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

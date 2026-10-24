#!/usr/bin/env python3
"""Film driver (copy for the second film: work/ here, walk shots on foot) for the dev client (recorder /vnaprec, camera /vnapcam).

  shoot.py preview "CAM"                     first and last frame of the camera move -> /tmp/vl-preview.png
  shoot.py take NAME SECONDS "CAM" [opts]    record a take -> work/<lang>/NAME.mp4 (+ .png sheet)
      --rate N        /tick rate N while filming (timelapse), 20 after
      --hud           keep the HUD (default: hidden, as F1)
      --at T CMD      a command T seconds into the take ('/x' = as the player on the server, 'c:x' = client command)
      --pre CMD       a command before the take (same forms)
      --lead S        seconds between --pre and the take
  shoot.py lang en|ru                        voice clips + game language (reloads resources)

CAM: "path x y z yaw pitch; x y z yaw pitch; ..." | "orbit x y z radius height fromDeg toDeg" | "still x y z yaw pitch"
"""
import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

REPO = Path.home() / "Downloads/FireShot/villager-news-ru-port"
# the film to shoot for: this one, or the first (VL_FILM=~/Movies/VillagerLife/film1)
HOME = Path(os.environ.get("VL_FILM", Path.home() / "Movies/VillagerLife/film2")).expanduser()
TOOLS1 = REPO / "tools/trailer"
REC = REPO / "neoforge/versions/1.21.1/run/recordings"
SHOTS = REPO / "neoforge/versions/1.21.1/run/screenshots"
LANG = HOME / "work/.lang"


sys.path.insert(0, str(REPO / "tools/dev"))
import mcp as _mcp  # noqa: E402  the game's MCP over HTTP from this process (a python per call was most of a take's setup)


def mcp(tool, args=None):
    try:
        out = _mcp.tool(tool, args or {})
    except RuntimeError as error:
        raise SystemExit(f"{tool}: {error}")
    return json.loads(out) if out.strip().startswith("{") else out


def batch(cmds):
    """Server commands as the player, in order, in one call (mc.batch)."""
    calls = [{"tool": "mc.server.command.run", "args": {"command": c.lstrip("/"), "as": "Dev"}} for c in cmds]
    if calls:
        mcp("mc.batch", {"calls": calls, "keepGoing": True})


def run(cmd):
    """'/x' as the player on the integrated server (answer printed), 'c:x' a client command."""
    if cmd.startswith("c:"):
        mcp("mc.client.command.run", {"command": cmd[2:]})
        return
    if cmd.startswith("i:"):
        x, y, z, face = cmd[2:].split()
        mcp("mc.client.block.interact", {"x": int(x), "y": int(y), "z": int(z), "face": face, "hand": "main"})
        return
    if cmd.startswith("a:"):
        actor(cmd[2:])
        return
    if cmd.startswith("s:"):
        # s:Name x y z yaw : the villager put there, still, facing yaw
        name, x, y, z, yaw = cmd[2:].split()
        run(f"/data merge entity @e[type=villager,name={name},limit=1] {{NoAI:0b}}")
        run(f"/tp @e[type=villager,name={name},limit=1] {x} {y} {z} {yaw} 0")
        # held in place, not frozen: an AI villager turns its head to the camera (a player) and to whoever talks
        run(f"/effect give @e[type=villager,name={name},limit=1] slowness infinite 255 true")
        return
    if cmd.startswith("u:"):
        run(f"/effect clear @e[type=villager,name={cmd[2:]},limit=1] slowness")
        return
    if cmd.startswith("sayat:"):
        # sayat:x y z Title — the villager nearest that point (a baby in its cradle)
        x, y, z, title = cmd[6:].split(" ", 3)
        run(f"/execute positioned {x} {y} {z} run vnap say !{title}")
        return
    if cmd.startswith("say:"):
        # say:Name Title of the group
        name, title = cmd[4:].split(" ", 1)
        run(f"/execute at @e[type=villager,name={name},limit=1] run vnap say !{title}")
        return
    if cmd.startswith("q:"):
        mcp("mc.client.container.quick_move", {"slot": int(cmd[2:])})
        return
    if cmd.startswith("k:"):
        mcp("mc.client.keyboard.press", {"key": cmd[2:]})
        return
    r = mcp("mc.server.command.run", {"command": cmd.lstrip("/"), "as": "Dev"})
    print(f"  {cmd} -> {' | '.join(r.get('output') or [])[:300]}")


CAM0 = [None]


def actor(spec):
    """a:Name side fwd [up] [@Other|@cam|yaw]: the villager put `fwd` blocks ahead of the first camera pose and `side`
    to its left (negative: right), on the ground, held there, facing the camera, another villager or a yaw."""
    import math
    parts = spec.split()
    name, side, fwd = parts[0], float(parts[1]), float(parts[2])
    face = parts[3] if len(parts) > 3 else "@cam"
    # a trailing ! freezes the villager (no AI): it keeps facing where it was turned, not the camera
    frozen = face.endswith("!")
    face = face.rstrip("!")
    x, y, z, yaw = (float(v) for v in CAM0[0][:4])
    r = math.radians(yaw)
    fx, fz = -math.sin(r), math.cos(r)
    lx, lz = fz, -fx
    px, pz = x + fx * fwd + lx * side, z + fz * fwd + lz * side
    run(f"/data merge entity @e[type=villager,name={name},limit=1] {{NoAI:0b}}")
    # dropped from over the ground gently (a fall from six blocks killed four of the cast)
    run(f"/effect give @e[type=villager,name={name},limit=1] slow_falling 5 0 true")
    run(f"/effect give @e[type=villager,name={name},limit=1] resistance infinite 255 true")
    run(f"/execute positioned {px:.2f} {y + 3:.2f} {pz:.2f} run tp @e[type=villager,name={name},limit=1] ~ ~ ~")
    run(f"/execute as @e[type=villager,name={name},limit=1] at @s run tp @s ~ ~ ~")
    if face == "@cam":
        run(f"/execute as @e[type=villager,name={name},limit=1] at @s run tp @s ~ ~ ~ facing {x:.2f} {y:.2f} {z:.2f}")
    elif face.startswith("@"):
        run(f"/execute as @e[type=villager,name={name},limit=1] at @s run tp @s ~ ~ ~ facing entity @e[type=villager,name={face[1:]},limit=1]")
    else:
        run(f"/tp @e[type=villager,name={name},limit=1] ~ ~ ~ {face} 0")
    run(f"/effect give @e[type=villager,name={name},limit=1] slowness infinite 255 true")
    if frozen:
        time.sleep(2.5)
        run(f"/data merge entity @e[type=villager,name={name},limit=1] {{NoAI:1b}}")
        if face.startswith("@") and face != "@cam":
            run(f"/execute as @e[type=villager,name={name},limit=1] at @s run tp @s ~ ~ ~ facing entity @e[type=villager,name={face[1:]},limit=1] eyes")
        elif not face.startswith("@"):
            run(f"/tp @e[type=villager,name={name},limit=1] ~ ~ ~ {face} 0")


def release():
    """Every villager free again after a take: a held actor (the mayor) once stood all day where a scene left it."""
    run("/effect clear @e[type=villager] slowness")
    run("/effect clear @e[type=villager] slow_falling")
    run("/execute as @e[type=villager] run data merge entity @s {NoAI:0b}")


def cam_command(secs, cam):
    kind, _, rest = cam.strip().partition(" ")
    if kind == "still":
        return f"vnapcam path {secs} {rest}"
    if kind == "walk":
        # on foot as the player: the first pose is where the take starts, the rest are steps and a last look
        return "vnapbot walk " + ";".join(rest.split(";")[1:])
    return f"vnapcam {kind} {secs} {rest}"


def poses(cam):
    """First and last pose (tp arguments) of a camera move."""
    kind, _, rest = cam.strip().partition(" ")
    if kind == "walk":
        first = rest.split(";")[0].split()
        return [first, first]
    if kind in ("path", "still"):
        pts = [p.split() for p in rest.split(";") if p.strip()]
        return [pts[0], pts[-1]]
    import math
    x, y, z, r, h, a, b = map(float, rest.split())
    out = []
    for deg in (a, b):
        ang = math.radians(deg)
        px, py, pz = x - math.sin(ang) * r, y + h, z + math.cos(ang) * r
        dx, dy, dz = x - px, y - py, z - pz
        yaw = math.degrees(math.atan2(-dx, dz))
        pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        out.append([f"{px:.2f}", f"{py:.2f}", f"{pz:.2f}", f"{yaw:.1f}", f"{pitch:.1f}"])
    return out


def preview(cam, pre=()):
    from PIL import Image
    CAM0[0] = poses(cam)[0]
    for c in pre:
        run(c)
    if pre:
        time.sleep(2)
    ims = []
    for i, p in enumerate(poses(cam)):
        run("/tp @s " + " ".join(p))
        time.sleep(2.5)
        mcp("mc.client.screenshot.take", {"name": f"preview{i}.png"})
        ims.append(Image.open(SHOTS / f"preview{i}.png").convert("RGB").resize((960, 540)))
    sheet = Image.new("RGB", (1920, 540))
    for i, im in enumerate(ims):
        sheet.paste(im, (960 * i, 0))
    sheet.save("/tmp/vl-preview.png")
    print("/tmp/vl-preview.png")


def lang(code):
    subprocess.run([str(TOOLS1 / "voice.sh"), code], check=True)
    mcp("mc.client.command.run", {"command": f"vnaprec lang {code}_{'us' if code == 'en' else 'ru'}"})
    time.sleep(3)
    while mcp("mc.client.state").get("loading"):
        time.sleep(1)
    time.sleep(8)
    LANG.write_text(code)
    print("lang", code)


def take(name, secs, cam, opts):
    code = LANG.read_text().strip() if LANG.exists() else "en"
    out = HOME / "work" / code
    out.mkdir(parents=True, exist_ok=True)
    first = poses(cam)[0]
    CAM0[0] = first
    # no name tags over the cast (villagers loaded since the last take too)
    run("/team join cast @e[type=villager]")
    # plain server commands go in one batch; the rest (actors, clicks) one by one, in order around them
    group = []
    for c in opts["pre"]:
        if c.startswith("/"):
            group.append(c)
            continue
        batch(group)
        group = []
        run(c)
    batch(group)
    # no screen left open by the take before (an escape too many opens the pause menu, and it was filmed)
    state = mcp("mc.client.state")
    if isinstance(state, dict) and state.get("screen"):
        mcp("mc.client.keyboard.press", {"key": "escape"})
        time.sleep(0.5)
    run("/tp @s " + " ".join(first))
    mcp("mc.client.command.run", {"command": f"vnaprec hud {'true' if opts['hud'] else 'clean'}"})
    time.sleep(opts["lead"])
    if opts["rate"] != 20:
        run(f"/tick rate {opts['rate']}")
    t0 = time.time()
    mcp("mc.client.command.run", {"command": cam_command(secs, cam)})
    mcp("mc.client.command.run", {"command": f"vnaprec start {name}"})
    pending = sorted(opts["at"], key=lambda a: a[0])
    while time.time() - t0 < secs:
        while pending and time.time() - t0 >= pending[0][0]:
            threading.Thread(target=run, args=(pending.pop(0)[1],)).start()
        time.sleep(0.05)
    mcp("mc.client.command.run", {"command": "vnaprec stop"})
    mcp("mc.client.command.run", {"command": "vnapbot stop"})
    if opts["rate"] != 20:
        run("/tick rate 20")
    mcp("mc.client.command.run", {"command": "vnaprec hud true"})
    release()
    time.sleep(2.5)
    subprocess.run(["python3", str(TOOLS1 / "mux.py"), str(REC), name, str(out)], check=True)
    subprocess.run(["python3", str(TOOLS1 / "frames.py"), str(out / f"{name}.mp4"), str(out / f"{name}.png")], check=True)


def main():
    a = sys.argv[1:]
    if a[0] == "preview":
        preview(a[1], [x for i, x in enumerate(a[2:]) if a[2:][i - 1] == "--pre"] if len(a) > 2 else [])
    elif a[0] == "lang":
        lang(a[1])
    elif a[0] == "take":
        name, secs, cam, rest = a[1], float(a[2]), a[3], a[4:]
        opts = {"rate": 20, "hud": False, "at": [], "pre": [], "lead": 2.0}
        while rest:
            k = rest.pop(0)
            if k == "--rate":
                opts["rate"] = int(rest.pop(0))
            elif k == "--hud":
                opts["hud"] = True
            elif k == "--at":
                opts["at"].append((float(rest.pop(0)), rest.pop(0)))
            elif k == "--pre":
                opts["pre"].append(rest.pop(0))
            elif k == "--lead":
                opts["lead"] = float(rest.pop(0))
        take(name, secs, cam, opts)


if __name__ == "__main__":
    main()

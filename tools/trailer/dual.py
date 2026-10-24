#!/usr/bin/env python3
"""One take on both clients at once (EN = dev client, RU = run-c2): the same camera move, recorded together, so a
one-off event (a wedding, a fight) is filmed in both languages.

  dual.py NAME SECONDS "CAM" [--rate N] [--hud] [--at T CMD]... [--pre CMD]...
CAM as in shoot.py. Commands ('/x') go to the server as Dev.
"""
import json
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import shoot  # noqa: E402

REPO = shoot.REPO
RUN = REPO / "neoforge/versions/1.21.1"
CLIENTS = {"en": (RUN / "run/mcp/server.json", RUN / "run/recordings"),
           "ru": (RUN / "run-c2/mcp/server.json", RUN / "run-c2/recordings")}


def client(lang, tool, args=None):
    env = dict(os.environ, VL_MCP=str(CLIENTS[lang][0]))
    out = subprocess.run(["python3", str(REPO / "tools/dev/mcp.py"), tool, json.dumps(args or {})], capture_output=True, text=True, env=env)
    if out.returncode:
        raise SystemExit(f"{lang} {tool}: {out.stderr.strip()}")


def both(command):
    threads = [threading.Thread(target=client, args=(lang, "mc.client.command.run", {"command": command})) for lang in CLIENTS]
    for t in threads:
        t.start()
    for t in threads:
        t.join()


def main():
    a = sys.argv[1:]
    name, secs, cam, rest = a[0], float(a[1]), a[2], a[3:]
    rate, hud, at, pre = 20, False, [], []
    while rest:
        k = rest.pop(0)
        if k == "--rate":
            rate = int(rest.pop(0))
        elif k == "--hud":
            hud = True
        elif k == "--at":
            at.append((float(rest.pop(0)), rest.pop(0)))
        elif k == "--pre":
            pre.append(rest.pop(0))
    for c in pre:
        shoot.run(c)
    first = " ".join(shoot.poses(cam)[0])
    shoot.run(f"/tp Dev {first}")
    shoot.run(f"/tp Dev2 {first}")
    both(f"vnaprec hud {'true' if hud else 'false'}")
    time.sleep(2.5)
    if rate != 20:
        shoot.run(f"/tick rate {rate}")
    t0 = time.time()
    both(shoot.cam_command(secs, cam))
    both(f"vnaprec start {name}")
    pending = sorted(at)
    while time.time() - t0 < secs:
        while pending and time.time() - t0 >= pending[0][0]:
            threading.Thread(target=shoot.run, args=(pending.pop(0)[1],)).start()
        time.sleep(0.05)
    both("vnaprec stop")
    if rate != 20:
        shoot.run("/tick rate 20")
    both("vnaprec hud true")
    time.sleep(3)
    for lang, (_, rec) in CLIENTS.items():
        out = shoot.HOME / "work" / lang
        out.mkdir(parents=True, exist_ok=True)
        subprocess.run(["python3", str(shoot.HOME / "tools/mux.py"), str(rec), name, str(out)], check=True)
        subprocess.run(["python3", str(shoot.HOME / "tools/frames.py"), str(out / f"{name}.mp4"), str(out / f"{name}.png")], check=True)


if __name__ == "__main__":
    main()

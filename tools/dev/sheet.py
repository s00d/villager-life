#!/usr/bin/env python3
"""Nine views in one picture: a player (default Dev2 on the second client) is put at each place in turn as a spectator,
a screenshot taken, and the shots laid out 3 by 3 with their labels (one look instead of nine).

  python3 tools/dev/sheet.py OUT.png [--server 1] [--player Dev2] [--mcp run-c2] [--dim minecraft:overworld] \
      "label|x y z yaw pitch" ...

Up to nine places; the server is a tools/dev/server.py instance, the client the one whose MCP file is
neoforge/versions/1.21.1/<mcp>/mcp/server.json.
"""
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("views", nargs="+")
    ap.add_argument("--server", default="1")
    ap.add_argument("--player", default="Dev2")
    ap.add_argument("--mcp", default="run-c2")
    ap.add_argument("--dim", default="minecraft:overworld")
    ap.add_argument("--wait", type=float, default=3.0)
    a = ap.parse_args()
    env = dict(os.environ, VL_MCP=str(ROOT / "neoforge/versions/1.21.1" / a.mcp / "mcp/server.json"))
    cmd = lambda *c: subprocess.run([sys.executable, str(ROOT / "tools/dev/server.py"), "cmd", a.server, *c], capture_output=True, text=True)
    # a dead player shows the death screen in every shot: back to life first
    subprocess.run([sys.executable, str(ROOT / "tools/dev/mcp.py"), "mc.client.screen.widget.click", '{"message":"Respawn"}'], capture_output=True, env=env)
    time.sleep(1)
    cmd(f"/gamemode spectator {a.player}")
    shots = []
    for view in a.views[:9]:
        label, where = view.split("|", 1) if "|" in view else (view, view)
        cmd(f"/execute in {a.dim} run tp {a.player} {where}")
        time.sleep(a.wait)
        out = subprocess.run([sys.executable, str(ROOT / "tools/dev/mcp.py"), "mc.client.screenshot.take"], capture_output=True, text=True, env=env).stdout
        try:
            shots.append((label, Image.open(json.loads(out)["absolutePath"]).convert("RGB")))
        except (ValueError, KeyError, OSError):
            print(f"no shot for {label}: {out.strip()}", file=sys.stderr)
    if not shots:
        sys.exit(1)
    w, h = 640, 360
    sheet = Image.new("RGB", (w * 3, h * ((len(shots) + 2) // 3)), "black")
    for i, (label, img) in enumerate(shots):
        x, y = (i % 3) * w, (i // 3) * h
        sheet.paste(img.resize((w, h)), (x, y))
        d = ImageDraw.Draw(sheet)
        d.rectangle((x, y, x + 8 * len(label) + 10, y + 18), fill="black")
        d.text((x + 5, y + 3), label, fill="white")
    sheet.save(a.out)
    print(a.out)


if __name__ == "__main__":
    main()

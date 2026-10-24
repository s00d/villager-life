#!/usr/bin/env python3
"""Copies the village structures saved with a structure block in the dev world into the mod.

A structure block in SAVE mode named villager_life:village/<name> writes
run/saves/<world>/generated/villager_life/structure/village/<name>.nbt; this copies every such file to
neoforge/src/main/resources/data/villager_life/structure/village/ and into the built resources, so /reload
in the running client picks it up (/vnap schem <name> sets it down). Load one for editing with a structure block in
LOAD mode under the same name: the game reads it from the mod.
usage: python3 tools/dev/pull_structures.py ["World name"]
"""
import os
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORLD = sys.argv[1] if len(sys.argv) > 1 else "New World"
NS = "villager_life"
SAVED = ROOT / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1") / "run/saves" / WORLD / "generated" / NS / "structure/village"
TARGETS = [ROOT / "neoforge/src/main/resources/data" / NS / "structure/village",
           ROOT / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1") / "build/resources/main/data" / NS / "structure/village"]

if not SAVED.is_dir():
    sys.exit(f"nothing saved under {SAVED}")
for path in sorted(SAVED.rglob("*.nbt")):
    name = path.relative_to(SAVED)
    for target in TARGETS:
        (target / name).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target / name)
    print(f"village/{name.with_suffix('')}")

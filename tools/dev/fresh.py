#!/usr/bin/env python3
"""Finds villages no run has touched yet and writes a scenario for them: the player flies (spectator) to a far point,
/locate finds the nearest village of each kind, and every one gets three days with a creative player above its bell.

  python3 tools/dev/fresh.py X Z [plains desert savanna taiga snowy] [--days N] [--out tools/dev/scenarios/fresh.txt]

Pick X Z far from any point used before (a run leaves its villages changed); the found ones are printed too.
"""
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mcc import run  # noqa: E402

FOUND = re.compile(r"\[(-?\d+), ~, (-?\d+)\]")


def main(args: list[str]) -> int:
    rest = list(args)
    x, z = int(rest.pop(0)), int(rest.pop(0))
    days, out, kinds = 3, Path("tools/dev/scenarios/fresh.txt"), []
    while rest:
        arg = rest.pop(0)
        if arg == "--days":
            days = int(rest.pop(0))
        elif arg == "--out":
            out = Path(rest.pop(0))
        else:
            kinds.append(arg)
    kinds = kinds or ["plains", "desert", "savanna", "taiga", "snowy"]
    run("/gamemode spectator")
    run(f"/tp Dev {x} 160 {z}")
    time.sleep(5)
    places = []
    for kind in kinds:
        answer = " ".join(run(f"/locate structure minecraft:village_{kind}", wait=30))
        found = FOUND.search(answer)
        if found is None:
            print(f"{kind}: none ({answer[:120]})")
            continue
        places.append((kind, int(found.group(1)), int(found.group(2))))
        print(f"{kind}: {found.group(1)} {found.group(2)}")
    lines = [f"# Fresh villages of the dev world (found with tools/dev/fresh.py from {x} {z}): {days} days each with a creative",
             "# player above the bell; then tools/dev/deaths.py --since <start> sums up the losses.",
             "/gamemode creative"]
    for kind, px, pz in places:
        lines += [f"# {kind}", f"/tp Dev {px} 140 {pz}", "sleep 8", "/time set 1000", f"sprint {24000 * days}",
                  "deaths", "expect check: 0 problems :: /vnap check 96", "expect-no No villager :: /vnap mayor info"]
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

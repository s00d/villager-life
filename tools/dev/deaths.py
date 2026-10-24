#!/usr/bin/env python3
"""Sums up the villagers' losses in the dev client's log: the "vnap death" / "vnap gone" / "vnap shelter" lines.

  python3 tools/dev/deaths.py [/tmp/vl-client.log] [--since HH:MM:SS] [--last-run]  (--last-run: a server log's last start only)

Per village (deaths within 96 blocks of each other): how many, how many homeless, at night, indoors, by what, and the
spots where two or more died (a trap: a pit before a door, a lane between trapdoors), then the shelters sought.
"""
import re
import sys
from pathlib import Path
from collections import Counter

DEATH = re.compile(r"\[(\d\d:\d\d:\d\d)\].*vnap death: (\S+) by (\S+)(?: (\S+)@(-?\d+), (-?\d+), (-?\d+) light=(\d+) roofed=(\w+))?(?: -)? at (-?\d+), (-?\d+), (-?\d+) "
                   r"time (\d+) (housed|homeless) sky=(\w+) asleep=(\w+) act=(\S+) bed=(-?\d+)")
OLD_AGE = re.compile(r"\[(\d\d:\d\d:\d\d)\].*vnap death: (\S+) by old age at (-?\d+), (-?\d+), (-?\d+)")
GONE = re.compile(r"\[(\d\d:\d\d:\d\d)\].*vnap gone: (\S+) (.*)")
SHELTER = re.compile(r"\[(\d\d:\d\d:\d\d)\].*vnap shelter: (\S+) from (.*) to (-?\d+), (-?\d+), (-?\d+) time (\d+)")


def main(args: list[str]) -> int:
    path = "/tmp/vl-client.log"
    since = ""
    last_run = False
    rest = list(args)
    while rest:
        arg = rest.pop(0)
        if arg == "--since":
            since = rest.pop(0)
        elif arg == "--last-run":
            last_run = True
        else:
            path = arg
    deaths, old, gone, shelters = [], [], [], Counter()
    if last_run:
        # a server's log keeps all its runs (server.py adds each after a ===== line): the last one only, read from the end
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        from server import last_run as last
        lines = last(Path(path)).splitlines()
    else:
        lines = open(path, encoding="utf-8", errors="replace")
    for line in lines:
        if (m := DEATH.search(line)) and m.group(1) >= since:
            deaths.append(m)
        elif (m := OLD_AGE.search(line)) and m.group(1) >= since:
            old.append(m)
        elif (m := GONE.search(line)) and m.group(1) >= since:
            gone.append(m)
        elif (m := SHELTER.search(line)) and m.group(1) >= since:
            shelters[(m.group(2), m.group(4), m.group(5), m.group(6))] += 1
    villages: list[list] = []
    for m in deaths:
        x, z = int(m.group(10)), int(m.group(12))
        for village in villages:
            if abs(village[0][0] - x) <= 96 and abs(village[0][1] - z) <= 96:
                village[1].append(m)
                break
        else:
            villages.append([(x, z), [m]])
    print(f"{len(deaths)} killed, {len(old)} of old age, {sum('zombie' in g.group(3) for g in gone)} turned, "
          f"{sum('hamlet' in g.group(3) for g in gone)} left for a hamlet")
    for (x, z), ms in villages:
        night = sum(12500 <= int(m.group(13)) < 23400 for m in ms)
        print(f"\n~{x} {z}: {len(ms)} killed; homeless {sum(m.group(14) == 'homeless' for m in ms)}, at night {night}, "
              f"indoors {sum(m.group(15) == 'false' for m in ms)}, asleep {sum(m.group(16) == 'true' for m in ms)}")
        print("  by " + ", ".join(f"{k} {n}" for k, n in Counter(m.group(4) or m.group(3) for m in ms).most_common()))
        spots = Counter((int(m.group(10)) // 3, int(m.group(12)) // 3) for m in ms)
        for (sx, sz), n in spots.most_common():
            if n < 2:
                break
            print(f"  {n} died by {sx * 3} {sz * 3}: a trap?")
        for m in ms:
            print(f"  {m.group(1)} {m.group(2)} t{m.group(13)} {m.group(14)} {m.group(17)} bed={m.group(18)} at {m.group(10)} {m.group(11)} {m.group(12)}"
                  f" by {m.group(4) or m.group(3)} light={m.group(8)} roofed={m.group(9)}")
    if shelters:
        print("\nshelters (villager, to, times sent; many times = it never got there):")
        for (name, x, y, z), n in shelters.most_common():
            print(f"  {name} -> {x} {y} {z}: {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

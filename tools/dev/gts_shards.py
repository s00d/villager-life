#!/usr/bin/env python3
"""Runs all the game tests on several test servers at once (each is one thread: the run waits on CPU, the Mac has many).

  python3 tools/dev/gts_shards.py [shards]        (tools/dev/gts.sh calls it with no filter)

The tests are found in the sources (@GameTest, its batch), the batches dealt out to the servers by what each cost last
time (the "vnap test:" lines of the last log; the big default batch is split by test). Every server gets its own
game directory (a world is locked to one process) with the dev run's config, the java command ModDevGradle writes for
runGameTestServer (its launch script), and -Dvl.tests with its tests (GameTestServerMixin). Logs: /tmp/vl-gts-<n>.log, all of them in
/tmp/vl-gts.log. Exit code 1 unless every server says all its tests passed.
"""
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NODE = ROOT / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1")
MODDEV = NODE / "build/moddev"
LOGS = Path(os.environ.get("VL_LOGS", "/tmp"))
LOG = LOGS / "vl-gts.log"
TEST = re.compile(r'@GameTest\(([^)]*)\)\s*\n\s*public static void (\w+)\(GameTestHelper')


def tests() -> dict[str, list[str]]:
    """batch -> test names (lower case, as the game names them)."""
    out = defaultdict(list)
    for source in (ROOT / "neoforge/src/gametest/java").rglob("*.java"):
        for args, name in TEST.findall(source.read_text(encoding="utf-8")):
            batch = re.search(r'batch = "(\w+)"', args)
            out[batch.group(1) if batch else "defaultBatch"].append(name.lower())
    return out


def history() -> dict[str, float]:
    """batch -> seconds it costs, from the last run's "vnap test:" lines: its longest test (the batch waits for it)
    and a tenth of a second a test for setting out its ground."""
    if not LOG.exists():
        return {}
    longest, count = defaultdict(float), defaultdict(int)
    for m in re.finditer(r"vnap test: (\w+)(?::\d+)? \S+ \d+t (\d+)ms", LOG.read_text(encoding="utf-8", errors="replace")):
        longest[m.group(1)] = max(longest[m.group(1)], int(m.group(2)) / 1000.0)
        count[m.group(1)] += 1
    return {batch: longest[batch] + 0.1 * count[batch] for batch in longest}


def launch() -> list[str]:
    """The java command ModDevGradle wrote for runGameTestServer (createGameTestServerLaunchScript), without its cd."""
    script = MODDEV / "runGameTestServer.sh"
    line = next(row for row in script.read_text().splitlines() if "exec " in row)
    return shlex.split(line.split("exec ", 1)[1].rstrip(")"))


def deal(shards: int) -> list[list[str]]:
    batches, took = tests(), history()
    units = []
    for batch, names in batches.items():
        cost = took.get(batch, 0.15 * len(names))
        if batch == "defaultBatch":
            # a batch of its own on each server: split by test, the cost shared out
            per = max(1, -(-len(names) // shards))
            for i in range(0, len(names), per):
                part = names[i:i + per]
                units.append((cost * len(part) / len(names), part))
        else:
            units.append((cost, [batch]))
    load = [0.0] * shards
    out = [[] for _ in range(shards)]
    for cost, names in sorted(units, key=lambda unit: -unit[0]):
        least = load.index(min(load))
        load[least] += cost
        out[least].extend(names)
    return out


def main(args: list[str]) -> int:
    shards = int(args[0]) if args else 4
    plan = deal(shards)
    procs = []
    for n, names in enumerate(plan):
        home = NODE / "run-gts" / str(n)
        if home.exists():
            shutil.rmtree(home)
        home.mkdir(parents=True)
        # the dev run's configs, when the version has run before (a fresh node starts with the mod's defaults)
        if (NODE / "run/config").is_dir():
            shutil.copytree(NODE / "run/config", home / "config")
        log = open(f"{LOGS}/vl-gts-{n}.log", "w", encoding="utf-8")
        # ModDevGradle's JVM arguments with this server's tests in place of its empty -Dvl.tests (the file ends in the
        # main class, so a -D after it would be the program's)
        vm = MODDEV / "gameTestServerRunVmArgs.txt"
        own = home / "vmargs.txt"
        own.write_text(re.sub(r"-Dvl\.tests=\S*", lambda _: f"-Dvl.tests={','.join(names)}", vm.read_text()))
        command = [str(own) if part == f"@{vm}" else part for part in launch()]
        command = [f"@{own}" if part == str(own) else part for part in command]
        procs.append((n, subprocess.Popen(command, cwd=home, stdout=log, stderr=subprocess.STDOUT), log))
    for n, proc, log in procs:
        proc.wait()
        log.close()
    ok = True
    with open(LOG, "w", encoding="utf-8") as everything:
        for n, _, _ in procs:
            text = Path(f"{LOGS}/vl-gts-{n}.log").read_text(encoding="utf-8", errors="replace")
            everything.write(text)
            verdict = re.findall(r"All \d+ required tests passed|\d+ required tests failed", text)
            print(f"server {n}: {verdict[-1] if verdict else f'no verdict (crashed? see {LOGS}/vl-gts-{n}.log)'}")
            ok &= bool(verdict) and verdict[-1].startswith("All")
            for line in text.splitlines():
                if "failed at" in line or "Exception in" in line:
                    print("   ", line[:300])
    return 0 if ok else 1


if __name__ == "__main__":
    started = time.time()
    code = main(sys.argv[1:])
    print(f"servers took {time.time() - started:.0f}s")
    sys.exit(code)

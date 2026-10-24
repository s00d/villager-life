#!/usr/bin/env python3
"""Fresh villages of several world seeds, each on a dedicated server of its own, all at once; one summary at the end.

  python3 tools/dev/sweep.py [--seeds 1111,2222,3333] [--kinds plains,desert,savanna,taiga,snowy] [--per-seed 2]
                             [--days 3] [--parallel 8] [--base 1] [--sleep]

For every seed a server generates the world and /locate finds the nearest village of each kind (the nearest
--per-seed of them are taken); then every village gets a server of its own (the same seed, the bot over its bell)
and a scenario of --days days with monsters: deaths, /vnap check, the stall and the village day every morning
(--sleep: each night is slept away with /time add, as the players' sleeping does). At most --parallel servers run at
once (each a JVM of 3 GB and about a core). Servers are numbered from --base. Logs: /tmp/vl-server-N.log,
/tmp/vl-sweep-N.log (scenario); the summary is printed and written to /tmp/vl-sweep.md.
"""
import re
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from server import last_run  # noqa: E402

HERE = Path(__file__).resolve().parent
SCRATCH = Path("/tmp/vl-sweep")
FOUND = re.compile(r"village_(\w+) is at \[(-?\d+), ~, (-?\d+)\] \((\d+) blocks")
IGNORED = ("server.properties", "UnixException", "NoSuchFile")


def sh(*args: str, log: Path | None = None) -> str:
    if log is None:
        return subprocess.run([sys.executable, *args], cwd=HERE.parents[1], capture_output=True, text=True).stdout
    with open(log, "w") as out:
        subprocess.run([sys.executable, *args], cwd=HERE.parents[1], stdout=out, stderr=subprocess.STDOUT)
    return log.read_text(errors="replace")


def server(*args: str) -> str:
    return sh(str(HERE / "server.py"), *args)


def locate(n: int, seed: str, kinds: list[str], per_seed: int) -> list[tuple]:
    started = server("start", str(n), "--seed", seed, "--xmx", "3G")
    if "up (" not in started:
        # a server left up (by another run or session) would answer for its own world, not this seed's
        print(f"seed {seed}: server {n} did not start ({started.strip()[:120]}), skipped", flush=True)
        return []
    found = []
    try:
        for kind in kinds:
            answer = server("cmd", str(n), f"execute as Dev at Dev run locate structure minecraft:village_{kind}")
            m = FOUND.search(answer)
            if m:
                found.append((seed, m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4))))
    finally:
        server("stop", str(n))
    return sorted(found, key=lambda v: v[4])[:per_seed]


def scenario(days: int, sleep: bool) -> str:
    lines = ["/gamerule doMobSpawning true", "/gamemode creative", "sleep 10", "/time set 1000"]
    for _ in range(days):
        if sleep:
            lines += ["sprint 12500", "/time add 11500", "sprint 1500"]
        else:
            lines += ["sprint 24000"]
        lines += ["deaths", "expect check: 0 problems :: /vnap check 96", "expect . :: /vnap shop info", "expect . :: /vnap day", "expect housing: :: /vnap housing"]
    lines += ["expect . :: /vnap who 96", "expect . :: /vnap houses", "expect . :: /vnap town"]
    return "\n".join(lines) + "\n"


def run(n: int, village: tuple, days: int, sleep: bool) -> dict:
    seed, kind, x, z, _ = village
    path = SCRATCH / f"s{n}.txt"
    path.write_text(scenario(days, sleep))
    started = server("start", str(n), "--seed", seed, "--xmx", "3G", "--at", str(x), "130", str(z))
    if "up (" not in started:
        return {"n": n, "village": f"{seed} {kind} {x} {z}", "error": "server did not start"}
    try:
        out = sh(str(HERE / "scenario.py"), "--server", str(n), str(path), log=Path(f"/tmp/vl-sweep-{n}.log"))
    finally:
        server("stop", str(n))
    # the log keeps the server's earlier runs above a ===== line: this run's only
    text = last_run(Path(f"/tmp/vl-server-{n}.log"))
    deaths = sum(int(d) for d in re.findall(r"deaths: (\d+)", out))
    stalls = re.findall(r"stall [^:]+: ([^;]+);.*?made up (\d+)c", out)
    traps = re.findall(r"(\d+) died by (-?\d+ -?\d+): a trap", sh(str(HERE / "deaths.py"), f"/tmp/vl-server-{n}.log", "--last-run"))
    return {
        "n": n, "village": f"{seed} {kind} {x} {z}", "deaths": deaths,
        "failed": [line.strip() for line in out.splitlines() if "FAIL" in line] + [line.strip() for line in out.splitlines()
                                                                                     if line.startswith("    problem")],
        "stall": stalls[-1][0] if stalls else "-", "minted": int(stalls[-1][1]) if stalls else 0,
        "big_mints": [m for m in re.findall(r"vnap mint: (\d+)c at [^ ]+, [^ ]+, [^ ]+ for ([^\n]+)", text) if int(m[0]) >= 1000],
        "errors": [line[:200] for line in text.splitlines() if ("Exception" in line or "/ERROR]" in line) and not any(i in line for i in IGNORED)][:5],
        "slow": text.count("vnap slow:"), "traps": traps,
        "day": (re.findall(r"village day (\d+)", out) or ["?"])[-1],
    } | ({"failed": ["no villagers at the village found by locate"]} if "0 villagers within" in out and not stalls else {})


def main(args: list[str]) -> int:
    seeds, kinds, per_seed, days, parallel, base, sleep = ["1111", "2222", "3333"], ["plains", "desert", "savanna", "taiga", "snowy"], 2, 3, 8, 1, False
    while args:
        arg = args.pop(0)
        if arg == "--seeds":
            seeds = args.pop(0).split(",")
        elif arg == "--kinds":
            kinds = args.pop(0).split(",")
        elif arg == "--per-seed":
            per_seed = int(args.pop(0))
        elif arg == "--days":
            days = int(args.pop(0))
        elif arg == "--parallel":
            parallel = int(args.pop(0))
        elif arg == "--base":
            base = int(args.pop(0))
        elif arg == "--sleep":
            sleep = True
    SCRATCH.mkdir(exist_ok=True)
    print(server("build").strip())
    with ThreadPoolExecutor(max_workers=min(parallel, len(seeds))) as pool:
        villages = [v for found in pool.map(lambda i: locate(base + i, seeds[i], kinds, per_seed), range(len(seeds))) for v in found]
    print(f"{len(villages)} villages:", ", ".join(f"{v[0]} {v[1]} {v[2]} {v[3]}" for v in villages))
    results, lock = [], threading.Lock()

    def one(i: int) -> None:
        result = run(base + i, villages[i], days, sleep)
        with lock:
            results.append(result)
            print(f"  done {result['village']}: deaths {result.get('deaths')}, {result.get('stall')}", flush=True)

    with ThreadPoolExecutor(max_workers=parallel) as pool:
        list(pool.map(one, range(len(villages))))
    results.sort(key=lambda r: r["n"])
    report = ["| server | village | day | deaths | traps | check failed | stall | minted | big mints | slow | errors |", "|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in results:
        if "error" in r:
            report.append(f"| {r['n']} | {r['village']} | | | | {r['error']} | | | | | |")
            continue
        report.append(f"| {r['n']} | {r['village']} | {r['day']} | {r['deaths']} | {len(r['traps'])} | {len(r['failed'])} | {r['stall']} | {r['minted']} "
                      f"| {len(r['big_mints'])} | {r['slow']} | {len(r['errors'])} |")
    details = []
    for r in results:
        for key in ("failed", "big_mints", "traps", "errors"):
            for item in r.get(key, []):
                details.append(f"- {r['n']} {r['village']} {key}: {item}")
    text = "\n".join(report + [""] + details) + "\n"
    Path("/tmp/vl-sweep.md").write_text(text)
    print(text)
    return 1 if any(r.get("failed") or r.get("errors") or "error" in r for r in results) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Runs a recorded scenario in the dev client (start it with tools/dev/restart.sh) and checks what the chat says.

  python3 tools/dev/scenario.py tools/dev/scenarios/village.txt [more.txt ...]
  python3 tools/dev/scenario.py --server N tools/dev/scenarios/village.txt   on dedicated server N (tools/dev/server.py):
                                commands go over RCON as the bot ("execute as Dev at Dev run ..."), sprints and
                                deaths are read from /tmp/vl-server-N.log; one scenario per server, servers side by side

A scenario is plain text, one step per line:
  # a comment
  /tp Dev 280 115 1385          a command; its chat answer is printed
  sleep 5                       seconds of real time
  sprint 24000                  /tick sprint, then wait for "Sprint completed"
  expect REGEX :: /command      run the command, fail unless its chat answer matches REGEX
  expect-no REGEX :: /command   run the command, fail if its chat answer matches REGEX
  deaths                        the villagers lost since the last "deaths" (or the start): the client log's
                                "vnap death" / "vnap gone" lines
Exit code 1 when an expectation failed. One scenario runs at a time in the client, and one per server (a lock file):
two at once teleport the player between their villages and both measure nothing.
"""
import fcntl
import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mcc  # noqa: E402
import server  # noqa: E402

LOG = mcc.LOG
# where this scenario's part of a server's log starts (the log keeps all the server's runs)
LOG_START = 0
# one per client or server: the default client's lock keeps its old name
LOCK = Path("/tmp/vl-scenario.lock") if str(LOG) == "/tmp/vl-client.log" else Path(f"{LOG}.lock")
SERVER: int | None = None


def run(command: str) -> list[str]:
    if SERVER is None:
        return mcc.run(command)
    return server.rcon(SERVER, f"execute as Dev at Dev run {command.lstrip('/')}").splitlines()


def done_sprints() -> list[str]:
    if SERVER is None:
        return [line for line in mcc.chat_lines() if "Sprint completed" in line or "Перемотка завершена" in line]
    # this scenario's part of the log only (it keeps all the server's runs)
    with open(LOG, "rb") as f:
        f.seek(LOG_START)
        text = f.read().decode(errors="replace")
    return [line.split("]: ", 1)[-1] for line in text.splitlines() if "Sprint completed" in line]


def losses(since: int) -> tuple[list[str], int]:
    """The "vnap death"/"vnap gone" lines of the client log after byte offset since, and the new offset."""
    data = LOG.read_bytes()
    text = data[since:].decode("utf-8", "replace")
    found = [line.split("]: ", 1)[-1] for line in text.splitlines() if "vnap death:" in line or "vnap gone:" in line]
    return found, len(data)


def sprint(ticks: int) -> str:
    before = len(done_sprints())
    run(f"/tick sprint {ticks}")
    deadline = time.time() + max(120.0, ticks / 40.0)
    while time.time() < deadline:
        lines = done_sprints()
        if len(lines) > before:
            return lines[-1]
        time.sleep(2)
    raise TimeoutError(f"sprint {ticks} did not finish")


def main(paths: list[str]) -> int:
    global LOG, LOCK, SERVER, LOG_START
    if paths[:1] == ["--server"]:
        SERVER = int(paths[1])
        paths = paths[2:]
        LOG = server.log(SERVER)
        LOCK = Path(f"{LOG}.lock")
        LOG_START = LOG.stat().st_size if LOG.exists() else 0
    lock = LOCK.open("w")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        print(f"another scenario is running (lock {LOCK}): stop it first")
        return 2
    lock.write(str(os.getpid()))
    lock.flush()
    failed = 0
    mark = LOG.stat().st_size
    for path in paths:
        print(f"== {path}")
        for number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("sleep "):
                time.sleep(float(line.split()[1]))
            elif line == "deaths":
                found, mark = losses(mark)
                print(f"{number}: deaths: {len(found)}" + "".join(f"\n    {one[:300]}" for one in found))
            elif line.startswith("sprint "):
                print(f"{number}: {sprint(int(line.split()[1]))}")
            elif line.startswith(("expect ", "expect-no ")):
                kind, rest = line.split(" ", 1)
                pattern, command = (part.strip() for part in rest.split("::", 1))
                answer = " | ".join(run(command))
                hit = re.search(pattern, answer) is not None
                ok = hit if kind == "expect" else not hit
                failed += not ok
                print(f"{number}: {'PASS' if ok else 'FAIL'} {kind} /{pattern}/ {command}\n    {answer[:2000]}")
            else:
                print(f"{number}: {line} -> {' | '.join(run(line))[:400]}")
    print(f"== {'all expectations met' if failed == 0 else f'{failed} expectation(s) failed'}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

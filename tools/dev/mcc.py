#!/usr/bin/env python3
"""Runs slash commands in the dev client as its player, one per argument, and prints what each answered; 'sleep N'
pauses.

  python3 tools/dev/mcc.py '/time set noon' 'sleep 2' '/tp Dev 0 100 0'

In a singleplayer world the answer comes from mc.server.command.run itself. A client on a dedicated server has no
mc.server.* tools: the command goes through the client and its chat lines are read from the client's log.
"""
import json
import os
import sys
import time
from pathlib import Path

from mcp import tool

# the client's own output (tools/dev/restart.sh writes it); run/logs/latest.log is overwritten by every GameTest run
LOG = Path(os.environ.get("VL_CLIENT_LOG", "/tmp/vl-client.log"))


def chat_lines() -> list[str]:
    text = LOG.read_bytes().decode("utf-8", "replace")
    return [line.split("[CHAT] ", 1)[1] for line in text.splitlines() if "[CHAT] " in line]


def by_chat(command: str, wait: float = 5.0) -> list[str]:
    """Runs one command through the client; the chat lines it answered with: up to wait seconds for the first, then 0.4 s for the rest."""
    before = len(chat_lines())
    tool("mc.client.command.run", {"command": command})
    deadline, lines = time.time() + wait, []
    while time.time() < deadline:
        time.sleep(0.4)
        found = chat_lines()[before:]
        if found and found == lines:
            return found
        lines = found
    return lines


_PLAYER: list[str | None] = []


def run(command: str, player: str | None = None) -> list[str]:
    command = command.lstrip("/")
    if player is None:
        if not _PLAYER:
            _PLAYER.append(json.loads(tool("mc.client.state")).get("playerName"))
        player = _PLAYER[0]
    if player is not None:
        try:
            return json.loads(tool("mc.server.command.run", {"command": command, "as": player})).get("output", [])
        except RuntimeError as error:
            # a refused or failed command is an answer too; only "no server here" falls back to the chat
            if "mc.remote.call" not in str(error):
                return [str(error)]
    return by_chat(command)


if __name__ == "__main__":
    for command in sys.argv[1:]:
        if command.startswith("sleep "):
            time.sleep(float(command.split()[1]))
            continue
        lines = run(command)
        print(command, "->", " | ".join(lines) if lines else "(no answer)")

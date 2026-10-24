#!/usr/bin/env python3
"""Calls the Minecraft MCP mod (s00d/minecraft-mcp 0.6.0) inside a running game.

  python3 tools/dev/mcp.py --list                        # every tool and its argument names
  python3 tools/dev/mcp.py mc.client.state
  python3 tools/dev/mcp.py mc.client.world.join '{"name":"New World","wait":true}'
  python3 tools/dev/mcp.py mc.server.command.run '{"command":"time set noon"}'   # singleplayer: the answer comes back
  python3 tools/dev/mcp.py mc.client.block.interact '{"x":1,"y":64,"z":2,"face":"south","hand":"main"}'
  python3 tools/dev/mcp.py mc.client.screenshot.take     # -> neoforge/versions/1.21.1/run/screenshots/

Each game listens on a fixed port: the dev client 25580 (the mod's default), the second client 25582, the production
client 25583, dedicated server N 25800+N. The game writes its URL to <game dir>/mcp/server.json on every start;
VL_MCP points at another game's file (tools/dev/client2.sh: neoforge/versions/1.21.1/run-c2/mcp/server.json).
A failing tool is a successful call with isError: printed to stderr, exit code 1.
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

INFO = Path(os.environ.get("VL_MCP", Path(__file__).resolve().parents[2] / "neoforge/versions" / os.environ.get("VL_MC", "1.21.1") / "run/mcp/server.json"))


def call(method, params):
    info = json.loads(INFO.read_text())
    headers = {"Content-Type": "application/json"}
    # a token only when the game was given one (the default loopback bind takes none)
    if info.get("authToken"):
        headers["Authorization"] = "Bearer " + info["authToken"]
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode()
    with urllib.request.urlopen(urllib.request.Request(info["url"], body, headers), timeout=600) as resp:
        return json.load(resp)


def tool(name, args=None):
    """Calls one tool and returns its text payload (usually JSON); a failing tool raises with its reason."""
    reply = call("tools/call", {"name": name, "arguments": args or {}})
    if "result" not in reply:
        raise RuntimeError(json.dumps(reply))
    text = reply["result"]["content"][0]["text"]
    if reply["result"].get("isError"):
        raise RuntimeError(text)
    return text


if __name__ == "__main__":
    if sys.argv[1] == "--list":
        for t in call("tools/list", {})["result"]["tools"]:
            print(t["name"], json.dumps(t.get("inputSchema", {}).get("properties", {}))[:300])
    else:
        try:
            print(tool(sys.argv[1], json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}))
        except RuntimeError as error:
            print(error, file=sys.stderr)
            sys.exit(1)

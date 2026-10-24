#!/usr/bin/env python3
"""A production client of one version (portablemc, not the MDG dev client) joining a dev server, with the MCP mod.

  python3 tools/dev/prod_client.py start 26.3 [--server 127.0.0.1:25608] [--name Tester] [--show]
  python3 tools/dev/prod_client.py stop 26.3
  VL_MCP=$(python3 tools/dev/prod_client.py mcp 26.3) python3 tools/dev/mcp.py mc.client.state

The client lives in neoforge/versions/<v>/run-prod: mods/ gets the mod's jar, the MCP mod (VL_MCP_JARS, a folder of
minecraft_mcp-neoforge-*+<v>.jar from the release, MCP on port 25583, the window hidden unless --show) and JEI (downloaded once into
~/.cache/vl-minecraft/jei). The checks of the later versions run here: the MDG dev client of 1.21.4 on crashed with EMF,
which the mod no longer uses, and has not been tried since. Log: /tmp/vl-prod-<v>.log.
"""
import os
import re
import shutil
import signal
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / ".cache/vl-minecraft"
GRADLE = Path.home() / ".gradle/caches/modules-2/files-2.1"
PORTABLEMC = os.environ.get("VL_PORTABLEMC", "portablemc")
MCP_JARS = Path(os.environ.get("VL_MCP_JARS", Path.home() / ".cache/vl-minecraft/mcp"))
JAVA = {21: "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java",
        25: "/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home/bin/java"}


def props(mc: str) -> dict[str, str]:
    text = (ROOT / "neoforge/versions" / mc / "gradle.properties").read_text()
    return dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))


def cached(group: str, name: str, version: str) -> Path:
    found = sorted((GRADLE / group / name / version).glob(f"*/{name}-{version}.jar"))
    if not found:
        raise SystemExit(f"{group}:{name}:{version} is not in the gradle cache: build the version once")
    return found[0]


def jei(mc: str, version: str) -> Path:
    target = CACHE / "jei" / f"jei-{mc}-neoforge-{version}.jar"
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        url = f"https://maven.blamejared.com/mezz/jei/jei-{mc}-neoforge/{version}/jei-{mc}-neoforge-{version}.jar"
        urllib.request.urlretrieve(url, target)
    return target


def home(mc: str) -> Path:
    return ROOT / "neoforge/versions" / mc / "run-prod"


def start(mc: str, args: list[str]) -> int:
    p = props(mc)
    server = args[args.index("--server") + 1] if "--server" in args else "127.0.0.1:25608"
    name = args[args.index("--name") + 1] if "--name" in args else "Tester"
    dir_ = home(mc)
    mods = dir_ / "mods"
    if mods.exists():
        shutil.rmtree(mods)
    mods.mkdir(parents=True)
    jar = ROOT / "neoforge/versions" / mc / "build/libs" / f"villager-life-{p.get('mod_version', '1.0.0')}+{mc}.jar"
    if not jar.exists():
        jar = next((ROOT / "neoforge/versions" / mc / "build/libs").glob("villager-life-*.jar"))
    shutil.copy2(jar, mods)
    mcp = sorted(MCP_JARS.glob(f"minecraft_mcp-neoforge-*+{mc}.jar"))
    if not mcp:
        print(f"no MCP jar for {mc} in {MCP_JARS}")
        return 1
    shutil.copy2(mcp[-1], mods)
    shutil.copy2(jei(mc, p["jei_version"]), mods)
    # the dev client's other mods (a dev server has them too, and Patchouli's channels are required both ways)
    for extra in (ROOT / "neoforge/versions" / mc / "run/mods").glob("*.jar"):
        if not extra.name.startswith(("minecraft_mcp", "entity_", "jei-")):
            shutil.copy2(extra, mods)
    host, port = server.split(":")
    log = open(f"/tmp/vl-prod-{mc}.log", "w")
    proc = subprocess.Popen([PORTABLEMC, "--main-dir", str(CACHE), "start", "--mc-dir", str(dir_),
                             "--jvm", JAVA[int(p["java_version"])], f"neoforge::{p['neo_version']}", "-u", name,
                             "--join-server", host, "--join-server-port", port],
                            stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
                            # its own MCP port (the dev client has 25580); hidden window unless --show: an automated
                            # run then steals no focus, and screenshots still work
                            env=dict(os.environ, MINECRAFT_MCP_ENDPOINT_PORT="25583",
                                     MINECRAFT_MCP_ENDPOINT_HEADLESS="false" if "--show" in args else "true"))
    (dir_ / "client.pid").write_text(str(proc.pid))
    print(f"client {mc} starting (pid {proc.pid}, log /tmp/vl-prod-{mc}.log, MCP {dir_ / 'mcp/server.json'})")
    return 0


def stop(mc: str) -> int:
    pid = home(mc) / "client.pid"
    if pid.exists():
        try:
            os.killpg(int(pid.read_text()), signal.SIGTERM)
        except ProcessLookupError:
            pass
        pid.unlink()
    print(f"client {mc} stopped")
    return 0


def main(args: list[str]) -> int:
    if len(args) < 2 or not re.fullmatch(r"\d+(\.\d+)+", args[1]):
        print(__doc__)
        return 1
    if args[0] == "start":
        return start(args[1], args[2:])
    if args[0] == "stop":
        return stop(args[1])
    if args[0] == "mcp":
        print(home(args[1]) / "mcp/server.json")
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

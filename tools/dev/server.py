#!/usr/bin/env python3
"""Dedicated servers for long village runs, no window: each on its own copy of a world, with a bot player ("Dev",
/vnap bot) so the town lives as round a real player. Several run side by side, one village each.

  python3 tools/dev/server.py build                          compile the mod, write the server launch files (gradle)
  python3 tools/dev/server.py start N [--world NAME|PATH] [--fresh] [--seed S] [--at X Y Z] [--xmx 4G] [--no-bot]
  python3 tools/dev/server.py cmd N '/time set 1000' 'vnap who 48'   RCON; the answer of each is printed
  python3 tools/dev/server.py stop N                         /vnap save, /stop, wait for the process
  python3 tools/dev/server.py status

A Forge node (VL_MC=1.21.8-forge) runs the production Forge server (its installer's, cached in
~/.cache/vl-minecraft/forge-server/<version>) with the mod's built jar and the MCP mod's Forge jar instead of the dev server.

Instance N lives in neoforge/versions/1.21.1/run/servers/N (world/, config/ copied from run/), plays on port 25600+N,
RCON on 25700+N, the MCP mod on 25800+N; its whole output goes to /tmp/vl-server-N.log (what deaths.py and scenario.py --server N read); each start is added to it after a ===== line.
The world is copied from run/saves/NAME (default "New World") on the first start or with --fresh; the client must
not be saving it meanwhile (copy a world the client is not in, or after tools/dev/restart.sh --stop). --seed S makes a
new world from that seed instead (its villages untouched by any run; the bot starts at its spawn).
"""
import os
import re
import shlex
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MC = os.environ.get("VL_MC", "1.21.1")  # the Stonecutter node: neoforge/versions/<MC>
NODE = ROOT / "neoforge/versions" / MC
RUN = NODE / "run"
LAUNCH = NODE / "build/moddev/runServer.sh"
PASSWORD = "vl"
JAVA_HOME = "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
FORGE = MC.endswith("-forge")
JAVAS = {"21": JAVA_HOME, "25": "/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"}


def node_props() -> dict[str, str]:
    text = (NODE / "gradle.properties").read_text()
    return dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))


def forge_server() -> Path:
    """Forge's production server of this node, installed once by its installer."""
    p = node_props()
    version = f"{p['minecraft_version']}-{p['forge_version']}"
    dir_ = Path.home() / ".cache/vl-minecraft/forge-server" / version
    if not (dir_ / "libraries/net/minecraftforge/forge" / version / "unix_args.txt").exists():
        import urllib.request
        dir_.mkdir(parents=True, exist_ok=True)
        # Forge's maven turns away a request without a user agent (403)
        request = urllib.request.Request(f"https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar",
                                         headers={"User-Agent": "villager-life-dev"})
        with urllib.request.urlopen(request) as answer:
            (dir_ / "installer.jar").write_bytes(answer.read())
        subprocess.run([JAVAS[p["java_version"]] + "/bin/java", "-jar", "installer.jar", "--installServer", "."], cwd=dir_,
                       stdout=open(dir_ / "install.log", "w"), stderr=subprocess.STDOUT, check=True)
    return dir_


def home(n: int) -> Path:
    return RUN / "servers" / str(n)


def log(n: int) -> Path:
    return Path(f"/tmp/vl-server-{n}.log")


# the server's log keeps its runs one after another; past LOG_MAX at a start, only the last runs within LOG_KEEP stay
LOG_MAX = 200 * 1024 * 1024
LOG_KEEP = 50 * 1024 * 1024
RUN_MARK = b"\n===== server "


def trim(path: Path, most: int = LOG_MAX, keep: int = LOG_KEEP) -> None:
    """Cuts the log down to its last runs within keep bytes (from a run's first line), copying the tail, not reading it all."""
    size = path.stat().st_size if path.exists() else 0
    if size <= most:
        return
    with open(path, "rb") as f:
        f.seek(size - keep)
        tail = f.read()
    # from a run's first line; a last run bigger than keep alone, from its first whole line
    cut = tail.find(RUN_MARK)
    tail = tail[cut + 1:] if cut >= 0 else tail[tail.find(b"\n") + 1:]
    tmp = path.with_suffix(".trim")
    tmp.write_bytes(tail)
    tmp.replace(path)


def last_run(path: Path, block: int = 1 << 20) -> str:
    """The log's last run (after its last ===== line), found by reading back from the end a block at a time."""
    if not path.exists():
        return ""
    size = path.stat().st_size
    with open(path, "rb") as f:
        at = size
        buf = b""
        while at > 0:
            step = min(block, at)
            at -= step
            f.seek(at)
            buf = f.read(step) + buf
            i = buf.rfind(RUN_MARK)
            if i >= 0:
                return buf[i + 1:].decode(errors="replace")
        return buf.decode(errors="replace")


def rcon(n: int, command: str, timeout: float = 600.0) -> str:
    """One command over RCON (Minecraft's: one packet out, the answer back; an answer over 4 KB comes in several)."""
    with socket.create_connection(("127.0.0.1", 25700 + n), timeout=timeout) as sock:
        def send(kind: int, body: str, rid: int) -> None:
            data = body.encode("utf-8")
            sock.sendall(struct.pack("<iii", len(data) + 10, rid, kind) + data + b"\0\0")

        def receive() -> tuple[int, str]:
            size = struct.unpack("<i", read(4))[0]
            packet = read(size)
            rid, _ = struct.unpack("<ii", packet[:8])
            return rid, packet[8:-2].decode("utf-8", "replace")

        def read(count: int) -> bytes:
            data = b""
            while len(data) < count:
                chunk = sock.recv(count - len(data))
                if not chunk:
                    raise ConnectionError("RCON closed")
                data += chunk
            return data

        send(3, PASSWORD, 1)
        if receive()[0] == -1:
            raise PermissionError("RCON password refused")
        send(2, command.lstrip("/"), 2)
        rid, body = receive()
        # the rest of a long answer: whatever else arrives straight after
        sock.settimeout(0.3)
        try:
            while True:
                more = receive()[1]
                body += more
        except (socket.timeout, ConnectionError, struct.error):
            pass
        return body


def alive(n: int) -> bool:
    try:
        socket.create_connection(("127.0.0.1", 25700 + n), timeout=1).close()
        return True
    except OSError:
        return False


def pid(n: int) -> int | None:
    found = subprocess.run(["pgrep", "-f", f"vl.server={n} "], capture_output=True, text=True).stdout.split()
    return int(found[0]) if found else None


def build() -> int:
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    tasks = [f":neoforge:{MC}:jar"] if FORGE else [f":neoforge:{MC}:classes", f":neoforge:{MC}:gametestClasses", f":neoforge:{MC}:createServerLaunchScript"]
    with open("/tmp/vl-server-build.log", "w") as out:
        code = subprocess.run(["./gradlew", "--no-configuration-cache", "--offline", *tasks],
                              cwd=ROOT, env=env, stdout=out, stderr=subprocess.STDOUT).returncode
    if code == 0 and FORGE:
        forge_server()
    print("build", "ok" if code == 0 else "FAILED, see /tmp/vl-server-build.log")
    return code


def properties(n: int, seed: str | None = None) -> str:
    return "\n".join(([f"level-seed={seed}"] if seed is not None else []) + [
        "online-mode=false", "white-list=false", "allow-flight=true", "enable-rcon=true", f"rcon.port={25700 + n}", f"rcon.password={PASSWORD}",
        f"server-port={25600 + n}", "level-name=world", "spawn-protection=0", f"view-distance={os.environ.get('VL_VIEW_DISTANCE', '4')}",
        "simulation-distance=12", "difficulty=easy", "max-tick-time=-1", "sync-chunk-writes=false",
        "broadcast-rcon-to-ops=false", "motd=Villager Life run " + str(n), ""])


def snapshot(dir_: Path, command: list[str]) -> list[str]:
    """The mod's classes copied for this server alone (resources hard-linked): another session's gradle build rewrites
    build/classes while servers run, and a class loaded after that was missing (NoClassDefFoundError mid-run)."""
    build, snap = NODE / "build", dir_ / "build"
    if snap.exists():
        shutil.rmtree(snap)
    shutil.copytree(build / "classes", snap / "classes")
    shutil.copytree(build / "resources", snap / "resources", copy_function=os.link)

    def rebase(text: str) -> str:
        return text.replace(str(build / "classes"), str(snap / "classes")).replace(str(build / "resources"), str(snap / "resources"))

    out = []
    for arg in command:
        if arg.startswith("@") and Path(arg[1:]).is_file():
            copy = dir_ / Path(arg[1:]).name
            copy.write_text(rebase(Path(arg[1:]).read_text()))
            arg = "@" + str(copy)
        out.append(rebase(arg))
    return out


def start(n: int, args: list[str]) -> int:
    world, fresh, at, xmx, seed, bot = "New World", False, None, "4G", None, True
    while args:
        arg = args.pop(0)
        if arg == "--world":
            world = args.pop(0)
        elif arg == "--fresh":
            fresh = True
        elif arg == "--at":
            at = " ".join(args.pop(0) for _ in range(3))
        elif arg == "--xmx":
            xmx = args.pop(0)
        elif arg == "--seed":
            seed = args.pop(0)
        elif arg == "--no-bot":
            # real clients will join (tools/dev/restart.sh --server, client2.sh): no stand-in player named like them
            bot = False
    if alive(n):
        print(f"server {n} is already up")
        return 1
    if not FORGE and not LAUNCH.exists():
        print(f"no {LAUNCH}: run 'server.py build' first")
        return 1
    dir_ = home(n)
    source = Path(world) if "/" in world else RUN / "saves" / world
    if seed is not None:
        # a world of its own, generated from the seed: villages nobody has run before
        if (dir_ / "world").exists():
            shutil.rmtree(dir_ / "world")
    elif (fresh or not (dir_ / "world").exists()) and not source.is_dir():
        print(f"no world {source}")
        return 1
    if fresh and (dir_ / "world").exists():
        shutil.rmtree(dir_ / "world")
    if seed is None and not (dir_ / "world").exists():
        shutil.copytree(source, dir_ / "world", ignore=shutil.ignore_patterns("session.lock"))
    if (RUN / "config").is_dir():
        shutil.copytree(RUN / "config", dir_ / "config", dirs_exist_ok=True)
    # the dev client's other mods, those a server has too (Patchouli's channels are required both ways: a client
    # with it was turned away from a server without it, "Incompatible client!"); the MCP mod comes from below
    (dir_ / "mods").mkdir(parents=True, exist_ok=True)
    if FORGE:
        # the production server: the mod's built jar (English voice) and the MCP mod's Forge jar, nothing of the dev runs
        for old in (dir_ / "mods").glob("*.jar"):
            old.unlink()
        game = node_props()["minecraft_version"]
        shutil.copy2(next((NODE / "build/libs").glob("villager-life-forge-*-en.jar")), dir_ / "mods")
        shutil.copy2(sorted((Path.home() / ".cache/vl-minecraft/mcp").glob(f"minecraft_mcp-forge-*+{game}.jar"))[-1], dir_ / "mods")
    else:
        for jar in (RUN / "mods").glob("*.jar"):
            if not jar.name.startswith("minecraft_mcp"):
                shutil.copy2(jar, dir_ / "mods" / jar.name)
        # the MCP mod of the release the production clients use (prod_client.py), when there is one: its channels must match
        for old in (dir_ / "mods").glob("minecraft_mcp*.jar"):
            old.unlink()
        release = sorted((Path.home() / ".cache/vl-minecraft/mcp").glob(f"minecraft_mcp-neoforge-*+{MC}.jar"))
        for jar in (release[-1:] or [j for j in (RUN / "mods").glob("minecraft_mcp*.jar")]):
            shutil.copy2(jar, dir_ / "mods" / jar.name)
    (dir_ / "server.properties").write_text(properties(n, seed))
    if not bot:
        # the clients' players operators from the start, by their offline UUIDs: "op Dev2" asks Mojang for the name
        # and makes an operator of a real account of that name (it did: "deV2")
        import hashlib, json, uuid
        def offline(name: str) -> str:
            digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
            digest[6] = (digest[6] & 0x0F) | 0x30
            digest[8] = (digest[8] & 0x3F) | 0x80
            return str(uuid.UUID(bytes=bytes(digest)))
        (dir_ / "ops.json").write_text(json.dumps([{"uuid": offline(name), "name": name, "level": 4, "bypassesPlayerLimit": False}
                                                   for name in ("Dev", "Dev2")], indent=2))
    (dir_ / "eula.txt").write_text("eula=true\n")
    if FORGE:
        # Forge's own launch (run.sh's): its libraries linked into the instance, which has the world, mods and config
        installed = forge_server()
        p = node_props()
        # and the shim jar its arguments name (Forge 1.20.4 on): relative to the instance
        for part in [installed / "libraries", *installed.glob("*-shim.jar")]:
            if not (dir_ / part.name).exists():
                (dir_ / part.name).symlink_to(part)
        command = [JAVAS[p["java_version"]] + "/bin/java", f"-Xmx{xmx}", f"-Dvl.server={n}", f"-DminecraftMcp.endpoint.port={25800 + n}",
                   f"@libraries/net/minecraftforge/forge/{p['minecraft_version']}-{p['forge_version']}/unix_args.txt", "nogui"]
    else:
        # the command gradle wrote, run from this instance's folder; the marker lets pgrep find the process
        line = next(row for row in LAUNCH.read_text().splitlines() if "exec " in row)
        command = shlex.split(line.split("exec ", 1)[1].rstrip(")"))
        # the launch file keeps the java it was written with; Homebrew moves java on each update
        if not Path(command[0]).exists():
            command[0] = JAVA_HOME + "/bin/java"
        # the MCP mod listens on one fixed port per game: each server its own
        command[1:1] = [f"-Xmx{xmx}", f"-Dvl.server={n}", f"-DminecraftMcp.endpoint.port={25800 + n}"]
        command = snapshot(dir_, command)
    # one log for all the server's runs, each started with a line of its own: deaths and what led to them outlive a restart
    import time
    trim(log(n))
    out = open(log(n), "a")
    out.write(f"\n===== server {n} started {time.strftime('%Y-%m-%d %H:%M:%S')} =====\n")
    out.flush()
    start = log(n).stat().st_size
    process = subprocess.Popen(command, cwd=dir_, stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                               start_new_session=True)
    deadline = time.time() + 300
    # this run's lines only (the log keeps the earlier runs above them)
    while not (re.search(r"Done \(\d", log(n).read_bytes()[start:].decode(errors="replace")) and alive(n)):
        if process.poll() is not None or time.time() > deadline:
            print(f"server {n} did not start, see {log(n)}")
            return 1
        time.sleep(2)
    # from the console "~ ~ ~" is the world spawn
    if not bot:
        print(f"server {n} up (port {25600 + n}, rcon {25700 + n}, log {log(n)}), no bot; Dev and Dev2 are operators")
        return 0
    print(f"server {n} up (port {25600 + n}, rcon {25700 + n}, log {log(n)}):", rcon(n, f"vnap bot Dev {at or '~ ~ ~'}"))
    return 0


def stop(n: int) -> int:
    process = pid(n)
    if alive(n):
        print(rcon(n, "vnap save"))
        rcon(n, "stop")
    for _ in range(120):
        if process is None or pid(n) is None:
            print(f"server {n} stopped")
            return 0
        time.sleep(1)
    print(f"server {n} did not stop (pid {process})")
    return 1


def main(args: list[str]) -> int:
    if not args:
        print(__doc__)
        return 2
    what = args.pop(0)
    if what == "build":
        return build()
    if what == "status":
        for dir_ in sorted((RUN / "servers").glob("*")):
            n = int(dir_.name)
            print(f"{n}: {'up' if alive(n) else 'down'} pid={pid(n)} log={log(n)}")
        return 0
    n = int(args.pop(0))
    if what == "start":
        return start(n, args)
    if what == "stop":
        return stop(n)
    if what == "cmd":
        for command in args:
            print(command, "->", rcon(n, command) or "(no answer)")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

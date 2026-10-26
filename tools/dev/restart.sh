#!/bin/zsh
# Restarts the dev client with the freshly built mod and joins the world again.
#   tools/dev/restart.sh [--lang ru_ru|en_us] ["New World"]
#   tools/dev/restart.sh --stop   (save and close the client: before runData, build, GameTests)
#   tools/dev/restart.sh --server 127.0.0.1:25601 [--lite] [--lang ..]   joins a server (tools/dev/server.py) instead of
#                                 a world of its own: from the title screen (leaving a world for a server stops the MCP
#                                 mod's port with the integrated server)
#   tools/dev/restart.sh --lite [--lang ..] ["World"]   test mode: the client draws as little as it can (render distance
#                                 2, 10 fps, no vsync, clouds, shadows, mipmaps), so the server thread has the machine;
#                                 the simulation distance stays (what ticks round the player is what the game is). The
#                                 next start without --lite puts the player's video options back.
# Saves with /vnap save (flushes the region files) and waits for "Saved the game" before killing: killing the client while it still saves
# leaves empty region files behind (a whole village lost its villagers that way on 18.10.2026). mc.client.world.leave is
# not used: the MCP mod calls Minecraft.disconnect from a client task and the integrated server never stops.
cd "$(dirname "$0")/../.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
lang=""
lite=""
server=""
if [[ "$1" == "--stop" ]]; then stop=1; shift; fi
if [[ "$1" == "--server" ]]; then server="$2"; shift 2; fi
if [[ "$1" == "--lite" ]]; then lite=1; shift; fi
if [[ "$1" == "--lang" ]]; then lang="$2"; shift 2; fi
world="${1:-New World}"

title() { python3 tools/dev/mcp.py mc.client.state 2>/dev/null | grep -q TitleScreen; }
# this client's java, not tools/dev/client2.sh's (same command, with --username): it was killed along with it
main_client() { for p in $(pgrep -f '[c]lientRunProgramArgs'); do ps -o command= -p $p | grep -q -- '--username' || echo $p; done; }

# the client only: the dedicated servers of tools/dev/server.py are NeoForge processes too (serverRunProgramArgs)
if [[ -n "$(main_client)" ]]; then
  log=/tmp/vl-client.log
  # on a server the world is the server's (tools/dev/server.py saves it): nothing of the client's to save
  joined=$(grep -E "Connecting to|Starting integrated minecraft server" $log 2>/dev/null | tail -1)
  if [[ "$joined" != *"Connecting to"* ]] && python3 tools/dev/mcp.py mc.client.state 2>/dev/null | grep -q '"inWorld":true'; then
    before=$(grep -c "Saved the game" $log)
    python3 tools/dev/mcp.py mc.client.command.run '{"command":"vnap save"}' >/dev/null
    for i in {1..120}; do [[ $(grep -c "Saved the game" $log) -gt $before ]] && break; sleep 1; done
    [[ $(grep -c "Saved the game" $log) -gt $before ]] || { echo "the world did not report a save; not killing the client"; exit 1; }
  fi
  kill $(main_client)
  for i in {1..30}; do [[ -z "$(main_client)" ]] && break; sleep 1; done
fi
# the old gradle runClient still holds the log open and would write into the new one; "[r]unClient" so the pattern
# never matches a shell whose own command line names it (pkill -f once killed the agent's shell that way); only this
# repository's (its path in the command line): a bare pattern killed other projects' clients on the same machine
own_run_client() { for p in $(pgrep -f '[r]unClient'); do ps -o command= -p $p | grep -qF -- "$PWD" && echo $p; done; }
[[ -n "$(own_run_client)" ]] && kill $(own_run_client) 2>/dev/null
for i in {1..30}; do [[ -z "$(own_run_client)" ]] && break; sleep 1; done
[[ -n "$stop" ]] && exit 0
rm -f /tmp/vl-client.log
[[ -n "$lang" ]] && sed -i '' "s/^lang:.*/lang:$lang/" neoforge/versions/${VL_MC:-1.21.1}/run/options.txt
optfile=neoforge/versions/${VL_MC:-1.21.1}/run/options.txt
video="renderDistance maxFps enableVsync graphicsMode renderClouds particles entityShadows mipmapLevels ao biomeBlendRadius entityDistanceScaling"
if [[ -n "$lite" ]]; then
  # the player's own values, kept once, to be put back
  [[ -f $optfile.video ]] || for key in ${=video}; do grep "^$key:" $optfile; done > $optfile.video
  for pair in renderDistance:2 maxFps:10 enableVsync:false graphicsMode:0 'renderClouds:"false"' particles:2 entityShadows:false \
      mipmapLevels:0 ao:false biomeBlendRadius:0 entityDistanceScaling:0.5; do
    sed -i '' "s/^${pair%%:*}:.*/$pair/" $optfile
  done
elif [[ -f $optfile.video ]]; then
  while IFS= read -r pair; do sed -i '' "s/^${pair%%:*}:.*/$pair/" $optfile; done < $optfile.video
  rm -f $optfile.video
fi
./gradlew --no-configuration-cache --offline :neoforge:${VL_MC:-1.21.1}:runClient > /tmp/vl-client.log 2>&1 &!
# the Mac's screen asleep, the client fails at once ("Failed to locate a primary monitor", 20.10.2026: six minutes of
# waiting for nothing); kept awake while the client runs, and a failure is reported the moment it is in the log
client=$!
caffeinate -dimsu -w $client &!
# a compile error ("Foo.java:12: error:"), not any line with "error:" in it: the harmless "Suppressing GLFW icon
# error:" of macOS made the script give up on a client that was still starting, and it was left on the title screen
failed() { grep -m1 -E "Failed to locate a primary monitor|BUILD FAILED|\.java:[0-9]+: error:|Exception in thread \"main\"" /tmp/vl-client.log; }
# as long as the client is starting: under load (GameTest servers of another session) it took more than six minutes
for i in {1..400}; do
  title && break
  failed && { echo "the client did not start (above), see /tmp/vl-client.log"; exit 1; }
  kill -0 $client 2>/dev/null || { echo "the client exited, see /tmp/vl-client.log"; exit 1; }
  sleep 3
done
title || { echo "the client did not reach the title screen in 20 minutes, see /tmp/vl-client.log"; exit 1; }
# the join asked again until the world is open: one asked too early (the title screen still fading in) was lost
for attempt in {1..5}; do
  if [[ -n "$server" ]]; then
    python3 tools/dev/mcp.py mc.client.connection.connect "{\"address\":\"$server\"}" >/dev/null 2>&1
  else
    python3 tools/dev/mcp.py mc.client.world.join "{\"name\":\"$world\"}" >/dev/null 2>&1
  fi
  for i in {1..60}; do
    state=$(python3 tools/dev/mcp.py mc.client.state 2>/dev/null)
    grep -q '"inWorld":true' <<<"$state" && break 2
    # saved dead (a client closed on the death screen): the world is open, only the respawn waits — joining again
    # over an open world fails on its lock and leaves "Reading world data..." for good
    if grep -q 'DeathScreen' <<<"$state"; then
      python3 tools/dev/mcp.py mc.client.screen.widget.click '{"message":"Respawn"}' >/dev/null 2>&1
    fi
    sleep 2
  done
done
python3 tools/dev/mcp.py mc.client.state

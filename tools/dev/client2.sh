#!/bin/zsh
# A second dev client at once, for multiplayer checks: its own game directory (run-c2: the dev client's mods, config
# and options), its own name, its own MCP port; it joins a server (tools/dev/server.py) or a LAN game.
#   tools/dev/client2.sh 127.0.0.1:25601 [Name]     start it and join (Name: Dev2)
#   tools/dev/client2.sh --stop [Name]
# Drive it with VL_MCP=neoforge/versions/1.21.1/run-c2/mcp/server.json VL_CLIENT_LOG=/tmp/vl-client2.log
# python3 tools/dev/mcc.py ... (mcp.py the same). Offline-mode servers only: no Microsoft account is asked for.
cd "$(dirname "$0")/../.."
node=neoforge/versions/${VL_MC:-1.21.1}
dir=$node/run-c2
export VL_MCP=$dir/mcp/server.json
if [[ "$1" == "--stop" ]]; then pkill -f "[-]-username ${2:-Dev2}"; exit 0; fi
address="$1"; name="${2:-Dev2}"
[[ -f $node/build/moddev/runClient.sh ]] || { echo "no launch script: ./gradlew :neoforge:${VL_MC:-1.21.1}:createClientLaunchScript"; exit 1; }
mkdir -p $dir
rsync -a --delete $node/run/mods/ $dir/mods/
rsync -a $node/run/config/ $dir/config/
cp $node/run/options.txt $dir/options.txt
rm -f $dir/mcp/server.json
line=$(grep "exec " $node/build/moddev/runClient.sh)
cmd=${line#*exec }
cmd=${cmd%)}
# the launch script keeps the java it was made with; Homebrew moves java on each update: the current one then
java_bin=${cmd%% *}
[[ -x $java_bin ]] || cmd="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}/bin/java ${cmd#* }"
# its name on the command line lets --stop find this client and not the other one
# the MCP mod listens on a fixed port: the dev client has 25580
(cd $dir && export MINECRAFT_MCP_ENDPOINT_PORT=25582 && eval "exec $cmd --username $name" > /tmp/vl-client2.log 2>&1 &!)
for i in {1..300}; do
  [[ -f $VL_MCP ]] && python3 tools/dev/mcp.py mc.client.state 2>/dev/null | grep -q TitleScreen && break
  sleep 2
done
python3 tools/dev/mcp.py mc.client.state 2>/dev/null | grep -q TitleScreen || { echo "client 2 did not reach the title screen, see /tmp/vl-client2.log"; exit 1; }
for attempt in {1..5}; do
  python3 tools/dev/mcp.py mc.client.connection.connect "{\"address\":\"$address\"}" >/dev/null 2>&1
  for i in {1..60}; do python3 tools/dev/mcp.py mc.client.state 2>/dev/null | grep -q '"inWorld":true' && break 2; sleep 2; done
done
python3 tools/dev/mcp.py mc.client.state

#!/bin/zsh
# dev client for filming: game sound to WAV (OpenAL wave driver), args go to restart.sh
cd ~/Downloads/FireShot/villager-news-ru-port
export ALSOFT_CONF=$HOME/Downloads/FireShot/villager-news-ru-port/tools/trailer/alsoft.conf VL_WAV=$HOME/Movies/VillagerLife/film1/raw/game.wav
tools/dev/restart.sh "$@" > /tmp/vl-restart.log 2>&1 || { tail -3 /tmp/vl-restart.log; exit 1; }
tail -1 /tmp/vl-restart.log
python3 tools/dev/mcp.py mc.client.window '{"visible":false}'
python3 tools/dev/mcp.py mc.client.command.run '{"command":"vnaprec size 1920 1080"}'
python3 tools/dev/mcp.py mc.client.command.run '{"command":"vnaprec chat false"}'
python3 tools/dev/mcp.py mc.client.command.run '{"command":"vnapcam fov 60"}'
python3 tools/dev/mcc.py '/gamemode creative' > /dev/null
python3 tools/dev/mcc.py '/vnap stage true' > /dev/null

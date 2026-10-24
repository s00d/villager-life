#!/bin/zsh
# grow.sh DAYS NAME : wide timelapse while sprinting DAYS days, town state logged per day
cd ~/Downloads/FireShot/villager-news-ru-port
m(){ python3 tools/dev/mcp.py "$@"; }
python3 tools/dev/mcc.py "/tp @s ${CAM:--600 112 462 -30 32}" > /dev/null
sleep 3
m mc.client.command.run '{"command":"vnaprec hud false"}' > /dev/null
[[ -n "$2" ]] && m mc.client.command.run "{\"command\":\"vnaprec lapse $2 ${EVERY:-100} ${FROM:-5000} ${TO:-7000}\"}" > /dev/null
for d in $(seq 1 $1); do
  m mc.server.tick '{"sprint":12000}' > /dev/null
  m mc.server.tick '{"sprint":12000}' > /dev/null
  echo "== day $d $(date +%T)"
  python3 tools/dev/mcc.py '/vnap town' '/vnap housing' | cut -c1-260
  python3 tools/dev/mcc.py '/vnap roads' | cut -c1-200
  rg -c "vnap death" /tmp/vl-client.log
done
[[ -n "$2" ]] && m mc.client.command.run '{"command":"vnaprec stop"}' > /dev/null
echo done

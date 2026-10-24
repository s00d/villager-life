#!/bin/bash
# Runs one gametest in the running dev client and prints its chat result.
# usage: tools/dev/runtest.sh <client-log> '/test run <methodnamelowercase>' [max-wait-seconds]
# The test name is the method name in lower case, without the class: /test run machinemenuskeeptheiritems
DEV="$(cd "$(dirname "$0")" && pwd)"
LOG="$1"; TEST="$2"; MAX="${3:-120}"
state() { python3 "$DEV/mcp.py" mc.client.state 2>/dev/null; }
for _ in $(seq 1 60); do
  if state | rg -q 'TitleScreen|inWorld":true'; then break; fi
  sleep 3
done
if ! state | rg -q 'inWorld":true'; then
  python3 "$DEV/mcp.py" mc.client.world.join '{"name":"New World"}' > /dev/null
  for _ in $(seq 1 60); do
    if state | rg -q 'inWorld":true'; then break; fi
    sleep 2
  done
  sleep 5
fi
START=$(wc -l < "$LOG" | tr -d " ")
python3 "$DEV/mcc.py" '/gamerule doDaylightCycle false' '/tp Dev 3000 160 3000' 'sleep 2' "$TEST" > /dev/null
for _ in $(seq 1 "$MAX"); do
  if tail -n +"$START" "$LOG" | rg -q 'GameTest done|No such test|Unknown or incomplete'; then break; fi
  sleep 1
done
tail -n +"$START" "$LOG" | rg '\[CHAT\]|LogTestReporter'

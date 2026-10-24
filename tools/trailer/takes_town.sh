#!/bin/zsh
# film 2, the scenes in New World besides the fights: board, anvil, heroes (language of work/.lang)
cd ~/Downloads/FireShot/villager-news-ru-port
S=tools/trailer/shoot.py
python3 tools/dev/mcc.py '/vnap spire leave' 'sleep 3' '/execute in minecraft:overworld run tp @s -549 64 540' '/difficulty peaceful' 'sleep 2' >/dev/null
python3 $S take p1_s12_board 12 "still -549 64 540.5 0 15" --hud --pre "/gamemode adventure" --pre "/clear @s" --pre "/item replace entity @s hotbar.1 with bread 2" --pre "/time set 4000" --at 0.6 "i:-549 63 543 north" --at 1.0 "c:vnapbot mouse 152 93" --at 2.4 "c:vnapbot mouse 267 95" --at 2.8 "c:vnapbot click" --at 4.6 "c:vnapbot mouse 320 95" --at 5.0 "c:vnapbot click" --at 6.8 "c:vnapbot mouse 373 95" --at 7.2 "c:vnapbot click" --at 8.4 "c:vnapbot mouse 300 200" --at 9.8 "c:vnapbot mouse 408 95" --at 10.2 "c:vnapbot click"
python3 tools/dev/mcp.py mc.client.keyboard.press '{"key":"escape"}' >/dev/null
T=(root archive clockwork garden deep)
python3 tools/dev/mcc.py '/gamemode survival' '/setblock -553 64 547 villager_life:core_anvil' '/kill @e[type=villager,x=-553,y=64,z=546,distance=..3]' >/dev/null
for i in 0 1 2 3; do
  cur=${T[$((i+2))]}; nxt=${T[$((i+3))]}
  python3 tools/dev/mcc.py '/clear @s' >/dev/null
  python3 $S take p2_anvil_$i 4.2 "still -552.5 64 544.5 0 30" --hud --lead 1 --pre "/time set 5000" --pre "/item replace entity @s inventory.9 with villager_life:silver_coin 60" --pre "/item replace entity @s hotbar.0 with villager_life:spire_${cur}_sword[custom_data={vnap_hone:3}]" --pre "/item replace entity @s hotbar.1 with villager_life:core_shard 64" --pre "/item replace entity @s hotbar.2 with villager_life:spire_seal_${nxt}" --at 0.2 "i:-553 64 547 north" --at 0.7 "q:30" --at 1.0 "q:31" --at 1.3 "q:32" --at 1.6 "c:vnapbot mouse 368 179" --at 2.3 "c:vnapbot click 0"
  python3 tools/dev/mcp.py mc.client.keyboard.press '{"key":"escape"}' >/dev/null
done
python3 tools/dev/mcc.py '/clear @s' '/execute as @e[type=villager,name=Viggo,limit=1] run tp @s -551 64 552' '/execute as @e[type=villager,name=Igor,limit=1] run tp @s -548 64 553' 'sleep 1' '/execute positioned -550 64 552 run vnap spire hero 40 8' >/dev/null
python3 $S take p2_heroes 12 "still -551.5 64.4 560.5 180 8" --hud --lead 1 --pre "/difficulty normal" --pre "/gamemode adventure" --pre "/item replace entity @s hotbar.0 with villager_life:spire_garden_sword[custom_data={vnap_hone:3}]" --pre "/time set 13800" --pre "/kill @e[type=villager,x=-551,y=64,z=560,distance=..3]" --at 0.3 "/summon zombie -549 64 547" --at 0.3 "/summon zombie -553 64 546" --at 1.0 "/summon skeleton -551 64 544" --at 4.0 "/summon zombie -547 64 549"
python3 tools/dev/mcc.py '/difficulty peaceful' '/clear @s' >/dev/null

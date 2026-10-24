#!/bin/zsh
# film 1, the scenes in New World (town, spire), in the language of its work/.lang
cd ~/Downloads/FireShot/villager-news-ru-port
export VL_FILM=~/Movies/VillagerLife/film1
S=tools/trailer/shoot.py
python3 tools/dev/mcc.py '/vnap spire leave' 'sleep 3' '/execute in minecraft:overworld run tp @s -546 64 558' '/execute as @e[type=villager] run data merge entity @s {NoAI:0b}' '/vnap stage true' '/team join cast @e[type=villager]' '/gamemode spectator' >/dev/null
python3 $S take d01_town_orbit 14 "orbit -545 64 540 85 48 120 190" --pre "/time set 4500"
python3 $S take d02_chapel 10 "orbit -522 70 596 24 9 10 70"
python3 $S take h01_statue 9 "orbit -555.5 65.5 554.5 5 1.2 140 220"
python3 tools/dev/mcc.py '/setblock -553 64 547 air' '/clear @s' >/dev/null
python3 $S take c02_board 12 "still -549 64 540.5 0 15" --hud --pre "/gamemode adventure" --at 0.6 "i:-549 63 543 north" --at 1.0 "c:vnapbot mouse 152 93" --at 2.4 "c:vnapbot mouse 267 95" --at 2.8 "c:vnapbot click" --at 4.6 "c:vnapbot mouse 320 95" --at 5.0 "c:vnapbot click" --at 6.8 "c:vnapbot mouse 373 95" --at 7.2 "c:vnapbot click" --at 8.4 "c:vnapbot mouse 300 200" --at 9.8 "c:vnapbot mouse 408 95" --at 10.2 "c:vnapbot click"
python3 tools/dev/mcp.py mc.client.keyboard.press '{"key":"escape"}' >/dev/null
python3 tools/dev/mcc.py '/gamemode survival' '/time set 5000' '/setblock -553 64 547 villager_life:core_anvil' '/clear @s' '/item replace entity @s inventory.9 with villager_life:silver_coin 60' '/give @s villager_life:spire_root_sword[custom_data={vnap_hone:2}]' '/give @s villager_life:core_shard 48' '/give @s villager_life:spire_seal_archive 1' 'sleep 2' >/dev/null
python3 $S take g01_anvil 11 "still -552.5 64 544.5 0 30" --hud --at 0.6 "i:-553 64 547 north" --at 2.0 "q:30" --at 3.0 "q:31" --at 4.0 "q:32" --at 5.0 "c:vnapbot mouse 390 118" --at 7.0 "c:vnapbot mouse 368 179" --at 7.8 "c:vnapbot click 0" --at 8.6 "c:vnapbot mouse 390 118"
python3 tools/dev/mcp.py mc.client.keyboard.press '{"key":"escape"}' >/dev/null
python3 tools/dev/mcc.py '/clear @s' '/gamemode spectator' >/dev/null
python3 tools/dev/mcc.py '/execute as @e[type=villager,name=Igor,limit=1] run tp @s -541 64 558' 'sleep 1' '/execute positioned -541 64 558 run vnap spire hero 40 8' >/dev/null
python3 $S take s16_hero 12 "path -546.0 64.0 558.5 90 3; -546.6 64.0 558.5 90 4" --pre "/time set 5000" --pre "a:Igor 0 4.6 @cam" --pre "a:Freya -1.4 3.9 @Igor!" --pre "a:Maxim 1.2 3.4 @Igor!" --at 0.6 "say:Maxim Talks With the Hero" --at 4.4 "say:Freya Talks With the Hero" --at 8.2 "say:Igor Hero Answers"
python3 tools/dev/mcc.py '/execute as @e[type=villager] run data merge entity @s {NoAI:0b}' '/execute in minecraft:overworld run tp @s -551 90 540' '/time set 11900' 'sleep 3' >/dev/null
python3 $S take s17_finale 14 "orbit -545 66 520 90 42 200 260"

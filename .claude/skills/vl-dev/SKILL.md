---
name: vl-dev
description: Villager Life dev tools — use before any live check in the dev client or on dedicated test servers (restart, commands, scenarios, tick sprints), when looking at a place or a villager without screenshots, when villagers die or a village misbehaves, when finding fresh villages to test on, and before running GameTests or the build.
---

# Villager Life: инструменты агента

Полный справочник — `tools/dev/README.md` (прочитать целиком перед живой проверкой). Главное:

- Клиент: `tools/dev/restart.sh [--lite] [--lang ru_ru] ["Мир"]` (`--lite` — для долгих прогонов: клиент почти не рисует) (проверить `"inWorld":true` в выводе), закрыть — `--stop`.
  Не запускать, если клиентом уже пользуется другая сессия: `pgrep -fl "scenario.py|clientRunProgramArgs"`.
- Долгие прогоны без окна, несколько деревень сразу: `python3 tools/dev/server.py build`, `server.py start N --fresh [--world ..]`
  (бот `Dev`, `/vnap bot`), `python3 tools/dev/scenario.py --server N <сценарий>` (по серверу на деревню, в фоне),
  `deaths.py /tmp/vl-server-N.log`, `server.py stop N`. Такт тот же, что в клиенте; выигрыш — параллельность.
- Полная проверка в мире: `python3 tools/dev/sweep.py --seeds 1111,2222,3333 --per-seed 2 --days 3 --parallel 8 --base 11` — сам ищет деревни на сидах и гоняет по серверу на деревню параллельно, сводка в `/tmp/vl-sweep.md` (`--sleep` — с пропуском ночей). Номера серверов — свободные (`pgrep -fl vl.server=`).
- Мультиплеер: сервер `server.py start N`, клиент `restart.sh --server 127.0.0.1:2560N`, второй клиент `tools/dev/client2.sh ADDR [Dev2]`
  (`VL_MCP=neoforge/versions/1.21.1/run-c2/mcp/server.json` для mcp.py/mcc.py); LAN — `/vnap offline` + `/publish`.
- Команды: `python3 tools/dev/mcc.py '/cmd' 'sleep 2' '/cmd2'` (ответ — из `mc.server.command.run`); MCP-мод 0.6.0: `python3 tools/dev/mcp.py --list`; порт у каждой игры свой (dev-клиент 25580, второй 25582, production 25583, сервер N 25800+N), токена нет, `hand` — `main`/`offhand`.
- Все проверки — только на 1.21.1; другие версии — только по прямой просьбе пользователя.
- Стройка домов: `/vnap site` (ближайшая стройка и почему стоит), `/vnap worldhouses [r]` (дома мира вокруг, кто живёт), `/vnap family room hand` (уровень руками), `/vnap worldhouse <шаблон|all> [l1|l2|l3] [cut]` (ванильный дом и уровень рядом, разрез).
- Смотреть без скриншотов: `/vnap spot`, `/vnap layer [dy] [r]`, `/vnap mobs [r]`, `/vnap who [r]`, `/vnap home [имя]`,
  `/vnap check [r]`; все ветки — `/help vnap`.
- Сценарии: `python3 tools/dev/scenario.py tools/dev/scenarios/<имя>.txt` (в фоне, один за раз), шаг `deaths`;
  свежие деревни — `python3 tools/dev/fresh.py X Z [виды]`; прокрутка — творческим игроком над колоколом.
- Потери: `python3 tools/dev/deaths.py [--since ЧЧ:ММ:СС]` по строкам `vnap death|gone|shelter` в `/tmp/vl-client.log`.
- Тесты: `tools/dev/gts.sh [фильтр]`, сборка `./gradlew --no-configuration-cache --offline build`; gradle и клиент не вместе.
- Лаги: `/neoforge track start entity 10` → `/neoforge track entity`, `/neoforge tps`, `/perf start|stop`, `jstack <pid>`.
- Исходники игры: `tools/dev/mcsrc.sh <Класс> | -l <имя> | -g '<regex>'`; миксины — `JAVA_TOOL_OPTIONS='-Dmixin.debug.export=true'`.
- Логи не обрезать — `rg` по полному файлу.
- Другие версии Minecraft (1.21.4 … 26.3), только по просьбе: `VL_MC=<v>` у всех инструментов; клиент версии — `python3 tools/dev/client_check.py <v>` (production-клиент через portablemc + MCP-мод из релиза `s00d/minecraft-mcp`, снимки сцен) или `prod_client.py start|stop|mcp <v>`; инструменты MCP — `mc.client.*`/`mc.server.*`.

Новый инструмент — записать в `tools/dev/README.md`, в этот навык и в раздел «Инструменты агента» `AGENTS.md`.

- Бот-игрок в моде (`/vnapbot climb <с> <по> [заточка] [секунд]`, `/vnapbot tour` — обход города, `/vnapbot stop`): проходит Шпиль за игрока настоящими клавишами, лог `run/devbot.log`; см. `tools/dev/README.md` «Бот-игрок внутри мода».

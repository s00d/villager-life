# HANDOFF — бриф для работников universal-modder (Villager Life)

Для `mod-orchestrator`, `mod-game-worker`, `mod-offline-worker` и любого агента, который начинает здесь с нуля.
Устройство мода, сборка, тесты и правила — в `AGENTS.md`; этот файл говорит, как к ним подключиться и чего не
делать. Состояние — `STATUS.md`, журнал — `MODLOG.md`.

## Игра и маршрут
- Minecraft Java 1.21.1, NeoForge 21.1.252, Java 21, Stonecutter 0.9.8, macOS (Apple Silicon M1 Max).
- Маршрут: **loader API** — свой мод с исходниками (`neoforge/src/main/java/com/vnap/`), миксины только там, где
  NeoForge не даёт событий (`mixin/`). Декомпиляция, чужие jar и игровые файлы не нужны: всё читается из
  исходников мода и из `~/.gradle` (NeoForm/маппинги Mojang).
- `um scan` для этого проекта бесполезен (он ищет установленные игры Windows); плейбук universal-modder —
  `skills/mod-any-game/references/engines/minecraft.md`.

## Оракулы (как узнать, что работает)
| Что | Команда | Где смотреть |
|---|---|---|
| компиляция | `./gradlew --no-configuration-cache --offline :neoforge:1.21.1:compileJava :neoforge:1.21.1:compileGametestJava > /tmp/vl-compile.log 2>&1` | `rg -n "error:\|warning:\|BUILD" /tmp/vl-compile.log` |
| GameTests (headless, ~60–90 с) | `tools/dev/gts.sh [классы,батчи,имена]` | `/tmp/vl-gts.log`: `All N required tests passed` |
| полная сборка + SpotBugs | `./gradlew --no-configuration-cache --offline build > /tmp/vl-build.log 2>&1` | `/tmp/vl-build.log` |
| реплики/озвучка (без TTS) | `python3 tools/expand/scripts/check_lines.py` | `check_lines: 0 problems` |
| книга | `python3 tools/guide/check_guide.py` | `check_guide: 0 problems` |
| живой клиент | `tools/dev/restart.sh [--lang ru_ru]` (сборка, безопасный перезапуск, вход в «New World») | `/tmp/vl-client.log` |
| команды в клиенте | `python3 tools/dev/mcc.py '/vnap who 48' '/vnap check 48'` | ответ чата печатается |
| записанные проверки | `python3 tools/dev/scenario.py tools/dev/scenarios/<файл>.txt` | код выхода 1 при провале |
| скриншот | `python3 tools/dev/mcp.py mc.screenshot.take` | `neoforge/versions/1.21.1/run/screenshots/` |

- Флаги `--no-configuration-cache --offline` обязательны. Логи не обрезать (`| tail`/`| head` нельзя): полный вывод
  в `/tmp/vl-*.log`, искать `rg`.
- **`um win` здесь не работает** (только Windows/WSL). Живой клиент ведётся через MCP-мод `minecraft_mcp`
  (`tools/dev/mcp.py`, порт/токен в `run/mcp/server.json`) и отладочные `/vnap …` (список — `AGENTS.md`, «Команды»).
- Клиент закрывать только через `restart.sh` (он делает `/vnap save` и ждёт «Saved the game»): `pkill` во время
  сохранения уже стёр однажды жителей мира. `mc.world.leave` вешает клиент.
- Только один процесс водит клиент. Перед `gts.sh`/`build` клиент лучше закрыть.

## Твёрдые правила (из `AGENTS.md` и от пользователя)
- Отвечать пользователю по-русски.
- **Озвучку не генерировать**: ни `tools/voice/dub.py`, ни TTS, ни Whisper — даже «пробу на пару клипов».
  Новые реплики — текстом в спеках (`tools/expand/spec/chunks/*.yaml`, EN+RU, теги эмоций S2 прямо в тексте) с
  `PENDING_TTS`; команду озвучки пишешь пользователю, запускает он.
- Не менять поведение модели TTS (точность, семплирование, кэш) — пользователь доверяет только исходной.
- Коммит и пуш — только по явной просьбе; никогда `git commit --amend`. В рабочей копии постоянно лежат ogg и
  `PENDING_TTS` из EN-прогона пользователя — их не коммитить без просьбы.
- Не публиковать (Modrinth, `um kb pr --yes`, релизы) без явного «да».
- Не трогать `-Werror`, ErrorProne, NullAway, SpotBugs; не переименовывать namespace `villager_life`,
  NBT-ключи `VillagerLife*`, конфиги `villager-life-*.json`.
- Не редактировать файлы планов (`docs/plans/*`).
- Настроение деревни меняют только действия игрока.
- Сгенерированное скриптами (`tools/art/*`, `tools/build/*`, `gen_from_spec.py`) руками не править — править
  генератор и перезапускать (таблица в `AGENTS.md`, «Генераторы»).
- Сделанное проверять связками и крайними случаями (чанки не загружены, нет кандидата, два колокола рядом,
  выгрузка/загрузка), а не только счастливый путь; «DONE» — только с тем, что измерено.

## Ассеты (fal / ComfyUI)
- Текстуры мода рисуются кодом (`tools/art/paint_*.py`, `gen_*.py`) в ванильном стиле 16×16 — сначала расширять
  генератор. Нейросеть — для черновиков, референсов, картинок книги и звуков.
- fal: ключ у пользователя в `~/.secrets/fal-key` (`um fal key status`); кредиты платные — перед пачкой больше
  ~20 генераций или чем-то дорогим (3D, видео, музыка) назвать цену (`um fal price <endpoint>`) и спросить.
  MCP-сервер fal не подключён (ему нужен `FAL_KEY` в окружении приложения) — работать через `um fal`.
- Куда класть: черновики — `tools/art/fal/` (в `.gitignore`, вместе с `fal_manifest.jsonl`); принятый ассет
  переносится в `neoforge/src/main/resources/assets/villager_life/…`, а его строка манифеста —
  в `tools/art/fal_manifest.jsonl` (коммитится: README мода обязан назвать модели fal).
- Стиль для промптов текстур: *"16x16 Minecraft Java vanilla-style pixel art texture, flat lighting from the top
  left, limited palette, crisp pixels, no text, no border"*; потом `um fal pixelate --colors 16` или
  `um sprite`, ближайший сосед до 16×16, сверка с соседними ванильными блоками.
- Звуки: `um fal sfx … --seconds 0.3–2`, затем **моно** ogg:
  `ffmpeg -i x.mp3 -ac 1 -af "silenceremove=start_periods=1:start_threshold=-50dB,loudnorm=I=-16" x.wav`, затем
  моно-ogg через `soundfile` (в ffmpeg здесь нет libvorbis, а встроенный `vorbis` пишет только стерео):
  `.venv/bin/python -c "import soundfile as sf; d,r=sf.read('x.wav'); sf.write('x.ogg', d, r, format='OGG', subtype='VORBIS', compression_level=0.3)"`.
  Minecraft позиционирует и глушит с расстоянием **только моно**-звуки.

## Находки из плейбука universal-modder, ждут решения (не исправлено)
1. ~~Голосовые клипы в стерео~~ — исправлено 19.10.2026: моно через libsndfile (`dub.py: to_ogg`), готовые
   перекодированы. ffmpeg-кодер `vorbis` моно не пишет — для звуков мода использовать `soundfile` или libvorbis.
2. `VillageScriptDirector.java:858` берёт `level.getHeight(...)` без проверки загрузки чанка: в незагруженном
   чанке это дно мира. Точка в 7 блоках от ядра алтаря (обычно загружена) — проверить, не сажает ли она флаг/мебель
   обряда под землю у края загрузки.
3. NeoForge GameTests: `helper.makeMockPlayer(...)` не добавлен в мир (сканы сущностей его не видят), а
   `makeMockServerPlayerInLevel()` всегда «creative» (мобы его не целят) — для тестов ИИ против игрока нужен
   настоящий `ServerPlayer` (рецепт — заметка kb `techniques/neoforge-1-21-1-gametests-for-mob-ai-real-survival-players-s.md`).

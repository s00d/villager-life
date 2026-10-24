# Публикация на Modrinth и CurseForge — чек-лист (21.10.2026)

> 24.10.2026: точные поля Modrinth, описание, галерея с подписями и план версий — в [`modrinth/`](modrinth/README.md); там же — что устарело ниже (jar на язык озвучки, Environment `client_and_server`, своя лицензия).

Публикует только пользователь. Источники: [Modrinth Content Rules](https://modrinth.com/legal/rules),
[CurseForge Moderation Policies](https://support.curseforge.com/en/support/solutions/articles/9000197279).

## Блокеры

- [x] **Чистая комната** (`AUDIT.md`, п. 6): ассетов, реплик, персонажей Villager News и кода marcy без лицензии в моде нет.
- [ ] Озвучка переписанных строк (1744+, `*.PENDING_TTS`) — запускает пользователь.
- [x] `LICENSE` — своя «Villager Life License» (24.10.2026, вместо MIT): читать и изучать, играть, сборки со ссылкой на официальный файл, видео; копировать, менять, перезаливать — нет.
- [ ] Новая история и репозиторий `s00d/villager-life` (ссылки в `neoforge.mods.toml` и README уже на него).

## Modrinth

| Поле | Значение / что сделать | Готово |
|---|---|---|
| Название | Villager Life | ✓ |
| Summary (коротко, без названия, без форматирования) | «Villagers live whole lives: they build their own town by hand, raise families, trade with coins and talk in English or Russian voices.» | ✓ |
| Описание | из README: что делает мод, зачем ставить, что нужно знать; без заголовков для основного текста — абзацы; английский | черновик в README |
| Иконка | `docs/media/icon-400.png` / `icon.png` — нарисована скриптом `tools/art/paint_icon.py`, **не ИИ** | ✓ |
| Галерея | скриншоты из игры `docs/media/{village,villagers,golem,guide}.jpg` (**ИИ-картинки запрещены**); баннер — коллаж скриптом | ✓ |
| Лицензия | Custom: `LicenseRef-Villager-Life-License`, ссылка на `LICENSE` в репозитории | ✓ |
| Окружение | client + server, required на обоих | ✓ (`neoforge.mods.toml`) |
| Зависимости в каждой версии | NeoForge; JEI/EMI — optional (EMF/ETF/ESF больше не нужны) | ✓ |
| Категории/теги | `game-mechanics`, `mobs`, `economy` (до трёх основных) | ☐ |
| **AI disclosure** | обязательно «Contains AI-generated content»: код с ИИ-ассистентом, голоса TTS, звуки/музыка fal, текстуры доски fal | ☐ |
| Риск «primarily AI» | правило: проект не может быть «entirely or primarily» ИИ. Голоса, SFX, музыка — ИИ; код — с ассистентом. Решение пользователя; в описании честно расписать, что сделано руками (схематики, дизайн механик, тесты, правки) | ☐ |
| Ссылки | Source, Issues — GitHub | ✓ после переезда |
| Версии | по jar на версию MC, changelog из `CHANGELOG.md` | CI готов |

## CurseForge

| Требование | Что | Готово |
|---|---|---|
| Аватар 400×400, не однотонный, без чужого арта, не WebP | `docs/media/icon-400.png` | ✓ |
| Описание с подробностями, английский первым; форк — что изменено, не копия описания оригинала | README | ☐ |
| Summary одним предложением, не копия описания | как на Modrinth | ✓ |
| Название без версии и «Minecraft» | Villager Life | ✓ |
| Чужой контент — по лицензии или разрешению, с указанием авторов | чужого нет (чистая комната) | ✓ |
| Музыка — своя или с правами, указать при подаче | 4 трека + ярмарка: fal Lyria / ElevenLabs Music — их условия дают права на коммерческое использование; указать в описании | ☐ |
| ИИ-изменённые скриншоты — пометка | скриншоты без обработки | ✓ |
| Changelog на каждый файл | `CHANGELOG.md` | ✓ |
| Никаких внешних ссылок на скачивание, донатов | README для CF без них | ☐ |
| Теги загрузчика и версий | NeoForge; 1.21.1 … 26.3 | ☐ |

## В моде

- [x] Версия 1.0.0 (`gradle.properties`), jar `villager-life-1.0.0+<mc>.jar`
- [x] `neoforge.mods.toml`: authors, credits, description, displayURL, issueTrackerURL, logoFile, license
- [x] `CHANGELOG.md`, `CONTRIBUTING.md`, шаблоны issue/PR
- [x] CI: сборка на push/PR, релиз GitHub по тегу (тег = `mod_version`, тесты всех версий)
- [x] Скриншоты для README/галереи: деревня, жители, голем, книга (лавка, доска, часовня — снять в развитом городе)
- [x] Баннер — коллаж скриншота скриптом (`tools/art/paint_banner.py`, не ИИ)
- [x] `/vnap` выключены в production (`debugCommands` = `!production`); отладочные строки лога (`vnap monster/slow/shelter/siege/hollows/mint`) — только при `debugCommands` (`VillagerLife.trace`); смерти и уходы жителей пишутся всегда

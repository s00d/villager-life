# Villager Life 1.0.0 — аудит перед первым релизом (21.10.2026)

## 0. Блокер: чужие права (решает пользователь)

**Oreville Studios подали DMCA 02.10.2026** — GitHub заблокировал исходный Java-порт
`MarcYohannTheScripter/villager-news-bedrock-addon-java-port` (HTTP 451, notice
`github/dmca/2026/10/2026-10-02-oreville-studios.md`: «The entire repository is infringing»).
Наш репозиторий — форк в той же сети (`isFork: true`, parent `AlexandderAR/villager-news-bedrock-addon-java-port`),
сейчас приватный. Правообладатель активно защищает права: публикация с их ассетами почти наверняка
кончится DMCA на Modrinth/CurseForge и баном аккаунта.

Правила площадок:
- Modrinth: «must own, or hold the necessary licenses, rights … for any content you upload»; reupload без
  разрешения запрещён (исключение — fork, который *по лицензии* можно форкать).
- CurseForge: чужой контент — только по лицензии или явному разрешению; форк — только если лицензия разрешает.

Что в моде сейчас от Villager News (Oreville Studios / Element Animation) и порта marcy:

| Что | Где | Файлов | Статус |
|---|---|---|---|
| Модели жителей, малыша, торговца, Wooly (CEM/EMF) | `assets/minecraft/optifine/cem/*.jem`, `*.properties` | 17 | изменены, но производные |
| Анимации лица/жестов `animation.oreville_vn.*` | `assets/villager_life/dialogue_animations.json` (побайтно), ссылки в `dialogues.json` (5504) | 1 | оригинал |
| Текстуры жителей (биомы, профессии, уровни, малыш) | `assets/minecraft/textures/entity/villager/**` | 29 | побайтно |
| Персонажи (Mayor, Testificate Man, №5, №9, Unreachable, нос, табличка) | `textures/entity/{dil..dtd,mayor,number_*,testificate_man,rainbow_nose,sign_text}.png` | 19 | побайтно |
| Косметика и яйца (шляпа мэра, усы, микрофон, шлем, нос, 5 яиц) | `textures/item/**`, `items/*.json`, `models/item/*.json` | ~45 | побайтно / производные |
| Звуки боли жителя `effect/a..v.ogg` | `sounds/effect/`, `sound/SupplementalSoundCatalog` | 22 | **оригинальные ogg** |
| Голоса | `sounds/voice/ru/blp.ogg`, `voice/ru/cnf.ogg` | 2 | **побайтно оригинальные** клипы Element (нарушение правила AGENTS.md) |
| Тексты реплик | `lang/en_us.json` (`subtitles…dialogue.*`) | 3120 из 7075 | дословно из Villager News |
| Имена персонажей и «Villager News» в репликах | lang, `villager.properties`, ESF `*.properties` | — | товарные знаки/персонажи Element |
| ESF-замены звуков жителя/торговца/овцы | `assets/minecraft/esf/entity/**` | 27 | производные |
| Код marcy (без лицензии, кроме CC0 шаблона Fabric) | `ContextualDialogueController` (было 2786 строк), `DialogueAnimationState` (673, почти без правок), `HandbookScreen`, `DialogueTestCommand`, `DialogueSoundState/SubtitleState`, `DialogueCatalog`, миксины звуков, `EMFModelPartMixin`, `CrossedArmsItemLayerMixin` и др. — 32 файла | 32 | **лицензии нет** (`LICENSE` сам это говорит) |

Работы Akira-Media в моде не осталось (решение пользователя 21.10): `LICENSE-PORT` и упоминания Akira-Media в
`LICENSE`, `AGENTS.md`, `MODLOG.md` убираются вместе с чистой комнатой; README и `neoforge.mods.toml` уже без них.

Варианты:
1. **Чистая комната (рекомендую)**: убрать всё из таблицы; жители — ванильная модель + наши слои
   (одежда, причёски, пол, седина уже свои — `VillagerOutfitLayer`); лицо/липсинк — своя простая анимация
   (или без неё); 3120 реплик Villager News и персонажей — удалить или переписать своими словами
   (новые id → TTS запускает пользователь); звуки боли — 22 SFX через fal (~0,05 $); код marcy — переписать
   (`ContextualDialogueController` — ядро, крупная работа). EMF/ETF/ESF тогда не нужны.
2. **Разрешение**: письмо Oreville Studios / Element Animation и marcy; публиковать после письменного «да».
3. **Не публиковать**: мод остаётся приватным (только сборки для себя).

## 1. Версия

`mod_version` = **1.0.0** (`gradle.properties`), jar `villager-life-1.0.0+<mc>.jar`, AGENTS/STATUS/`prod_client.py`.
Старые теги `v1.0.0…v1.0.5`, `1.0.5-1.21.1-ru.1`, `v1.0.5-1.21.3` и **GitHub-релизы v1.0.0…v1.0.5** — апстрима
Akira-Media, в их jar лежат оригинальные голоса Element. Релиз мода — тег `1.0.0` (без `v`, в новой истории).

## 2. Сравнение с крупными модами (Create, Farmer's Delight, MineColonies, Comforts, MCA Reborn)

| Что | У них | У нас |
|---|---|---|
| `LICENSE` с понятной лицензией кода и ассетов | Create MIT; FD MIT код + отдельно ассеты; MineColonies GPL-3 | нет — смесь MIT/без лицензии/чужие ассеты (см. п. 0) |
| `neoforge.mods.toml`: authors, credits, displayURL, issueTrackerURL, logoFile, description | всё заполнено | было `authors="Villager Life"`, без URL — **исправлено** |
| `CHANGELOG.md` | да | **добавлен** |
| `CONTRIBUTING.md`, шаблоны issue/PR | да | **добавлены** |
| Иконка (не ИИ для Modrinth, 400×400 для CurseForge) | да | `icon.png` 512×512 похожа на ИИ — **заменена** рисунком скрипта (`tools/art/paint_icon.py`) |
| CI: сборка на PR, релиз по тегу | да | только ручной `build.yml` — **добавлен release по тегу** |
| `updateJSONURL` | Create, FD | не нужен (обновления — через лаунчеры) |
| Ссылки Modrinth/CurseForge в README | да | после публикации |

## 3. Остатки старого Villager News (кроме п. 0)

| Находка | Решение |
|---|---|
| `LICENSE` озаглавлен «Villager News Addon Port» | переписать после чистой комнаты: MIT, автор s00d, раздел про сгенерированные ассеты |
| `README*` «Characters and items» про персонажей Villager News | переписаны; после п. 0 — убрать раздел целиком |
| Реплики «Villager News is proud!», «Villager News!» (`adhvqz.2.2`, `lvigit.18.1`) | уйдут с п. 0 |
| `EMFModelPartMixin` — id части `villager_news_base_fgk6` | уйдёт с CEM-моделями |
| `tools/voice/ref_from_originals.py`, `manifests/legacy_dub.json`, `full_voices.json` | ссылки на оригиналы Element: удалить после п. 0 |
| `tools/expand/scripts/__pycache__/*.pyc` в git | удалить из git |
| `MODLOG.md`, `docs/plans` | история работы — оставить (не в jar) |
| пакет `com.vnap`, префикс миксинов `vnap$`, команды `/vnap` | имя Java-пакета, не видно игроку — оставить |
| `run/` dev-сейвы, `neoforge/libs` | в .gitignore — ок |

## 4. ИИ-контент (важно для Modrinth)

С 13.08.2026 Modrinth: проект не может быть «entirely or primarily» ИИ; **иконка и галерея не могут быть ИИ**;
нужна пометка «Contains AI-generated content». У нас: код в основном написан с ИИ-ассистентом, все голоса —
TTS, звуки и музыка — fal, текстуры доски — fal. Поэтому картинки для README/страниц — **только скриншоты
dev-клиента и рисунки скриптом**, fal для них не тратим. Риск «primarily AI» для Modrinth остаётся — решать
пользователю; CurseForge требует только пометку на изменённых ИИ скриншотах.

## 5. История git и репозиторий

- `.git` — 929 МБ (голоса Element в истории апстрима, 2212 клипов, и наши перегенерации).
- GitHub: `s00d/villager-news-addon-neoforge-port`, private, **fork** (сеть DMCA-репозитория), ветки
  `main`, `neoforge-1.21.1`, `neoforge-1.21.3`; PR нет; форков нет; 6 релизов апстрима с jar.
- Переименование форка оставит его в сети форков. План: **новый не-форк репозиторий `s00d/villager-life`**,
  одна начальная история с текущего состояния (без голосов Element), старый — удалить или архивировать
  (необратимо — по «да» пользователя). Перед этим дождаться, пока сессии CI/mcp-port закоммитят.

## 6. Чистая комната — итог (21–22.10.2026)

Решение пользователя — вариант 1. Сделано:

| Что из п. 0 | Итог |
|---|---|
| Модели CEM, ESF, текстуры жителей и персонажей, косметика, яйца, анимации `oreville_vn`, звуки боли | удалены (`4a46eb61`); жители — ванильная модель + наши слои (`VillagerOutfitLayer`, `VillagerModelMixin`: причёски, бантик, кивок при разговоре); боль — ванильный звук; EMF/ETF/ESF больше не нужны. В jar 1.21.1 нет ни одного файла с blob из апстрима |
| Код marcy (32 файла) | переписан с нуля (`f8ba73c0`, `79402363`): `Speech`, `SpeechQueue`, `Voices`, `VillagerTalk`, реакции по темам; `LICENSE` — MIT, s00d; `LICENSE-PORT` удалён |
| Особые персонажи, носы, косметика, таблички в руках | удалены из кода и данных: 73 группы реплик (`a56c636a` и следующий коммит) |
| Тексты реплик Villager News | 1744 варианта переписаны вручную (EN и RU, 10 параллельных проходов с проверкой каждой строки), ещё 83 близких к оригиналу — отдельным проходом; 462 описания групп (`body`, видны в `ReactionsScreen`) — свои. Повторная сверка: 0 дословных совпадений с `en_us.json` апстрима, 0 описаний |
| Голоса | старые клипы переписанных строк удалены, на их месте `*.PENDING_TTS` — озвучку перегенерирует пользователь (`dub.py --lang en`, `--lang ru`) |
| Скрипты, тянувшие апстрим | `restore_en_from_upstream.py`, `ref_from_originals.py`, `manifests/legacy_dub.json` удалены |
| Картинки | иконка и баннер — скриптами (`tools/art/paint_icon.py`, `paint_banner.py`), галерея — скриншоты dev-клиента (`docs/media/`), кадр голема в книге переснят |

Осталось от прежнего имени, не видно игроку: Java-пакет `com.vnap`, префикс миксинов `vnap$`, команды `/vnap`
(это сокращение, не имя чужого проекта). Названия ситуаций (`title` групп) — короткие описания триггеров
(«Place a Bed», «See a Zombie»): функциональные, не творческий текст; оставлены, на них ссылается код.

# Villager Life на Modrinth — что заполнять (24.10.2026)

> Заполнено через API 24.10.2026 (проект `villager-life-s00d`, id `b4ZjMej0`, черновик): summary, тело (`body.md`, со скриншотами с CDN Modrinth), категории, лицензия, ссылки, 13 кадров галереи. Ссылки на GitHub (Source, Issues, URL лицензии и в тексте) убраны, пока репозитория нет: Modrinth не может их проверить. Вернуть после создания `s00d/villager-life`. Осталось руками: выключить монетизацию (TTS), Disclosures → AI, а окружение `client_and_server` и зависимости JEI/EMI — в каждой версии при загрузке.

Публикует пользователь. Здесь — значения полей, проверенные по исходникам Modrinth (labrinth, frontend) и правилам на 24.10.2026.
Описание — `description.md`, галерея — `gallery.md` + `docs/media/gallery/`.

## До публикации (блокеры)

- [ ] Публичный репозиторий `s00d/villager-life`: на него ведут `issueTrackerURL`/`displayURL` в `neoforge.mods.toml`, README, LICENSE и описание. Сейчас 404 — ссылки Source/Issues будут битыми.
- [x] Озвучка: `*.PENDING_TTS` нет, по 5266 клипов EN и RU (24.10.2026).
- [ ] Лицензия модели TTS требует отдельной письменной лицензии для коммерческого использования. **Не включать монетизацию (Creator Rewards)**, пока её нет.
- [ ] Риск правила «primarily AI»: проект, сделанный «почти целиком ИИ с минимумом человеческого участия кроме промптов и тестов», не может быть публичным. В `description.md` раздел «How it was made» честно перечисляет, что сделано человеком; решение — за пользователем.

## Проект

| Поле | Значение |
|---|---|
| Name | `Villager Life` (3–64) |
| Slug (URL) | `villager-life` |
| Summary (≤255, без форматирования и без названия) | `Villagers live whole lives: they build their own town by hand, raise families, trade with coins and talk in English or Russian voices.` |
| Description (markdown ≤65 536) | `description.md` целиком |
| Icon (<512 КиБ) | `neoforge/src/main/resources/assets/villager_life/icon.png` или `docs/media/icon-400.png` (нарисованы скриптом, не ИИ) |
| Primary categories (≤3) | `game-mechanics`, `mobs`, `economy` |
| Additional categories | `adventure` (Шпиль), `decoration`, `social` |
| Environment | `Client and server` (`client_and_server`) — поле Environments в настройках проекта; старые client_side/server_side больше не используются |
| License | Custom → ID `LicenseRef-Villager-Life-License`, URL `https://github.com/s00d/villager-life/blob/main/LICENSE` (так же сделан Create) |
| Issues | `https://github.com/s00d/villager-life/issues` |
| Source | `https://github.com/s00d/villager-life` |
| Wiki | — (справочник в игре) |
| Discord | `https://discord.gg/JkNspxqWk9` |
| Disclosures → AI-generated content | **assets** (голоса TTS, звуки, музыка), **code** (код написан с ассистентом), **text** — если считать реплики, написанные с ассистентом. Иконка и галерея — не ИИ |
| Monetization | выключено (лицензия модели TTS) |
| Gallery | 13 картинок, первая `featured` — `gallery.md` |

## Версии

Modrinth **не читает зависимости из `neoforge.mods.toml`** и не знает окружение из jar. Веб-форма сама берёт из toml только номер (`1.0.0`), имя, загрузчик `neoforge` и версии игры из диапазона `minecraft`. Номер версии и диапазон 26.x поправить руками.

Один jar = одна версия Modrinth (вариант «отдельная версия на язык»: лаунчеры и модпаки ставят только основной файл версии, так что второй язык как доп. файл никто не скачает). Два jar с одним modid вместе не ставятся — сказано в описании.

| Jar | Version number (≤32) | Version name (≤64) | Game versions |
|---|---|---|---|
| `villager-life-1.0.0+1.21.1-ru.jar` | `1.0.0+1.21.1-ru` | `Villager Life 1.0.0 — 1.21.1 (Russian voices)` | 1.21.1 |
| `villager-life-1.0.0+1.21.1-en.jar` | `1.0.0+1.21.1-en` | `Villager Life 1.0.0 — 1.21.1 (English voices)` | 1.21.1 |
| то же для 1.21.4, 1.21.8, 1.21.11 | `1.0.0+<mc>-<lang>` | | ровно одна версия |
| 26.1.2 | `1.0.0+26.1.2-<lang>` | | форма предложит 26.1, 26.1.1, 26.1.2 (`[26.1,26.2)`): оставить проверенные |
| 26.2, 26.3 | `1.0.0+26.2-<lang>`… | | диапазоны `[26.2,26.3)`, `[26.3,26.4)`: оставить проверенные |

В каждой версии:
- Loader: `NeoForge`.
- Release channel: `Release` (или `Beta` для первой публикации).
- Dependencies: JEI (`jei`) — Optional, EMI (`emi`) — Optional. NeoForge и Minecraft как зависимости не добавляются.
- Changelog: секция `1.0.0` из `CHANGELOG.md`.
- **Порядок**: сначала `-ru`, потом `-en` — лаунчер при обновлении берёт самую новую совместимую версию, по умолчанию пусть будет английская.

Размер: лимит 500 МиБ на файл (en ~228 МБ, ru ~171 МБ — проходят).

## Что поправлено в моде (24.10.2026)

- `neoforge.mods.toml`: убран мусор шаблона, `logoBlur=false` (пиксельная иконка), описание дополнено Шпилем, `updateJSONURL` намеренно не задан (Modrinth сравнил бы `1.0.0+1.21.1-en` с `1.0.0` и всегда писал бы «есть обновление»).
- `mod_license` = `Villager Life License (source-available, all rights reserved)` — текст для списка модов.
- README/README.ru: Шпиль, `-en`/`-ru` jar, «до трёх голосов», Discord, лицензия.

Не сделано: нижняя граница NeoForge (`[${neo_version},)` требует ровно сборку, с которой собирали — 21.1.252 и т. д.; опустить можно только до проверенной сборки).

## Автопубликация (по желанию)

Minotaur 2.10.0 (официальный плагин, есть `environment`). В сборку не добавлен: нужен доступ в сеть (`--offline` сборка упадёт без кэша плагина). Сниппет для `neoforge/build.gradle`:

```groovy
plugins { id 'com.modrinth.minotaur' version '2.10.0' }

// neoforge/versions/<v>/gradle.properties: modrinth_game_versions=1.21.1
modrinth {
    projectId = 'villager-life'
    versionNumber = "${mod_version}+${minecraft_version}-${voice}"
    versionName = "Villager Life ${mod_version} — ${minecraft_version} (${voice == 'ru' ? 'Russian' : 'English'} voices)"
    versionType = 'release'
    uploadFile = tasks.named('jar')
    gameVersions = modrinth_game_versions.split(',') as List
    loaders = ['neoforge']
    environment = 'client_and_server'
    dependencies {
        optional.project 'jei'
        optional.project 'emi'
    }
    debugMode = providers.gradleProperty('modrinthDry').isPresent()
}
```

Запуск (сначала с `-PmodrinthDry`): `MODRINTH_TOKEN=… ./gradlew --no-configuration-cache :neoforge:1.21.1:modrinth -Pvoice=ru`, затем `-Pvoice=en`. Токен — scope `CREATE_VERSION`. `syncBodyFrom` не включать: перезаписанное описание не вернуть.

## Порядок публикации

1. Создать проект (он создаётся черновиком), заполнить таблицу «Проект», загрузить иконку и галерею.
2. Загрузить версии по таблице (минимум одна нужна для подачи).
3. Disclosures, Environment, License — проверить ещё раз.
4. Submit for review → processing → approved / rejected.

Источники: modrinth.com/legal/rules (13.08.2026), modrinth.com/news/article/ai-policy-and-disclosures, modrinth.com/news/article/new-environments, docs.modrinth.com/api, github.com/modrinth/code (labrinth `project_creation.rs`, `version_creation.rs`, `validate/neoforge.rs`; frontend `helpers/infer/loader-parsers.ts`), github.com/modrinth/minotaur, docs.neoforged.net/docs/gettingstarted/modfiles

# Villager Life на CurseForge — что заполнять (24.10.2026)

> Заполнено на сайте 24.10.2026 (проект `villager-life-s00d`, id 1763169, unlisted, не одобрен): описание (Markdown, `description.md`), summary, главная категория Mobs + Farming, Structures, Adventure and RPG, Armor/Tools/Weapons, Discord в соцсетях, Custom License (текст `LICENSE`), 13 скриншотов с подписями (первый — featured), Default Relations: JEI и EMI — optional. Не заполнено: Source (репозиторий ещё 404), файлы.

Проект: https://authors.curseforge.com/#/projects/1763169 (id 1763169). Описание и картинки CurseForge через API не
принимает (токен — только для файлов), их заполняют на сайте. Публичная ссылка до одобрения:
`https://www.curseforge.com/minecraft/mc-mods/villager-life-s00d` (после одобрения перенаправит на страницу мода).

## Поля проекта

| Поле | Значение |
|---|---|
| Name | `Villager Life` (без версии и слова «Minecraft» — правило CF) |
| Summary (одно предложение, не копия описания) | `Villagers live whole lives: they build their own town by hand, raise families, trade with coins and talk in English or Russian voices.` |
| Description | `description.md` (редактор в режиме Markdown) или `description.html` (режим HTML/WYSIWYG): тот же текст, ссылки Discord/исходники, 7 скриншотов (CDN Modrinth, открыт всем), JEI/EMI — на страницы CurseForge, без ссылок на скачивание в других местах |
| Avatar (400×400, не WebP) | `docs/media/icon-400.png` |
| Primary category | Mobs |
| Additional categories | Adventure and RPG, Structures, Armor, Tools, and Weapons, Food |
| License | Custom License — текст из `LICENSE` целиком |
| Socials / links | Issues: `https://github.com/s00d/villager-life/issues`, Source: `https://github.com/s00d/villager-life`, Discord: `https://discord.gg/JkNspxqWk9` |
| Gallery | 13 кадров `docs/media/gallery/*.jpg`, подписи — `docs/release/modrinth/gallery.md` (Title → Title, Description → Description); первым — `01-town-and-spire.jpg` |
| AI | в описании — раздел «How it was made»; при подаче файла — отметить ИИ-ассеты, если форма спросит |

Правила CF: никаких внешних ссылок на скачивание и донатов в описании (в `description.md` их нет; ссылки на Discord и
исходники разрешены), changelog к каждому файлу, музыка — указать происхождение (fal: Lyria, ElevenLabs Music).

## Файлы (когда версия будет готова)

Один jar = один файл; `-ru` и `-en` — отдельные файлы одной и той же версии игры (сначала `-ru`, потом `-en`).
Загрузка: `POST https://minecraft.curseforge.com/api/projects/1763169/upload-file`, заголовок `X-Api-Token`,
multipart: `file` (jar) и `metadata`:

```json
{
  "changelog": "<секция 1.0.0 из CHANGELOG.md>",
  "changelogType": "markdown",
  "displayName": "Villager Life 1.0.0 — 1.21.1 (English voices)",
  "gameVersions": [11779, 10150, 9638, 9639, 11135],
  "releaseType": "release",
  "relations": { "projects": [
    { "slug": "jei", "type": "optionalDependency" },
    { "slug": "emi", "type": "optionalDependency" }
  ] }
}
```

ID версий (из `GET /api/game/versions`, тип `minecraft-*`), всегда вместе с NeoForge 10150, Client 9638, Server 9639:

| Minecraft | id | Java |
|---|---|---|
| 1.21.1 | 11779 | Java 21 — 11135 |
| 1.21.4 | 12281 | 11135 |
| 1.21.8 | 13620 | 11135 |
| 1.21.11 | 14406 | 11135 |
| 26.1.2 | 16082 | Java 25 — 14454 |
| 26.2 | 16498 | 14454 |
| 26.3 | 17045 | 14454 |

```bash
curl -H "X-Api-Token: $CURSEFORGE_TOKEN" -F "metadata=<metadata.json" -F "file=@neoforge/versions/1.21.1/build/libs/villager-life-1.0.0+1.21.1-ru.jar" https://minecraft.curseforge.com/api/projects/1763169/upload-file
```

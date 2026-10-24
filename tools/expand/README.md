# VNAP expand — реакции CreateAERO (grief + soft-compat + иконки)

Канон-репо: этот `villager-news-ru-port` (fork s00d, 1.0.5 + RU).  
**Не** использовать `/tmp/villager-news-nf` как source.

## Что добавлено

- ~100 новых dialogue groups: Phase A (grief/огонь/дом), B (категории модов), C (иконки предметов).
- Soft-compat: `ModFlavorMatchers.java` — только `namespace`/`path`, без hard-deps и без import чужих модов.
- Триггеры в `dialogue/` (`WorldEvents`, `PlayerWatch`, `Harm`, `Surroundings`) (огонь у дома, hold/place/break/use, пушки, FD gift, golem kill, explosion…).
- Спека: `spec/chunks/*.yaml` (**EN-primary**: обязателен `frames_en`; `frames`/`frames_ru` = RU).
- Пайплайн: YAML → assets/manifest → `tools/voice/dub.py --lang en|ru` → pack в `voice/en` и `voice/ru` → duration → `./gradlew build`.
- Клиент: язык `ru*` → RU voice + `ru_ru.json`; иначе EN voice + `en_us.json` (нет RU-ключа → fallback на EN).

## Спеки — чанки (не один мегафайл)

Правь реплики в `spec/chunks/`:

| Файл | Содержание |
|------|------------|
| `A_grief.yaml` | огонь / дом / grief |
| `B_create.yaml`, `B_combat.yaml`, `B_tech.yaml`, `B_life.yaml` | категории модов |
| `C_icons.yaml` | иконки предметов |
| `D_vanilla_events.yaml` | NeoForge/ванильные события |
| `E_mood_cult.yaml` | mood / монумент / обряды (культ удалён, имя файла историческое) |
| `F_workshops.yaml` | мастерские, станки, работа жителей |
| `G_children.yaml` | дети (`speaker: child`): эхо на реплики взрослых + игра днём и отбой вечером |
| `H_golem.yaml` | голос Tocsin Golem (`speaker: golem`): атаки, фазы, удары, насмешки; заголовки = `GolemVoice` |
| `I_women.yaml` | женщины (`speaker: woman`): подмены общих реплик (`Family.WOMAN_LINES`) и семейные моменты |
| `J_family.yaml` | мужья и гости свадьбы (`speaker: villager`) |
| `K_family_kids.yaml` | дети в семейных моментах (`speaker: child`) |

## Быстрый порядок

```bash
cd "/Users/s00d/Downloads/FireShot/villager-news-ru-port"

# 1) применить чанки (EN-primary)
python3 tools/expand/scripts/gen_from_spec.py
# python3 tools/expand/scripts/gen_from_spec.py --purge
# python3 tools/expand/scripts/gen_from_spec.py --rebuild-manifest

# 2) полный список клипов base+expand для EN TTS с нуля
python3 tools/expand/scripts/build_full_voice_manifest.py

# 3) СТОП — озвучка (ты) пишет сразу в sounds/voice/<lang>, см. tools/voice/dub.py и scan.py
.venv/bin/python tools/voice/dub.py --lang ru
.venv/bin/python tools/voice/dub.py --lang en

# 4) сверка id модов
python3 tools/expand/scripts/verify_mod_ids.py \
  --mods-dir "/Users/s00d/Downloads/FireShot/CreateAERO 2.2/mods"

# 6) длительности из готовых ogg (EN и RU, длиннейший клип; dub.py делает это сам)
python3 tools/expand/scripts/duration_from_ogg.py

# 7) после TTS+verify:
./gradlew build
```

## Файлы

| Путь | Роль |
|------|------|
| `spec/chunks/*.yaml` | источник реплик |
| `tools/voice/manifests/new.json` | expand-клипы (`en_tts` / `ru_tts`) |
| `tools/voice/manifests/full_voices.json` | все 2700+ клипов для EN/RU Fish |
| `out/id_map.json` | `A1`/`B4`/`C15` → dialogue id |
| `sounds/voice/en|ru/*.ogg` | bilingual TTS |
| `sounds/voice/en|ru/*.PENDING_TTS` | маркеры до TTS |

## Правила soft-compat

- Нет `mandatory` deps на Create/TACZ/Mekanism/…
- Нет `import` классов чужих модов.
- Нет мода → matcher не срабатывает, ванильный VNAP жив.
- Smoke: мир только с VNAP + мир CreateAERO.

## Тон реплик

RU = игровой текст и TTS. Фразы/предложения (не «Ой!» одним кадром). Юмор и характер — свои.

## Phase E (mood/cult)

`spec/chunks/E_mood_cult.yaml` — director titles; stub clips already in `dialogues.json` (voice/ii placeholders until dub.py).

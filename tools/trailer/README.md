# Ролики Villager Life

Два ролика, каждый на EN и RU, с закадром и без:

| | Что | Голос |
|---|---|---|
| **film1** — «Один день и одна жизнь деревни» (~3:13) | путник приходит в деревню, деревня растёт в город, Шпиль, герой | рассказчик George (`voices.json` → `narrator`) |
| **film2** — «I installed one mod…» (~3:40) | от первого лица: игрок ушёл копать, вернулся — деревня живёт; Шпиль, быстрый монтаж боёв | игрок Liam (`voices.json` → `player`) |

Видео (большие) лежат вне репо: `~/Movies/VillagerLife/film{1,2}/` — `out/` (готовые ролики, `.srt`/`.ass`), `work/<lang>/` (дубли),
`raw/` (миры, с которых снимали: `world-pristine` — свежая деревня, `world-town` — выросший город), `film1/old/` (первая версия).
Ссылки `vo`, `music`, `edit/edl.json`, `STORYBOARD.md`, `tools` ведут сюда.

Здесь (в репо): сценарии и раскадровки (`film*/STORYBOARD.md`, `film*/vo/script.json`), озвучка (`film*/vo/{en,ru}/*.wav`),
музыка (`film*/music/*.mp3`, её промты — `film*/music/fal_manifest.jsonl`), монтажные листы (`film*/edl.json`), промты — `prompts.md`.

## Как снимать

- Клиент для съёмки: `tools/trailer/client.sh [Мир]` — звук игры пишется в WAV (OpenAL wave driver, `alsoft.conf`), окно скрыто, 1920×1080.
- Свой рекордер в моде (только dev): `/vnaprec start|stop NAME`, `hud true|false|clean`, `lang ru_ru`, `lapse` (таймлапс);
  камера `/vnapcam path|orbit|follow|watch|fov|bob`; бот `/vnapbot walk|mine|duel|click|mouse` (`client/dev/*`).
- `shoot.py take NAME SECONDS "CAM" [--hud --pre CMD --at T CMD --rate N]` — дубль в `work/<lang>/` и лист кадров; `shoot.py lang ru|en` —
  язык игры и голосов (`voice.sh` подменяет клипы в build-ресурсах; после перезапуска клиента — снова).
  Ролик — `VL_FILM=~/Movies/VillagerLife/film1` (по умолчанию film2). CAM: `path …`, `orbit …`, `still …`, `walk x y z yaw pitch; точки; look x y z` (пешком).
- Сцены целиком: `takes1_fresh.sh`, `takes1_town.sh` (film1), `takes_fresh.sh`, `takes_town.sh` (film2), бои Шпиля — `fights.py ЭТАЖ[:keeper|:die] …`.
- Монтаж: film2 — `plan.py` (пишет `edl.json`), затем `edit.py ~/Movies/VillagerLife/film2/edit/edl.json en|ru [--vo]`;
  film1 — `VL_FILM=~/Movies/VillagerLife/film1 edit.py …/film1/edit/edl.json en|ru [--vo]`. Заставки film2 — `cards.py [en|ru]`.
- Озвучка: `tts.py player|narrator en|ru film*/vo/script.json film*/vo/<lang>` (fal ElevenLabs, ключ `~/.secrets/fal-key`, уже готовые не делает).
- Проверка: листы кадров (`frames.py`), Whisper по готовому ролику.

`RESTORE.md` — что вернуть в dev-клиенте после съёмок (звук, моды, настройки).

## YouTube

`publish/` — названия, теги, описания (`film*_<lang>.txt`) и превью (`thumb_film*_<lang>.jpg`, `thumbs.py`) четырёх роликов с закадром
(ролики без закадра не выкладываются). Субтитры — `film*/out/*.srt`. Ссылки:
film1 EN https://youtu.be/oe4SSerVNHs · film1 RU https://youtu.be/mHCvLn-c2_s · film2 EN https://youtu.be/VIsO7N__Pn0 · film2 RU https://youtu.be/dO6bLLSdPL4

# Промты генерации роликов

Всё платное — через fal (ключ `~/.secrets/fal-key`). Цены на момент генерации: ElevenLabs TTS — 0,10 $ за 1000 символов, Lyria 3.5 — 0,10 $ за трек.

## Голоса (`voices.json`)

- **narrator** — `fal-ai/elevenlabs/tts/multilingual-v2`, `{"voice": "George", "stability": 0.55, "similarity_boost": 0.8, "style": 0.15, "speed": 0.95}`. Calm warm storyteller (trailer 1). ElevenLabs George, multilingual v2: same voice for EN and RU.
- **player** — `fal-ai/elevenlabs/tts/eleven-v3`, `{"voice": "Liam", "stability": 0.4}`. The player, a guy about 18, casual streamer tone (trailer 2). ElevenLabs v3 (Liam; Will was turned down): takes tags like [surprised], [laughs], [whispers].

Тексты — `film1/vo/script.json` (рассказчик, EN и RU), `film2/vo/script.json` (игрок; теги ElevenLabs v3 вроде `[surprised]` в тексте).

## Музыка film1 (`google/lyria-3.5`, 60 с, без вокала)

- **village**: warm cinematic folk opening for a cozy village life game trailer, gentle acoustic guitar and celtic harp intro, then fiddle, tin whistle and soft strings join, light hand percussion, morning sunrise feeling, hopeful and heartwarming, steady 96 bpm, builds slowly, instrumental only, no vocals
- **spire**: epic fantasy adventure orchestral music for a game trailer, a mysterious ancient tower climb, starts with low strings, deep taiko drums and a distant choir hum, grows into heroic full orchestra with brass fanfares, boss battle intensity, then a triumphant resolution, 110 bpm, instrumental only, wordless choir allowed but no lyrics
- **town**: upbeat medieval town-building montage music for a game trailer, lively folk orchestra: fiddle, accordion, mandolin, pizzicato strings, frame drums and claps, playful and busy like builders at work, rises to a joyful triumphant festival climax with brass, 120 bpm, instrumental only, no vocals

## Музыка film2 (`google/lyria-3.5`, 60 с, без вокала)

- **cozy**: cozy relaxed acoustic indie background music for a Minecraft let's play, gentle fingerpicked acoustic guitar, soft glockenspiel and light shaker, warm and curious, sunny afternoon vibe, 90 bpm, loopable, calm and unobtrusive under talking, instrumental only, no vocals
- **epic**: epic hybrid trailer music for a boss rush montage, opens with a huge braam hit and silence, then driving taiko and staccato strings at 150 bpm, heavy synth bass, brass stabs, rising choir, relentless energy with hard hits every bar, big climax and a final impact, instrumental, wordless choir only

## Субтитры и титры

Титры film1 — в `film1/edl.json` (`titles`, EN/RU); заставки film2 рисует `cards.py` (тексты в `TEXTS`), штамп «THE BUILDERS' SPIRE / ШПИЛЬ ЗОДЧИХ» — в `plan.py`.

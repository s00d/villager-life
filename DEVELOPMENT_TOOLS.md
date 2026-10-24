# How Villager Life is made: tools and MCP servers

Villager Life is designed, directed, tested and played by s00d. Most of the code was written with an AI coding
agent under that direction and review; the game itself was driven by the agent through MCP servers so that every
change could be checked in a running Minecraft, not only in tests. This page lists what was used and what for.

## The agent

- **[Claude Code](https://claude.com/claude-code)** (Anthropic) — the coding agent: reads and writes the code, runs
  the build and the tests, drives the game and the dev servers, reads logs. Project knowledge for it lives in
  [`AGENTS.md`](AGENTS.md) (what the mod is, every system, the rules) and the `vl-dev` skill in `.claude/skills/`
  (how to check things in game).
- **universal-modder** — a Claude Code plugin for game modding: a brief for workers ([`HANDOFF.md`](HANDOFF.md)), the
  state in plain words ([`STATUS.md`](STATUS.md)), a dated log ([`MODLOG.md`](MODLOG.md)) and the `um fal` recipes for
  sound effects and music.

## MCP servers

| Server | Used for |
|---|---|
| **[minecraft-mcp](https://github.com/s00d/minecraft-mcp)** 0.2.0 (s00d) | A mod for the client and the dedicated server that exposes the running game over MCP: chat commands with their output, screenshots, the player's position and camera, clicks on blocks and widgets, container state, entities nearby, tick sprint, logs. The agent plays, builds test scenes and records shots through it (`tools/dev/mcp.py`, `mcc.py`, `scenario.py`). |
| **[Context7](https://context7.com)** | Current documentation of NeoForge, Minecraft APIs and libraries, instead of guessing from memory. |
| **fal** (through `um fal`) | Generated sound effects (ElevenLabs Sound Effects v2) and music (Google Lyria 3.5, ElevenLabs Music); for the videos, narration (ElevenLabs) and music. Every request is logged with its model and prompt in `tools/art/fal/*/fal_manifest.jsonl` and `tools/trailer/film*/music/fal_manifest.jsonl`. |
| **[discord-mcp](https://github.com/SaseQ/discord-mcp)** | Setting up the project's Discord server: channels, roles, permissions, the welcome, rules, FAQ and gallery posts. |
| **Claude in Chrome / the Claude desktop browser** | Filling in the CurseForge project page (description, licence, gallery, relations). |

## Build and checks

- **Gradle**, **NeoForge ModDevGradle**, **[Stonecutter](https://stonecutter.kikugie.dev/)** 0.9.8 — one source tree
  built for Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3; Java 21 and 25.
- **ErrorProne**, **NullAway**, **SpotBugs** with `-Werror` — the build fails on any warning.
- **NeoForge GameTests** — 365 tests of the mod's behaviour (`neoforge/src/gametest`), run four servers at once by
  `tools/dev/gts.sh`.
- **Live checks** in the dev client and on dedicated servers: `tools/dev/restart.sh`, `server.py` (servers with a
  bot player), `sweep.py` (fresh villages on several seeds in parallel), `scenario.py` (recorded multi-day checks),
  `deaths.py` (where and why villagers died), `prod_client.py` / `client_check.py` (a production client of any
  version through **portablemc**).
- **GitHub Actions** (`.github/workflows/build.yml`) — builds every version and both voice jars, runs the tests.
- **`check_lines.py`, `check_lang.py`, `check_guide.py`** — every dialogue line has its English and Russian text and a
  voice clip; every translation key exists in both languages; the guide fits its pages.

## Content tools

- **Blockbench** — block, item and entity models (Java block JSON and Bedrock geometry).
- **Python + Pillow** scripts in `tools/art/` — textures, the icon, the guide's art, the window kit, the Spire's
  blocks and gear, the banner. No image in the mod or on its pages is AI-generated.
- **Minecraft structure blocks** — every building of the mod is a vanilla `.nbt` structure built in game.
- **The mod's dev recorder and camera** (`/vnaprec`, `/vnapcam`, dev builds only) and **ffmpeg** — the videos, cut by scripts in `tools/trailer/`; thumbnails from the videos' own frames (`thumbs.py`).
- **Fish Audio S2 Pro** (MLX, run locally) — the villagers' voices from the mod's own lines (`tools/voice/dub.py`);
  **Whisper** (mlx-whisper) checks that each clip says its whole line.
- **ffmpeg**, **libvorbis** / **soundfile** — mono Ogg Vorbis for every sound, so Minecraft places it in the world.

## Publishing

- **GitHub CLI** — the repository and its releases.
- **Modrinth API** and the **CurseForge upload API** — project pages, galleries and files.

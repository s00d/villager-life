# Changelog

All notable changes to Villager Life. Versions follow [Semantic Versioning](https://semver.org/);
each Minecraft version has a jar per voice language: `villager-life-<version>+<minecraft>-<en|ru>.jar`.

## [Unreleased]

**Fabric.** The mod runs on Fabric (with Fabric API) as well as NeoForge and Forge, on every Minecraft version:
1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3, its own jar
`villager-life-fabric-<version>+<minecraft>-<en|ru>.jar`. JEI and EMI (1.21.1) show the mod's recipes there too.

Fixed:
- A homeless villager looks for a room among the town's built houses only: a reserved plot could push the real house
  out of the nearest few, and the villager stayed outside.
- Two holders of the same chunk no longer release it for each other (Forge from 1.21.5).
- On 26.x the "Villager Trade Rebalance" experiment no longer replaces the mod's decor trades on Fabric.

## [1.1.0] — 2026-10-26

**Forge.** The mod runs on Forge as well as NeoForge, on every Minecraft version: 1.21.1, 1.21.4, 1.21.8, 1.21.11,
26.1.2, 26.2 and 26.3. Forge takes its own jar, `villager-life-forge-<version>+<minecraft>-<en|ru>.jar`. On Forge,
JEI exists for 1.21.1 only; EMI works everywhere.

Fixed on every loader:
- A rare server freeze: the light engine read a chunk's blocks while they were being set (seen on Apple Silicon,
  most often while a Spire floor was being built).
- A bed's owner moving into a new house could leave a neighbour remembering the same bed (one of them slept outside).
- A villager standing a few seconds in the last cell of a build no longer stops it for the whole day.
- The Well of Lore no longer loses experience from merged orbs.
- The Colosseum's first wave keeps its share of shooters and flyers against a blade.
- A town's wall no longer runs round another town's unfinished house or graves.
- Name tags on Forge 26.2 and later.

## [1.0.0] — 2026-10-24

First release.

**Which file?** One jar per Minecraft version and voice: `-en` has English voices, `-ru` Russian ones. Install one of
them; text, subtitles and the guide follow your game language either way. NeoForge only, on the server and on every
client; JEI or EMI optional.

**Videos:** [overview](https://youtu.be/oe4SSerVNHs) · [let's play](https://youtu.be/VIsO7N__Pn0) ·
in Russian: [обзор](https://youtu.be/mHCvLn-c2_s) · [прохождение](https://youtu.be/dO6bLLSdPL4).
Discord: https://discord.gg/JkNspxqWk9

- Villagers live a whole life: work at workshops of their trade, build their houses and their town by hand,
  marry, raise children, grow old; a mayor, a shopkeeper, coins, wages and a treasury.
- The town plans streets and lots, lays roads, a square, a chapel with a tower clock, a bakehouse, a tavern
  and a wall; families pay for their houses, the treasury for the town.
- The village's mood is its opinion of the player; rites and the Tocsin Golem boss.
- The Spire of the Architects: a finished town raises a hundred-floor spire in ten chapters, with guards, ten keepers,
  trials, puzzles and secret rooms, gear forged at the Core Anvil; villagers go on expeditions and come back as heroes.
- Dialogue in English or Russian voices (one jar per voice language), subtitles, the Villager Life Guide book.
- Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3 on NeoForge.

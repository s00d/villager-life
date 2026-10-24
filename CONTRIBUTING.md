# Contributing

Thanks for helping with Villager Life.

## Licence

Villager Life is source-available, not open source: the code is published to read and learn from (see [LICENSE](LICENSE)).
By sending a pull request, a translation or an asset you confirm you have the right to send it and grant s00d the right
to use and license it as part of the mod (section 3 of the licence). You keep the copyright in your contribution.

## Bugs and ideas

Open an [issue](https://github.com/s00d/villager-life/issues) with the bug template. Say the Minecraft and
NeoForge versions, the mod version and other mods, and attach `logs/latest.log` (and the crash report, if any).
What did you do, what did you expect, what happened instead — a screenshot or the village's coordinates help a lot.

## Code

- Build: `./gradlew --no-configuration-cache build` (Java 21; the 26.x versions also need Java 25).
  The build runs ErrorProne, NullAway and SpotBugs with `-Werror` — warnings fail it.
- Game tests: `./gradlew --no-configuration-cache :neoforge:1.21.1:runGameTestServer`. New behaviour comes with a GameTest.
- The sources are written for Minecraft 1.21.1; other versions come from the same code through Stonecutter
  (`neoforge/versions/<version>`). Version differences go through the helpers `com.vnap.Mc`, `Nbt`,
  `client/McClient`, `client/Draw` — see the "Minecraft versions" part of `AGENTS.md`.
- Match the style of the code around you; comments only for what the code cannot say.
- `AGENTS.md` describes every system of the mod and the dev tools in `tools/dev`.

## Assets

Only content you made yourself or that is under a licence that allows it, with the source named in the pull request.
No assets from other mods, add-ons or resource packs.

Translations: the strings live in `tools/lang/*.lang.json` (run `python3 tools/lang/merge_lang.py` after a change)
and the guide in `assets/villager_life/guide/<lang>.json`.

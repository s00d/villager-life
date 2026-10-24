#!/bin/zsh
# Reads Minecraft/NeoForge sources (Mojang names) from the sources jar the build already has, without an IDE.
#   tools/dev/mcsrc.sh VillageSiege            the file(s) whose name matches, printed
#   tools/dev/mcsrc.sh -l Villager             only list the matching paths
#   tools/dev/mcsrc.sh -g 'spawnGolemIfNeeded' rg over all sources (who calls / defines it)
# VL_MC=1.21.4 reads that version's sources. Extracted once into /tmp/vl-mcsrc[-<VL_MC>] (about the size of the jar), reused after.
cd "$(dirname "$0")/../.." || exit 1
jar=$(ls neoforge/versions/${VL_MC:-1.21.1}/build/moddev/artifacts/*-sources.jar 2>/dev/null | head -1)
[[ -z "$jar" ]] && { echo "no sources jar: run a build first"; exit 1; }
dir=/tmp/vl-mcsrc${VL_MC:+-$VL_MC}
if [[ ! -f "$dir/.done" || "$jar" -nt "$dir/.done" ]]; then
  rm -rf "$dir"; mkdir -p "$dir"
  unzip -qo "$jar" '*.java' -d "$dir" && touch "$dir/.done"
fi
case "$1" in
  -g) shift; rg -n "$@" "$dir" | sed "s#$dir/##" ;;
  -l) rg --files "$dir" | rg "/$2[^/]*\.java$" | sed "s#$dir/##" ;;
  *) for f in $(rg --files "$dir" | rg "/$1\.java$"); do echo "== ${f#$dir/}"; cat "$f"; done ;;
esac

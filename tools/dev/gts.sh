#!/bin/bash
# Runs the game tests headless and prints the verdict. Full log: $VL_LOGS/vl-gts.log (/tmp by default) (never trimmed).
# usage: tools/dev/gts.sh                          all tests, on VL_SHARDS (4) test servers at once; then what held the run
#        tools/dev/gts.sh GenerationsGameTests,homes,anelderpassesawayatnight   only these: test classes,
#                                                  batches (homes, family_life, ...) or test names in lower case
#        VL_MC=1.21.8-forge tools/dev/gts.sh [...]    a Forge node: one test server (ForgeGradle's runGameTestServer)
cd "$(dirname "$0")/../.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home}"
L="${VL_LOGS:-/tmp}"  # several worktrees at once: give each its own VL_LOGS
mkdir -p "$L"
start=$(date +%s)
python3 tools/expand/scripts/check_lines.py || exit 1
python3 tools/lang/check_lang.py || exit 1
if [ -n "$1" ] || [[ "${VL_MC:-}" == *-forge ]]; then
  ./gradlew --no-configuration-cache --offline :neoforge:${VL_MC:-1.21.1}:runGameTestServer -Ptests="$1" > $L/vl-gts.log 2>&1
  code=$?
  rg -n "failed at|required tests|optional tests|BUILD|error:" $L/vl-gts.log
else
  # all of them: built once, then several test servers at once (gts_shards.py)
  ./gradlew --no-configuration-cache --offline :neoforge:${VL_MC:-1.21.1}:gametestClasses :neoforge:${VL_MC:-1.21.1}:createGameTestServerLaunchScript > $L/vl-gts-build.log 2>&1 \
    || { rg -n "error:|BUILD" $L/vl-gts-build.log; exit 1; }
  python3 tools/dev/gts_shards.py ${VL_SHARDS:-4}
  code=$?
fi
python3 tools/dev/slow_tests.py $L/vl-gts.log 8
echo "took $(( $(date +%s) - start ))s"
exit $code

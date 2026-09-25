#!/bin/sh
# Builds dist/open-diplom: a trimmed Java runtime (jlink), the program and
# launchers. Nothing has to be installed on the target machine (ADR-0007).
#
#   scripts/build-dist.sh [target JDK]
#
# The target JDK is the JDK of the platform the program will run on, for
# example Liberica JDK 11 for Windows x86 unpacked next to the build; its
# jmods are linked by the jlink of the JDK this script runs with. Without an
# argument the running JDK is the target.
set -eu
cd "$(dirname "$0")/.."
TARGET=${1:-${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}}
JAR=target/open-diplom.jar
[ -f "$JAR" ] || { echo "Сначала соберите программу: mvn package" >&2; exit 1; }
MODULES=$(jdeps --ignore-missing-deps --print-module-deps --multi-release 11 -q "$JAR")
OUT=dist/open-diplom
rm -rf "$OUT"
mkdir -p "$OUT"
jlink --module-path "$TARGET/jmods" \
    --add-modules "$MODULES,jdk.crypto.ec,jdk.localedata" --include-locales=ru,en \
    --strip-debug --no-man-pages --no-header-files --compress=2 \
    --output "$OUT/runtime"
cp "$JAR" "$OUT/"
cp scripts/open-diplom.cmd scripts/open-diplom.sh "$OUT/"
chmod +x "$OUT/open-diplom.sh"
cp LICENSE NOTICE "$OUT/"
du -sh "$OUT/runtime" "$OUT/open-diplom.jar"

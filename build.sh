#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC=javac
    JAR=jar
fi
if ! command -v "$JAVAC" >/dev/null 2>&1; then
    echo "Installez un JDK 17 ou plus récent pour compiler ce projet." >&2
    exit 1
fi
rm -rf build/classes
mkdir -p build/classes dist
find src -name '*.java' -print | LC_ALL=C sort > build/sources.txt
"$JAVAC" -encoding UTF-8 --release 17 -Xlint:all -d build/classes @build/sources.txt
cp -R resources/Puissance4/. build/classes/Puissance4/
"$JAR" --create --file dist/Puissance4.jar --main-class Puissance4.Main --manifest resources/MANIFEST.MF -C build/classes .
echo "Prêt : dist/Puissance4.jar"

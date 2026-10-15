#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
MODE="${1:-time}"
REPEATS="${2:-3}"
case "$MODE" in fixed|time|book) ;; *) echo "Usage: ./benchmark.sh [fixed|time|book] [1..20] [ancien.jar]" >&2; exit 1 ;; esac
if [[ -n "${3:-}" ]]; then
    BENCH_JAR="$3"
else
    ./build.sh >&2
    BENCH_JAR=dist/Puissance4.jar
fi
[[ -f "$BENCH_JAR" ]] || { echo "JAR introuvable : $BENCH_JAR" >&2; exit 1; }
mkdir -p build/benchmarks
"$JAVAC" -encoding UTF-8 --release 17 -cp "$BENCH_JAR" -d build/benchmarks benchmarks/Puissance4/Benchmark.java
"$JAVA" -Xms64m -Xmx512m -cp "$BENCH_JAR:build/benchmarks" Puissance4.Benchmark "$MODE" tests/data/exact-positions.csv "$REPEATS"

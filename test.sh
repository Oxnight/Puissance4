#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=javac
    JAVA=java
fi
mkdir -p build/tests
find tests -name '*.java' -print | LC_ALL=C sort > build/test-sources.txt
"$JAVAC" -encoding UTF-8 --release 17 -Xlint:all -cp build/classes -d build/tests @build/test-sources.txt
"$JAVA" -ea -cp build/classes:build/tests Puissance4.EngineTests
"$JAVA" -ea -cp build/classes:build/tests Puissance4.AdvancedTests
"$JAVA" -Xmx32m -ea -cp build/classes:build/tests Puissance4.LimitedHeapTests
if [[ "${1:-}" == "--gui" ]]; then
    "$JAVA" -ea -cp build/classes:build/tests Puissance4.GuiTests
    "$JAVA" -ea -Dpuissance4.appearance=light -Dpuissance4.reducedMotion=true -cp build/classes:build/tests Puissance4.AtelierTests
    "$JAVA" -ea -Dpuissance4.appearance=dark -Dpuissance4.reducedMotion=true -cp build/classes:build/tests Puissance4.AtelierTests
    "$JAVA" -ea -cp build/classes:build/tests Puissance4.gui.InteractionAuditTests
    "$JAVA" -ea -Dpuissance4.appearance=light -Dpuissance4.reducedMotion=true -cp build/classes:build/tests Puissance4.gui.LayoutAuditTests
    "$JAVA" -ea -Dpuissance4.appearance=dark -Dpuissance4.reducedMotion=true -cp build/classes:build/tests Puissance4.gui.LayoutAuditTests
fi

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
if ! "$JAVA" -version >/dev/null 2>&1; then
    echo "Installez Java 17 ou plus récent, puis relancez RUN.command."
    read -r -p "Appuyez sur Entrée pour fermer." _reply
    exit 1
fi
if [[ ! -f dist/Puissance4.jar ]]; then ./build.sh; fi
if ! "$JAVA" -jar dist/Puissance4.jar; then
    echo "Le lancement a échoué. Vérifiez que Java 17 ou plus récent est installé."
    read -r -p "Appuyez sur Entrée pour fermer." _reply
    exit 1
fi

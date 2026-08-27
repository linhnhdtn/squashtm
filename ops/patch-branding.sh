#!/usr/bin/env bash
# Chen <script src="plugin/dtn-myfeature/custom.js"> vao index.html cua SPA.
# Idempotent. Chay lai sau moi lan build lai frontend (build moi ghi de index.html).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"; set -a; . ./.env; set +a

WAR="$ROOT/.runtime/squash-tm/bundles/squash-tm.war"
INJECT='<script src="plugin/dtn-myfeature/custom.js" defer></script>'
MARKER='plugin/dtn-myfeature/custom.js'

[[ -f "$WAR" ]] || { echo "!! khong thay $WAR"; exit 1; }
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
FRONT_JAR=$(unzip -l "$WAR" | awk '{print $4}' | grep -E 'WEB-INF/lib/tm-front-.*\.jar$' | head -1)

[[ -f "$WAR.orig" ]] || cp "$WAR" "$WAR.orig"
unzip -q "$WAR" "$FRONT_JAR" -d "$WORK"
mkdir -p "$WORK/front" && (cd "$WORK/front" && unzip -q "$WORK/$FRONT_JAR" META-INF/resources/index.html)

if grep -q "$MARKER" "$WORK/front/META-INF/resources/index.html"; then
  echo "→ index.html da co custom.js, bo qua"; exit 0
fi

sed -i "s#</body>#  ${INJECT}\n</body>#" "$WORK/front/META-INF/resources/index.html"
(cd "$WORK/front" && zip -q "$WORK/$FRONT_JAR" META-INF/resources/index.html)
# -0 = STORED: Spring Boot loader khong doc duoc nested jar bi nen -> app 500
(cd "$WORK" && zip -0 -q "$WAR" "$FRONT_JAR")
unzip -v "$WAR" "$FRONT_JAR" | grep -q Stored || { echo "!! nested jar khong Stored, rollback"; cp "$WAR.orig" "$WAR"; exit 1; }
echo "✓ da chen custom.js — restart de ap dung"

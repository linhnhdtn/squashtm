#!/usr/bin/env bash
# Nhoi frontend da build vao tm-front-*.jar ben trong squash-tm.war.
#   ./front-patch/repack-front.sh            patch war
#   ./front-patch/repack-front.sh --restore  tra war ve ban goc
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"; set -a; . ./.env; set +a

WAR="$ROOT/.runtime/squash-tm/bundles/squash-tm.war"
DIST="$ROOT/.runtime/tm-front-src/tm/tm-front/dist/sqtm-app"

if [[ "${1:-}" == "--restore" ]]; then
  [[ -f "$WAR.orig" ]] || { echo "!! khong co $WAR.orig"; exit 1; }
  cp "$WAR.orig" "$WAR"; echo "✓ da tra war ve ban goc"; exit 0
fi

[[ -d "$DIST" ]] || { echo "!! chua build frontend: thieu $DIST (chay ./front-patch/apply.sh)"; exit 1; }
[[ -f "$WAR.orig" ]] || cp "$WAR" "$WAR.orig"

WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
FRONT_JAR=$(unzip -l "$WAR.orig" | awk '{print $4}' | grep -E 'WEB-INF/lib/tm-front-.*\.jar$' | head -1)

unzip -q "$WAR.orig" "$FRONT_JAR" -d "$WORK"
mkdir -p "$WORK/jar" && (cd "$WORK/jar" && unzip -q "$WORK/$FRONT_JAR")
rm -rf "$WORK/jar/META-INF/resources"; mkdir -p "$WORK/jar/META-INF/resources"
cp -r "$DIST/." "$WORK/jar/META-INF/resources/"
rm -f "$WORK/$FRONT_JAR"
(cd "$WORK/jar" && zip -q -r "$WORK/$FRONT_JAR" .)

cp "$WAR.orig" "$WAR"
# -0 = STORED, xem chu thich trong patch-branding.sh
(cd "$WORK" && zip -0 -q "$WAR" "$FRONT_JAR")
unzip -v "$WAR" "$FRONT_JAR" | grep -q Stored || { echo "!! nested jar khong Stored, rollback"; cp "$WAR.orig" "$WAR"; exit 1; }
echo "✓ xong — chay ./ops/patch-branding.sh roi restart"

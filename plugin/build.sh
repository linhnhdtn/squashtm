#!/usr/bin/env bash
# Build plugin dtn-myfeature bang javac + jar (khong can Maven / mang).
# Classpath lay tu WEB-INF/lib cua squash-tm.war da giai nen trong .runtime.
#   ./plugin/build.sh [--deploy]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a; . ./.env; set +a

SQUASH_HOME="$ROOT/.runtime/squash-tm"
LIBS="$ROOT/.cache/squash-libs"
OUT="$ROOT/plugin/target"
JAR="$OUT/dtn-myfeature-1.0.0.jar"

[[ -f "$SQUASH_HOME/bundles/squash-tm.war" ]] || { echo "!! chua co bo cai, chay ./bootstrap.sh truoc"; exit 1; }

if [[ ! -d "$LIBS/WEB-INF/lib" ]]; then
  echo "   giai nen thu vien tu war (chi lan dau)"
  mkdir -p "$LIBS"
  unzip -q -o "$SQUASH_HOME/bundles/squash-tm.war" 'WEB-INF/lib/*.jar' 'WEB-INF/lib-provided/*.jar' -d "$LIBS"
fi

CP=$(ls "$LIBS"/WEB-INF/lib/*.jar "$LIBS"/WEB-INF/lib-provided/*.jar | tr '\n' ':')
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"

# -parameters BAT BUOC: khong co thi @PathVariable loi runtime
# "Name for argument of type [long] not specified" -> HTTP 500.
"$JAVA_HOME/bin/javac" -nowarn -parameters -encoding UTF-8 --release 21 \
  -cp "$CP" -d "$OUT/classes" $(find plugin/src/main/java -name '*.java')

cp -r plugin/src/main/resources/. "$OUT/classes/"
"$JAVA_HOME/bin/jar" --create --file "$JAR" -C "$OUT/classes" .
echo "✓ $JAR"

if [[ "${1:-}" == "--deploy" ]]; then
  cp "$JAR" "$SQUASH_HOME/plugins/"
  echo "✓ deployed -> $SQUASH_HOME/plugins/"
fi

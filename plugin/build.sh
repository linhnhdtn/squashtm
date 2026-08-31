#!/usr/bin/env bash
# Build the dtn-myfeature plugin with javac + jar (no Maven, no network).
# The classpath comes from WEB-INF/lib of the squash-tm.war already unpacked in .runtime.
#   ./plugin/build.sh [--check] [--deploy]      (both flags allowed, in any order)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a; . ./.env; set +a

SQUASH_HOME="$ROOT/.runtime/squash-tm"
LIBS="$ROOT/.cache/squash-libs"
OUT="$ROOT/plugin/target"
JAR="$OUT/dtn-myfeature-1.0.0.jar"

[[ -f "$SQUASH_HOME/bundles/squash-tm.war" ]] || { echo "!! nothing installed yet, run ./bootstrap.sh first"; exit 1; }

if [[ ! -d "$LIBS/WEB-INF/lib" ]]; then
  echo "   extracting libraries from the war (first run only)"
  mkdir -p "$LIBS"
  unzip -q -o "$SQUASH_HOME/bundles/squash-tm.war" 'WEB-INF/lib/*.jar' 'WEB-INF/lib-provided/*.jar' -d "$LIBS"
fi

CP=$(ls "$LIBS"/WEB-INF/lib/*.jar "$LIBS"/WEB-INF/lib-provided/*.jar | tr '\n' ':')
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"

# -parameters is MANDATORY: without it @PathVariable fails at runtime with
# "Name for argument of type [long] not specified" -> HTTP 500.
"$JAVA_HOME/bin/javac" -nowarn -parameters -encoding UTF-8 --release 21 \
  -cp "$CP" -d "$OUT/classes" $(find plugin/src/main/java -name '*.java')

cp -r plugin/src/main/resources/. "$OUT/classes/"

# Self-check of the trend/environmentFailures logic: no DB, no framework needed, and it stays
# out of the jar.
# It has to be javac then java, not the single-file source launcher: the launcher loads the class
# into a classloader of its own -> a different runtime package -> IllegalAccessError on the
# package-private method.
has() { for a in "$@"; do [[ "$a" == "$FLAG" ]] && return 0; done; return 1; }

FLAG=--check
if has "$@"; then
  rm -rf "$OUT/test-classes"; mkdir -p "$OUT/test-classes"
  "$JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 --release 21 \
    -cp "$OUT/classes:$CP" -d "$OUT/test-classes" \
    $(find plugin/src/test/java -name '*.java')
  "$JAVA_HOME/bin/java" -cp "$OUT/classes:$OUT/test-classes:$CP" \
    org.squashtest.tm.plugin.dtnmyfeature.DtnRunHistoryCheck
fi

"$JAVA_HOME/bin/jar" --create --file "$JAR" -C "$OUT/classes" .
echo "✓ $JAR"

FLAG=--deploy
if has "$@"; then
  cp "$JAR" "$SQUASH_HOME/plugins/"
  echo "✓ deployed -> $SQUASH_HOME/plugins/"
fi

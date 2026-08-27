#!/usr/bin/env bash
# Dung Squash TM tu may trang: tai bo cai + DB + schema + build & deploy plugin.
#   ./bootstrap.sh            plugin Java (nhanh, ~1 phut sau khi tai xong)
#   ./bootstrap.sh --with-front   kem fork frontend Angular (+ ~10 phut, ~1.9GB dia)
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"

[[ -f .env ]] || cp .env.example .env
set -a; . ./.env; set +a

RUNTIME="$ROOT/.runtime"
CACHE="$ROOT/.cache"
SQUASH_HOME="$RUNTIME/squash-tm"
TARBALL="$CACHE/squash-tm-$SQUASH_VERSION.tar.gz"
NEXUS="https://nexus.squashtest.org/nexus/repository/public-releases/tm/core/squash-tm-distribution/$SQUASH_VERSION/squash-tm-$SQUASH_VERSION.tar.gz"

need() { command -v "$1" >/dev/null || { echo "!! thieu '$1'"; exit 1; }; }
need docker; need curl; need unzip; need zip
[[ -x "$JAVA_HOME/bin/javac" ]] || { echo "!! khong thay JDK 21 tai JAVA_HOME=$JAVA_HOME (sua trong .env)"; exit 1; }

mkdir -p "$RUNTIME" "$CACHE"

# 1) bo cai Squash TM (342 MB) — dung lai neu da co trong .cache
if [[ ! -f "$TARBALL" ]]; then
  echo "→ tai Squash TM $SQUASH_VERSION"
  curl -# -L --fail -o "$TARBALL.part" "$NEXUS" && mv "$TARBALL.part" "$TARBALL"
fi
if [[ ! -d "$SQUASH_HOME" ]]; then
  echo "→ giai nen vao $SQUASH_HOME"
  tar xzf "$TARBALL" -C "$RUNTIME"
fi

# 2) database
echo "→ database"
(cd ops && docker compose --env-file "$ROOT/.env" up -d)
until docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; do sleep 2; done

# 3) schema (chi nap lan dau — nhan biet qua bang CORE_CONFIG)
if ! docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
     "select 1 from information_schema.tables where table_name='core_config'" | grep -q 1; then
  SQL=$(ls "$SQUASH_HOME"/database-scripts/postgresql-full-install-*.sql | head -1)
  echo "→ nap schema: $(basename "$SQL")"
  docker cp "$SQL" "$DB_CONTAINER":/tmp/install.sql
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -o /dev/null -f /tmp/install.sql
  echo "   $(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tAc \
        "select count(*) from information_schema.tables where table_schema='public'") bang"
else
  echo "→ schema da co, bo qua"
fi

# 4) i18n override (viet hoa nhan UI)
echo "→ conf/lang"
cp conf/lang/custom_translations_*.json "$SQUASH_HOME/conf/lang/"

# 5) plugin Java
echo "→ build + deploy plugin"
./plugin/build.sh --deploy

# 6) frontend fork (tuy chon)
if [[ "${1:-}" == "--with-front" ]]; then
  ./front-patch/apply.sh
  ./front-patch/repack-front.sh
fi
./ops/patch-branding.sh || true

echo
echo "✓ xong. Khoi dong:  ./ops/squashtm.sh start     (hoac: make dev)"

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
until docker exec "$DB_CONTAINER" mariadb-admin ping -h 127.0.0.1 \
        -u"$DB_USER" -p"$DB_PASSWORD" --silent >/dev/null 2>&1; do sleep 2; done

# 3) schema (chi nap lan dau — nhan biet qua bang CORE_CONFIG)
# MYSQL_PWD thay cho -p...: khong lo mat khau tren cmdline VA khong sinh canh bao ra stderr,
# nho vay giu duoc stderr de thay loi that (nuot stderr thi schema hong ma van bao thanh cong).
mdb()     { docker exec -i -e MYSQL_PWD="$DB_PASSWORD"      "$DB_CONTAINER" mariadb -u"$DB_USER" -N -B "$DB_NAME"; }
mdbroot() { docker exec -i -e MYSQL_PWD="$DB_ROOT_PASSWORD" "$DB_CONTAINER" mariadb -uroot     -N -B "$@"; }

# Squash TM 10+ tren MariaDB doi role 'alter_squash_table_seq' PHAI co truoc khi nap schema:
# script install chua ~200 dong `GRANT ALL ON <seq> TO alter_squash_table_seq`. Thieu role thi
# nap schema chet giua chung. App con tu kiem lai luc khoi dong:
#   select 1 from information_schema.applicable_roles
#   where ROLE_NAME='alter_squash_table_seq' and IS_DEFAULT='YES'
mdbroot <<SQL
CREATE ROLE IF NOT EXISTS alter_squash_table_seq;
GRANT alter_squash_table_seq TO '$DB_USER'@'%';
SET DEFAULT ROLE alter_squash_table_seq FOR '$DB_USER'@'%';
SQL

# MariaDB tren Linux phan biet hoa thuong ten bang, Squash tao bang chu HOA -> upper()
if ! echo "select 1 from information_schema.tables
            where table_schema='$DB_NAME' and upper(table_name)='CORE_CONFIG'" | mdb | grep -q 1; then
  SQL=$(ls "$SQUASH_HOME"/database-scripts/mariadb-full-install-*.sql | head -1)
  echo "→ nap schema: $(basename "$SQL")"
  # nap bang root: user Squash khong co GRANT OPTION nen khong chay duoc cac lenh GRANT trong script
  mdbroot "$DB_NAME" < "$SQL" > /dev/null
  echo "   $(echo "select count(*) from information_schema.tables
                   where table_schema='$DB_NAME'" | mdb) bang"
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

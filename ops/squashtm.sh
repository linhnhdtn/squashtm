#!/usr/bin/env bash
# Quan ly Squash TM. Chay detached (setsid) nen khong chet khi dong terminal.
#   ./ops/squashtm.sh start | stop | restart | status | logs
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env
set -a; . ./.env; set +a

SQUASH_HOME="$ROOT/.runtime/squash-tm"
PID_FILE="$ROOT/.runtime/squash-tm.pid"
LOG_FILE="$ROOT/.runtime/squash-tm.out"

JAVA_OPTS=(
  "-Dserver.address=$BIND_ADDRESS"
  "-Dserver.port=$HTTP_PORT"
  "-Dspring.profiles.active=mariadb"
  "-Dspring.datasource.url=jdbc:mariadb://localhost:$DB_PORT/$DB_NAME"
  "-Dspring.datasource.username=$DB_USER"
  "-Dspring.datasource.password=$DB_PASSWORD"
  # chi nhan: interactive | only | forced | disabled  (KHONG co 'auto')
  "-Dsquash.db.update-mode=forced"
  "-Dspring.web.resources.cache.period=${STATIC_CACHE:-0}"
)

lan_ip() { ip -4 -o addr show scope global 2>/dev/null | awk '$2 !~ /^(docker|br-)/ {split($4,a,"/"); print a[1]; exit}'; }
listening() { ss -tln 2>/dev/null | grep -q ":$HTTP_PORT "; }

start() {
  listening && { echo "port $HTTP_PORT dang bi chiem"; status; return 0; }
  [[ -f "$SQUASH_HOME/bundles/squash-tm.war" ]] || { echo "!! chua bootstrap, chay ./bootstrap.sh"; exit 1; }

  if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    echo "→ khoi dong database"
    (cd ops && docker compose --env-file "$ROOT/.env" up -d) >/dev/null
    until docker exec "$DB_CONTAINER" mariadb-admin ping -h 127.0.0.1 \
            -u"$DB_USER" -p"$DB_PASSWORD" --silent >/dev/null 2>&1; do sleep 2; done
  fi

  echo "→ khoi dong Squash TM ($BIND_ADDRESS:$HTTP_PORT)"
  cd "$SQUASH_HOME/bin" || exit 1
  PATH="$JAVA_HOME/bin:$PATH" SQUASH_JAVA_ARGS="${JAVA_OPTS[*]}" \
    setsid nohup ./startup.sh > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"

  printf "→ cho app san sang"
  for _ in $(seq 1 40); do
    grep -q 'Started SquashTm' "$LOG_FILE" 2>/dev/null && { echo " ok"; status; return 0; }
    grep -q 'Application run failed' "$LOG_FILE" 2>/dev/null && { echo " LOI"; tail -20 "$LOG_FILE"; return 1; }
    printf "."; sleep 3
  done
  echo " timeout — xem $LOG_FILE"; return 1
}

stop() {
  # startup.sh cd vao bin/ nen cmdline chua duong dan TUONG DOI (../bundles/squash-tm.war):
  # pkill theo duong dan tuyet doi khong bao gio khop -> stop/restart im lang khong lam gi.
  pkill -f "Dserver\\.port=$HTTP_PORT .*squash-tm\\.war" 2>/dev/null
  rm -f "$PID_FILE"; sleep 3; echo "→ da dung"
}

status() {
  if listening; then
    local ip; ip=$(lan_ip)
    echo "● Squash TM DANG CHAY  (admin/admin)"
    echo "   local : http://localhost:$HTTP_PORT/squash/"
    [[ -n "$ip" && "$BIND_ADDRESS" != "127.0.0.1" ]] && echo "   LAN   : http://$ip:$HTTP_PORT/squash/"
    echo "   log   : $LOG_FILE"
  else
    echo "○ Squash TM khong chay"
  fi
}

case "${1:-status}" in
  start) start ;; stop) stop ;; restart) stop; start ;;
  status) status ;; logs) tail -f "$LOG_FILE" ;;
  *) echo "Dung: $0 {start|stop|restart|status|logs}"; exit 1 ;;
esac

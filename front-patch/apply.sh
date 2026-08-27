#!/usr/bin/env bash
# Fork frontend Angular: clone tag dung version, apply patch, build.
# Chi can khi muon trang nam TRONG SPA (/squash/dtn-dashboard).
# Ton ~10 phut va ~1.9 GB dia (node_modules 1.1 GB).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"; set -a; . ./.env; set +a

TAG="v${SQUASH_VERSION%.RELEASE}"                  # 13.0.7.RELEASE -> v13.0.7
SRC="$ROOT/.runtime/tm-front-src"
REPO_URL="https://gitlab.com/henixdevelopment/open-source/squash/squashtest-tm-staging.git"

command -v yarn >/dev/null || { echo "!! thieu yarn (yarn 1.x). node 22 + 'npm i -g yarn'"; exit 1; }

if [[ ! -d "$SRC/.git" ]]; then
  echo "→ clone $TAG (sparse, chi tm/tm-front)"
  git clone --filter=blob:none --sparse --depth 1 --branch "$TAG" "$REPO_URL" "$SRC"
  (cd "$SRC" && git sparse-checkout set tm/tm-front)
fi

cd "$SRC"
if git diff --quiet; then
  echo "→ apply patch (4 file, chi them dong)"
  git apply --whitespace=nowarn "$ROOT/front-patch/tm-front-v13.0.7.patch"
else
  echo "→ patch da apply truoc do, bo qua"
fi

PAGES="$SRC/tm/tm-front/projects/sqtm-app/src/app/pages"
echo "→ copy page dtn-dashboard"
rm -rf "$PAGES/dtn-dashboard"
cp -r "$ROOT/front-patch/dtn-dashboard" "$PAGES/"

cd "$SRC/tm/tm-front"
[[ -d node_modules ]] || { echo "→ yarn install (~4 phut, 1.1 GB)"; yarn install --frozen-lockfile --network-timeout 600000; }
echo "→ yarn build (~5 phut)"
yarn build
echo "✓ build xong: $SRC/tm/tm-front/dist/sqtm-app"

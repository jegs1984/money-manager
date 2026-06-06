#!/usr/bin/env bash
# =============================================================================
# Money Manager — Start
# =============================================================================

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_PYTHON="$PROJECT_ROOT/venv/bin/python"
ENV_FILE="$PROJECT_ROOT/.env"
APP_PORT=8765

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; NC='\033[0m'

# ── Pre-flight checks ─────────────────────────────────────────────────────────
if [[ ! -x "$VENV_PYTHON" ]]; then
    echo -e "${RED}✖ Virtualenv not found.${NC}"
    echo "  Run the installer first:  bash installer/setup.sh"
    exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
    echo -e "${RED}✖ .env file not found.${NC}"
    echo "  Run the installer first:  bash installer/setup.sh"
    exit 1
fi

# ── Detect and start PostgreSQL ───────────────────────────────────────────────
PG_VERSION=""
for v in 16 15 14; do
    if brew list "postgresql@$v" &>/dev/null 2>&1; then
        PG_VERSION="$v"; break
    fi
done

if [[ -z "$PG_VERSION" ]]; then
    echo -e "${RED}✖ PostgreSQL not found. Run installer/setup.sh first.${NC}"
    exit 1
fi

PG_PREFIX="$(brew --prefix "postgresql@$PG_VERSION")"
PG_DATA="$PG_PREFIX/var/postgresql@$PG_VERSION"
export PATH="$PG_PREFIX/bin:$PATH"

if ! pg_ctl status -D "$PG_DATA" &>/dev/null; then
    echo -e "${CYAN}▶ Starting PostgreSQL $PG_VERSION...${NC}"
    pg_ctl -D "$PG_DATA" -l "$PG_DATA/server.log" start
    sleep 2
fi

# ── Add LibreOffice to PATH (CC statement parsing) ────────────────────────────
if [[ -x "/Applications/LibreOffice.app/Contents/MacOS/soffice" ]]; then
    export PATH="/Applications/LibreOffice.app/Contents/MacOS:$PATH"
fi

# ── Kill any stale process on the port ───────────────────────────────────────
lsof -ti tcp:$APP_PORT | xargs kill -9 2>/dev/null || true

# ── Launch ────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}  ₿ Money Manager${NC}"
echo -e "  ${CYAN}http://127.0.0.1:$APP_PORT${NC}"
echo ""
echo -e "  Press ${YELLOW}Ctrl-C${NC} to stop."
echo ""

# Open browser after server is up
(
    for i in $(seq 1 20); do
        if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:$APP_PORT/" 2>/dev/null | grep -qE '^[23]'; then
            open "http://127.0.0.1:$APP_PORT/"
            break
        fi
        sleep 0.5
    done
) &

cd "$PROJECT_ROOT"
exec "$VENV_PYTHON" manage.py runserver "127.0.0.1:$APP_PORT"

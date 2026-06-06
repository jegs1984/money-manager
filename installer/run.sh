#!/usr/bin/env bash
# =============================================================================
# Money Manager — Manual launch (no .app needed)
# =============================================================================
# Run from anywhere; finds the project root automatically.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VENV_PYTHON="$PROJECT_ROOT/venv/bin/python"
APP_PORT=8765

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
die() { echo -e "${RED}✖ $*${NC}"; exit 1; }
info() { echo -e "${CYAN}▶ $*${NC}"; }

[[ -x "$VENV_PYTHON" ]] || die "Virtualenv not found. Run installer/setup.sh first."
[[ -f "$PROJECT_ROOT/.env" ]] || die ".env not found. Run installer/setup.sh first."

# ── Detect PostgreSQL version ─────────────────────────────────────────────────
PG_VERSION=""
for v in 16 15 14; do
    if brew list "postgresql@$v" &>/dev/null 2>&1; then
        PG_VERSION="$v"; break
    fi
done
[[ -n "$PG_VERSION" ]] || die "PostgreSQL not found. Run installer/setup.sh first."

PG_BIN="$(brew --prefix postgresql@$PG_VERSION)/bin"
export PATH="$PG_BIN:$PATH"

# ── Start PostgreSQL if needed ────────────────────────────────────────────────
PG_DATA="$(brew --prefix postgresql@$PG_VERSION)/var/postgresql@$PG_VERSION"
if ! pg_ctl status -D "$PG_DATA" &>/dev/null; then
    info "Starting PostgreSQL..."
    pg_ctl -D "$PG_DATA" -l "$PG_DATA/server.log" start
    sleep 2
fi

# ── Add LibreOffice to PATH ───────────────────────────────────────────────────
if [[ -x "/Applications/LibreOffice.app/Contents/MacOS/soffice" ]]; then
    export PATH="/Applications/LibreOffice.app/Contents/MacOS:$PATH"
fi

# ── Kill any stale server on the port ────────────────────────────────────────
lsof -ti tcp:$APP_PORT | xargs kill -9 2>/dev/null || true

# ── Launch ────────────────────────────────────────────────────────────────────
cd "$PROJECT_ROOT"
echo -e "${GREEN}Starting Money Manager on http://127.0.0.1:$APP_PORT${NC}"
echo "Press Ctrl-C to stop."
echo ""

# Open browser after a short delay in background
(sleep 2 && open "http://127.0.0.1:$APP_PORT/") &

exec "$VENV_PYTHON" manage.py runserver "127.0.0.1:$APP_PORT"

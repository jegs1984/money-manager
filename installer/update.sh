#!/usr/bin/env bash
# =============================================================================
# Money Manager — Update script
# =============================================================================
# Run after pulling new code to sync deps and schema without full reinstall.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VENV_PYTHON="$PROJECT_ROOT/venv/bin/python"
PIP="$PROJECT_ROOT/venv/bin/pip"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}▶ $*${NC}"; }
success() { echo -e "${GREEN}✔ $*${NC}"; }
die()     { echo -e "${RED}✖ $*${NC}"; exit 1; }

[[ -x "$VENV_PYTHON" ]] || die "Virtualenv not found. Run installer/setup.sh first."

info "Updating Python dependencies..."
"$PIP" install --upgrade --quiet \
    "Django>=4.2,<5.0" \
    "psycopg2-binary>=2.9" \
    "python-dotenv>=1.0"
"$PIP" install --quiet "xlrd==1.2.0" 2>/dev/null || true
success "Dependencies updated"

# Read DB creds from .env
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    DB_USER=$(grep '^DB_USER=' "$PROJECT_ROOT/.env" | cut -d'=' -f2-)
    DB_NAME=$(grep '^DB_NAME=' "$PROJECT_ROOT/.env" | cut -d'=' -f2-)

    info "Applying any new SQL migrations..."
    for f in "$PROJECT_ROOT/sql"/migrate_*.sql; do
        [[ -f "$f" ]] || continue
        psql -U "$DB_USER" -d "$DB_NAME" -f "$f" 2>/dev/null && \
            success "Applied: $(basename "$f")" || \
            echo "  (already applied or skipped: $(basename "$f"))"
    done
fi

info "Collecting static files..."
cd "$PROJECT_ROOT"
"$VENV_PYTHON" manage.py collectstatic --noinput --clear 2>/dev/null || true
success "Static files updated"

echo ""
success "Update complete. Run installer/run.sh to start."

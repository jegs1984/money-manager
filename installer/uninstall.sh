#!/usr/bin/env bash
# =============================================================================
# Money Manager — Uninstaller
# =============================================================================
# Removes the virtualenv, .env, and Desktop .app.
# Does NOT drop the database or uninstall Homebrew packages.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_NAME="Money Manager"
DESKTOP="$HOME/Desktop"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}▶ $*${NC}"; }
success() { echo -e "${GREEN}✔ $*${NC}"; }
warn()    { echo -e "${YELLOW}⚠ $*${NC}"; }

echo ""
echo -e "${RED}╔══════════════════════════════════════╗${NC}"
echo -e "${RED}║    Money Manager — Uninstaller       ║${NC}"
echo -e "${RED}╚══════════════════════════════════════╝${NC}"
echo ""
warn "This will remove:"
echo "  • $PROJECT_ROOT/venv/"
echo "  • $PROJECT_ROOT/.env"
echo "  • $DESKTOP/$APP_NAME.app"
echo ""
echo "Your database and Homebrew packages will NOT be touched."
echo ""
read -rp "Continue? (y/N) " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 0; }

# Stop server if running
PORT=8765
info "Stopping server on port $PORT (if any)..."
lsof -ti tcp:$PORT | xargs kill -9 2>/dev/null || true
success "Server stopped"

# Remove venv
if [[ -d "$PROJECT_ROOT/venv" ]]; then
    info "Removing virtualenv..."
    rm -rf "$PROJECT_ROOT/venv"
    success "venv removed"
else
    warn "No venv found"
fi

# Remove .env
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    info "Removing .env..."
    rm -f "$PROJECT_ROOT/.env"
    success ".env removed"
else
    warn "No .env found"
fi

# Remove Desktop app
if [[ -d "$DESKTOP/$APP_NAME.app" ]]; then
    info "Removing Desktop app..."
    rm -rf "$DESKTOP/$APP_NAME.app"
    success "Desktop app removed"
else
    warn "No Desktop app found"
fi

echo ""
echo -e "${GREEN}✔ Uninstall complete.${NC}"
echo "  To fully remove the database:"
echo "    psql postgres -c \"DROP DATABASE money_manager;\""
echo "    psql postgres -c \"DROP USER money_manager_user;\""
echo ""

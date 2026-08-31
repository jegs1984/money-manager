#!/usr/bin/env bash
# Money Manager Linux and Termux installer.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VENV_DIR="$PROJECT_ROOT/venv"
ENV_FILE="$PROJECT_ROOT/.env"
PYTHON="${PYTHON:-python3}"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
info() { echo -e "${CYAN}> $*${NC}"; }
success() { echo -e "${GREEN}[ok] $*${NC}"; }
die() { echo -e "${RED}[error] $*${NC}" >&2; exit 1; }

command -v "$PYTHON" >/dev/null 2>&1 || {
    if command -v pkg >/dev/null 2>&1; then
        info "Installing Python with Termux pkg..."
        pkg update -y
        pkg install -y python openssl
        PYTHON=python
    else
        die "Python 3 is required. Install it with your Linux package manager and rerun this script."
    fi
}

info "Using $($PYTHON --version)"
if [[ ! -d "$VENV_DIR" ]]; then
    info "Creating virtual environment..."
    "$PYTHON" -m venv "$VENV_DIR" || die "Could not create a virtual environment. Install python-venv and rerun."
fi

PIP="$VENV_DIR/bin/pip"
VENV_PYTHON="$VENV_DIR/bin/python"
info "Installing Python dependencies..."
"$PIP" install --upgrade pip
# psycopg2-binary is intentionally omitted: Linux/Termux defaults to SQLite.
"$PIP" install \
    "Django>=4.2,<5.0" \
    "python-dotenv>=1.0" \
    "xlrd>=1.2.0" \
    "reportlab>=4.0" \
    "cryptography>=42.0"
success "Python dependencies installed"

if [[ ! -f "$ENV_FILE" ]]; then
    info "Creating SQLite configuration..."
    SECRET_KEY="$($VENV_PYTHON -c 'import secrets; print(secrets.token_urlsafe(48))')"
    cat > "$ENV_FILE" <<EOF
DJANGO_SECRET_KEY=$SECRET_KEY
DJANGO_DEBUG=True
DJANGO_ALLOWED_HOSTS=127.0.0.1 localhost
DB_ENGINE=sqlite3
DB_NAME=db.sqlite3
EOF
    chmod 600 "$ENV_FILE"
else
    info "Keeping existing .env"
fi

cd "$PROJECT_ROOT"
info "Applying Django migrations..."
"$VENV_PYTHON" manage.py migrate --noinput
info "Collecting static files..."
"$VENV_PYTHON" manage.py collectstatic --noinput
success "Money Manager is installed"
echo "Run: bash installer/run-linux.sh"
echo "URL: http://127.0.0.1:8765"
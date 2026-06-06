#!/usr/bin/env bash
# =============================================================================
# Money Manager — Mac Setup Script
# =============================================================================
# Run once from the project root or from the installer/ directory.
# Re-running is safe (all steps are idempotent).
# =============================================================================

set -euo pipefail

# ── Resolve project root (works whether called from root or installer/) ───────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_PORT=8765
DB_NAME="money_manager"
DB_USER="money_manager_user"
DB_PASSWORD="mm_$(openssl rand -hex 8)"
VENV_DIR="$PROJECT_ROOT/venv"
ENV_FILE="$PROJECT_ROOT/.env"
APP_NAME="Money Manager"
DESKTOP="$HOME/Desktop"

# Colours
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}▶ $*${NC}"; }
success() { echo -e "${GREEN}✔ $*${NC}"; }
warn()    { echo -e "${YELLOW}⚠ $*${NC}"; }
die()     { echo -e "${RED}✖ $*${NC}"; exit 1; }

echo ""
echo -e "${CYAN}╔══════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     Money Manager — Mac Installer    ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════╝${NC}"
echo ""

# ── 1. Homebrew ───────────────────────────────────────────────────────────────
info "Checking Homebrew..."
if ! command -v brew &>/dev/null; then
    warn "Homebrew not found — installing..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    # Add brew to PATH for Apple Silicon
    if [[ -f /opt/homebrew/bin/brew ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
fi
success "Homebrew ready: $(brew --version | head -1)"

# ── 2. Python 3 ───────────────────────────────────────────────────────────────
info "Checking Python 3..."
if ! brew list python3 &>/dev/null && ! brew list python &>/dev/null; then
    warn "Installing Python via Homebrew..."
    brew install python
fi
PYTHON="$(brew --prefix)/bin/python3"
[[ ! -x "$PYTHON" ]] && PYTHON="$(which python3)"
[[ ! -x "$PYTHON" ]] && die "Cannot find python3"
success "Python: $($PYTHON --version)"

# ── 3. PostgreSQL ─────────────────────────────────────────────────────────────
info "Checking PostgreSQL..."
PG_VERSION=""
for v in 16 15 14; do
    if brew list "postgresql@$v" &>/dev/null; then
        PG_VERSION="$v"; break
    fi
done
if [[ -z "$PG_VERSION" ]]; then
    warn "Installing PostgreSQL 16 via Homebrew..."
    brew install postgresql@16
    PG_VERSION=16
fi
PG_PREFIX="$(brew --prefix postgresql@$PG_VERSION)"
export PATH="$PG_PREFIX/bin:$PATH"
success "PostgreSQL $PG_VERSION ready"

# Start PostgreSQL service if not running
if ! pg_ctl status -D "$PG_PREFIX/var/postgresql@$PG_VERSION" &>/dev/null 2>&1; then
    info "Starting PostgreSQL service..."
    brew services start "postgresql@$PG_VERSION" || true
    sleep 2
fi

# ── 4. LibreOffice ────────────────────────────────────────────────────────────
info "Checking LibreOffice (needed for CC statement parsing)..."
if ! command -v soffice &>/dev/null && [[ ! -d "/Applications/LibreOffice.app" ]]; then
    warn "Installing LibreOffice via Homebrew Cask..."
    brew install --cask libreoffice
fi
success "LibreOffice present"

# ── 5. Python virtualenv ──────────────────────────────────────────────────────
info "Setting up Python virtualenv at $VENV_DIR..."
if [[ ! -d "$VENV_DIR" ]]; then
    "$PYTHON" -m venv "$VENV_DIR"
fi
PIP="$VENV_DIR/bin/pip"
VENV_PYTHON="$VENV_DIR/bin/python"

info "Installing Python dependencies..."
"$PIP" install --upgrade pip --quiet
"$PIP" install --quiet \
    "Django>=4.2,<5.0" \
    "psycopg2-binary>=2.9" \
    "python-dotenv>=1.0"
# xlrd 1.2.0 may not be on PyPI anymore; skip silently — we use LibreOffice fallback
"$PIP" install --quiet "xlrd==1.2.0" 2>/dev/null || \
    warn "xlrd not available (LibreOffice fallback will be used for CC files)"
success "Python dependencies installed"

# ── 6. Database & user ────────────────────────────────────────────────────────
info "Setting up PostgreSQL database..."

# Determine if DB user already exists
USER_EXISTS=$(psql postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" 2>/dev/null || echo "")

if [[ -z "$USER_EXISTS" ]]; then
    info "Creating DB user '$DB_USER'..."
    psql postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';" 2>/dev/null || true
else
    warn "DB user '$DB_USER' already exists — keeping existing password from .env"
    # Read existing password from .env if present
    if [[ -f "$ENV_FILE" ]]; then
        DB_PASSWORD=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d'=' -f2- | tr -d '"' || echo "$DB_PASSWORD")
    fi
fi

DB_EXISTS=$(psql postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" 2>/dev/null || echo "")
if [[ -z "$DB_EXISTS" ]]; then
    info "Creating database '$DB_NAME'..."
    psql postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" 2>/dev/null || true
fi

# Grant privileges
psql postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;" 2>/dev/null || true
success "Database ready"

# ── 7. Schema + seed ──────────────────────────────────────────────────────────
info "Running schema migrations..."
PSQL_CMD="psql -U $DB_USER -d $DB_NAME"

# Apply init_db.sql (idempotent — uses IF NOT EXISTS)
$PSQL_CMD -f "$PROJECT_ROOT/sql/init_db.sql" 2>/dev/null || \
    warn "init_db.sql had warnings (may already be applied)"

# Apply CC staging migration (idempotent)
if [[ -f "$PROJECT_ROOT/sql/migrate_add_cc_staging.sql" ]]; then
    $PSQL_CMD -f "$PROJECT_ROOT/sql/migrate_add_cc_staging.sql" 2>/dev/null || true
fi

# Seed categories only if table is empty
CATEGORY_COUNT=$($PSQL_CMD -tAc "SELECT COUNT(*) FROM finance_category;" 2>/dev/null || echo "0")
if [[ "$CATEGORY_COUNT" -eq 0 ]]; then
    info "Seeding categories..."
    $PSQL_CMD -f "$PROJECT_ROOT/sql/categories.sql" 2>/dev/null || \
        warn "categories.sql had warnings"
else
    warn "Categories already seeded ($CATEGORY_COUNT rows) — skipping"
fi

# Django sessions table
info "Applying Django session table..."
cd "$PROJECT_ROOT"
"$VENV_PYTHON" manage.py migrate sessions --run-syncdb 2>/dev/null || \
    "$VENV_PYTHON" manage.py migrate 2>/dev/null || true
success "Schema ready"

# ── 8. .env file ──────────────────────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
    info "Generating .env file..."
    SECRET_KEY="$($VENV_PYTHON -c "import secrets, string; print(''.join(secrets.choice(string.ascii_letters+string.digits+string.punctuation) for _ in range(50)))"  | tr -d "'\"\`\\\\")"
    cat > "$ENV_FILE" <<EOF
DJANGO_SECRET_KEY=${SECRET_KEY}
DJANGO_DEBUG=True
DJANGO_ALLOWED_HOSTS=127.0.0.1 localhost

DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
DB_HOST=127.0.0.1
DB_PORT=5432
EOF
    success ".env written"
else
    warn ".env already exists — not overwriting"
fi

# ── 9. Collect static ─────────────────────────────────────────────────────────
info "Collecting static files..."
cd "$PROJECT_ROOT"
"$VENV_PYTHON" manage.py collectstatic --noinput --clear 2>/dev/null || true
success "Static files ready"

# ── 10. Desktop .app bundle ───────────────────────────────────────────────────
info "Creating '$APP_NAME.app' on Desktop..."

APP_BUNDLE="$DESKTOP/$APP_NAME.app"
APP_MACOS="$APP_BUNDLE/Contents/MacOS"
APP_RESOURCES="$APP_BUNDLE/Contents/Resources"

mkdir -p "$APP_MACOS" "$APP_RESOURCES"

# Info.plist
cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>             <string>Money Manager</string>
  <key>CFBundleDisplayName</key>      <string>Money Manager</string>
  <key>CFBundleIdentifier</key>       <string>com.local.money-manager</string>
  <key>CFBundleVersion</key>          <string>1.0</string>
  <key>CFBundleExecutable</key>       <string>launch</string>
  <key>CFBundleIconFile</key>         <string>AppIcon</string>
  <key>CFBundlePackageType</key>      <string>APPL</string>
  <key>LSMinimumSystemVersion</key>   <string>12.0</string>
  <key>LSUIElement</key>              <false/>
</dict>
</plist>
PLIST

# Determine pg prefix for runtime PATH
PG_BIN="$(brew --prefix postgresql@$PG_VERSION)/bin"

# LibreOffice soffice binary location
LO_BIN=""
if [[ -x "/Applications/LibreOffice.app/Contents/MacOS/soffice" ]]; then
    LO_BIN="/Applications/LibreOffice.app/Contents/MacOS"
elif command -v soffice &>/dev/null; then
    LO_BIN="$(dirname "$(command -v soffice)")"
fi

# Main launcher script embedded into the .app
cat > "$APP_MACOS/launch" <<LAUNCHER
#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${PROJECT_ROOT}"
VENV_PYTHON="${VENV_PYTHON}"
PG_BIN="${PG_BIN}"
PG_VERSION="${PG_VERSION}"
LO_BIN="${LO_BIN}"
APP_PORT=${APP_PORT}
DB_NAME="${DB_NAME}"

export PATH="\$PG_BIN:\$LO_BIN:\$PATH"

# ── Start PostgreSQL if not running ──────────────────────────────────────────
PG_DATA="\$(brew --prefix postgresql@\$PG_VERSION)/var/postgresql@\$PG_VERSION"
if ! pg_ctl status -D "\$PG_DATA" &>/dev/null; then
    pg_ctl -D "\$PG_DATA" -l "\$PG_DATA/server.log" start
    sleep 2
fi

# ── Kill any existing server on this port ────────────────────────────────────
lsof -ti tcp:\$APP_PORT | xargs kill -9 2>/dev/null || true

# ── Launch Django in a visible Terminal window ────────────────────────────────
osascript <<OSA
tell application "Terminal"
    activate
    set w to do script "cd '\$PROJECT_ROOT' && source '\${VENV_DIR:-\$PROJECT_ROOT/venv}/bin/activate' && python manage.py runserver 127.0.0.1:\$APP_PORT"
    set custom title of w to "Money Manager Server"
end tell
OSA

# ── Wait for server then open browser ────────────────────────────────────────
for i in \$(seq 1 20); do
    if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:\$APP_PORT/" | grep -qE '^[23]'; then
        break
    fi
    sleep 0.5
done
open "http://127.0.0.1:\$APP_PORT/"
LAUNCHER

chmod +x "$APP_MACOS/launch"

# Generate a simple green circle icon via Python (no external tools needed)
"$VENV_PYTHON" - <<'PYICON'
import struct, zlib, os

def make_png(size=512):
    """Generate a minimal green circle PNG."""
    import math
    w = h = size
    cx = cy = size // 2
    r = size // 2 - 4
    rows = []
    for y in range(h):
        row = [0]  # filter byte
        for x in range(w):
            dist = math.sqrt((x - cx)**2 + (y - cy)**2)
            if dist <= r:
                row += [34, 197, 94, 255]   # emerald-500
            else:
                row += [24, 24, 27, 0]      # transparent
        rows.append(bytes(row))

    def chunk(name, data):
        c = zlib.crc32(name + data) & 0xffffffff
        return struct.pack('>I', len(data)) + name + data + struct.pack('>I', c)

    raw = b''.join(rows)
    compressed = zlib.compress(raw, 9)

    sig = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    ihdr = chunk(b'IHDR', ihdr_data)
    idat = chunk(b'IDAT', compressed)
    iend = chunk(b'IEND', b'')
    return sig + ihdr + idat + iend

desktop = os.path.expanduser('~/Desktop')
app = os.path.join(desktop, 'Money Manager.app', 'Contents', 'Resources')
os.makedirs(app, exist_ok=True)
with open(os.path.join(app, 'AppIcon.png'), 'wb') as f:
    f.write(make_png(512))
PYICON

success "Desktop app created: $APP_BUNDLE"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║           Installation complete! 🎉          ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  ${CYAN}Double-click${NC} 'Money Manager' on your Desktop to launch."
echo -e "  Or run manually:"
echo -e "    cd $PROJECT_ROOT"
echo -e "    source venv/bin/activate"
echo -e "    python manage.py runserver 127.0.0.1:$APP_PORT"
echo ""

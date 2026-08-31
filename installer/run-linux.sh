#!/usr/bin/env bash
# Start Money Manager on Linux or Termux.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VENV_PYTHON="$PROJECT_ROOT/venv/bin/python"
APP_PORT="${APP_PORT:-8765}"

[[ -x "$VENV_PYTHON" ]] || { echo "Run bash installer/setup-linux.sh first." >&2; exit 1; }
[[ -f "$PROJECT_ROOT/.env" ]] || { echo "Run bash installer/setup-linux.sh first." >&2; exit 1; }

if command -v termux-open-url >/dev/null 2>&1; then
    OPEN_URL=termux-open-url
elif command -v xdg-open >/dev/null 2>&1; then
    OPEN_URL=xdg-open
else
    OPEN_URL=
fi

cd "$PROJECT_ROOT"
URL="http://127.0.0.1:$APP_PORT"
echo "Money Manager: $URL"
echo "Press Ctrl-C to stop."

if [[ -n "$OPEN_URL" ]]; then
    (sleep 2; "$OPEN_URL" "$URL" >/dev/null 2>&1 || true) &
fi

exec "$VENV_PYTHON" manage.py runserver "127.0.0.1:$APP_PORT"
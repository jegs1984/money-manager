#!/usr/bin/env bash
# =============================================================================
# Money Manager — Docker container entrypoint
# =============================================================================
# Runs before the CMD in docker-compose.yml.
# Waits for the DB, runs Django migrations, then hands off to CMD.
# =============================================================================

set -euo pipefail

echo ""
echo "╔══════════════════════════════════════╗"
echo "║     Money Manager — Docker           ║"
echo "╚══════════════════════════════════════╝"
echo ""

# ── Wait for Postgres ─────────────────────────────────────────────────────────
echo "▶ Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT}..."
until python -c "
import psycopg2, os, sys
try:
    psycopg2.connect(
        dbname=os.environ['DB_NAME'],
        user=os.environ['DB_USER'],
        password=os.environ['DB_PASSWORD'],
        host=os.environ['DB_HOST'],
        port=os.environ['DB_PORT'],
    )
    sys.exit(0)
except Exception:
    sys.exit(1)
" 2>/dev/null; do
    echo "  … DB not ready, retrying in 1s"
    sleep 1
done
echo "✔ PostgreSQL is ready"

# ── Django migrations ──────────────────────────────────────────────────────────
echo "▶ Running Django migrations..."
python manage.py migrate --noinput
echo "✔ Migrations complete"

# ── Collect static (in case volume mount shadowed the build-time run) ─────────
echo "▶ Collecting static files..."
python manage.py collectstatic --noinput --clear 2>/dev/null || true
echo "✔ Static files ready"

echo ""
echo "✔ App starting on http://localhost:${APP_PORT:-8765}"
echo ""

# ── Hand off to CMD ────────────────────────────────────────────────────────────
exec "$@"

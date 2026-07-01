# =============================================================================
# Money Manager — Docker Installer
# =============================================================================

Runs the full Money Manager stack (Django + PostgreSQL) inside Docker.
No local Python, PostgreSQL, or Homebrew installation required.

---

## Prerequisites

| Tool | Install |
|---|---|
| Docker Desktop | https://www.docker.com/products/docker-desktop/ |

That's it.

---

## Quick Start

```bash
# 1. Clone the repo
git clone https://github.com/jegs1984/money-manager.git
cd money-manager

# 2. Copy the Docker env template (edit DB_PASSWORD and SECRET_KEY if desired)
cp .env.docker .env

# 3. Start everything
docker compose up
```

Open **http://localhost:8765** in your browser.

On the **first run**, Docker will:
1. Pull `postgres:16-alpine` and the Python 3.12 base image
2. Build the Django application image
3. Create the database, run `init_db.sql`, `migrate_add_cc_staging.sql`, and seed categories
4. Run Django migrations
5. Start the dev server

Subsequent starts are fast — the database volume persists between restarts.

---

## Common commands

| Task | Command |
|---|---|
| Start in background | `docker compose up -d` |
| View logs | `docker compose logs -f web` |
| Stop | `docker compose down` |
| Rebuild after code change | `docker compose up --build` |
| Open Django shell | `docker compose exec web python manage.py shell` |
| **Wipe DB and start fresh** | `docker compose down -v` then `docker compose up` |

---

## Port mapping

| Service | Container port | Host port |
|---|---|---|
| Django dev server | 8000 | **8765** |
| PostgreSQL | 5432 | **5433** (avoids clash with local Postgres) |

The host port can be changed via the `APP_PORT` variable in `.env`.

---

## Data persistence

PostgreSQL data is stored in a named Docker volume:

```
money_manager_postgres_data
```

This volume survives `docker compose down`. Use `docker compose down -v` to delete it
(this **permanently deletes all your financial data**).

---

## Environment variables (`.env`)

| Variable | Default | Description |
|---|---|---|
| `DJANGO_SECRET_KEY` | `docker-insecure-…` | Django secret key — change in production |
| `DJANGO_DEBUG` | `True` | Set `False` in production |
| `DJANGO_ALLOWED_HOSTS` | `127.0.0.1 localhost` | Space-separated allowed hosts |
| `DB_PASSWORD` | `mm_docker_secret` | Postgres password |
| `APP_PORT` | `8765` | Host port for the web UI |

---

## vs. Mac native installer

| | Docker | Mac native (`installer/setup.sh`) |
|---|---|---|
| Requires Homebrew | ✗ | ✓ |
| Requires Python locally | ✗ | ✓ |
| Requires Postgres locally | ✗ | ✓ |
| Works on Linux / Windows | ✓ | ✗ |
| Double-click `.app` | ✗ | ✓ |
| LibreOffice (CC parsing) | ✗ (planned) | ✓ |

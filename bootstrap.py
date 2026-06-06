#!/usr/bin/env python3
"""
bootstrap.py — Money Manager project setup script
==================================================
Run this script once after cloning / copying the project folder.
It will:
  1. Create a Python virtual environment  (./venv)
  2. Install dependencies from requirements.txt
  3. Copy .env.example → .env  (if .env does not exist)
  4. Create the PostgreSQL database and apply the SQL schema (sql/init_db.sql)
  5. Run Django fake-initial migrations so the ORM is in sync
  6. Print startup instructions

Usage
-----
  python bootstrap.py [--db-name NAME] [--db-user USER] [--db-password PASS]
                      [--db-host HOST] [--db-port PORT]

All DB arguments default to values from .env (or .env.example fallbacks).
"""

import argparse
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

# ── Paths ──────────────────────────────────────────────────────────────────
BASE_DIR = Path(__file__).resolve().parent
VENV_DIR = BASE_DIR / 'venv'
ENV_FILE = BASE_DIR / '.env'
ENV_EXAMPLE = BASE_DIR / '.env.example'
REQ_FILE = BASE_DIR / 'requirements.txt'
SQL_SCHEMA = BASE_DIR / 'sql' / 'init_db.sql'
MANAGE_PY = BASE_DIR / 'manage.py'

IS_WIN = platform.system() == 'Windows'
PYTHON = sys.executable


# ── Helpers ────────────────────────────────────────────────────────────────

def info(msg: str) -> None:
    print(f'\033[92m[✓]\033[0m {msg}')


def step(msg: str) -> None:
    print(f'\033[94m[→]\033[0m {msg}')


def warn(msg: str) -> None:
    print(f'\033[93m[!]\033[0m {msg}')


def die(msg: str) -> None:
    print(f'\033[91m[✗]\033[0m {msg}', file=sys.stderr)
    sys.exit(1)


def run(cmd: list, **kwargs) -> subprocess.CompletedProcess:
    """Run a command, streaming output, raise on non-zero exit."""
    result = subprocess.run(cmd, **kwargs)
    if result.returncode != 0:
        die(f'Command failed: {" ".join(str(c) for c in cmd)}')
    return result


def venv_bin(name: str) -> Path:
    """Return path to a binary inside the virtualenv."""
    if IS_WIN:
        return VENV_DIR / 'Scripts' / name
    return VENV_DIR / 'bin' / name


def load_env() -> dict:
    """Parse .env or .env.example into a dict (no external lib required)."""
    src = ENV_FILE if ENV_FILE.exists() else ENV_EXAMPLE
    env = {}
    for line in src.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, _, val = line.partition('=')
        env[key.strip()] = val.strip()
    return env


# ── Steps ──────────────────────────────────────────────────────────────────

def create_venv() -> None:
    if VENV_DIR.exists():
        info('Virtual environment already exists — skipping.')
        return
    step('Creating virtual environment…')
    run([PYTHON, '-m', 'venv', str(VENV_DIR)])
    info('Virtual environment created.')


def install_deps() -> None:
    step('Installing Python dependencies…')
    pip = venv_bin('pip')
    run([str(pip), 'install', '--upgrade', 'pip', '--quiet'])
    run([str(pip), 'install', '-r', str(REQ_FILE), '--quiet'])
    info('Dependencies installed.')


def create_env_file() -> None:
    if ENV_FILE.exists():
        info('.env already exists — skipping.')
        return
    step('Copying .env.example → .env…')
    shutil.copy(ENV_EXAMPLE, ENV_FILE)
    warn('.env created from example. Edit it with your real DB credentials before starting the server.')


def create_database(cfg: dict) -> None:
    step(f"Ensuring PostgreSQL database '{cfg['DB_NAME']}' exists…")

    # psql must be on PATH
    psql = shutil.which('psql')
    if not psql:
        warn('psql not found on PATH — skipping DB creation. Create the database manually.')
        return

    create_cmd = (
        f"SELECT 1 FROM pg_database WHERE datname='{cfg['DB_NAME']}'"
        f" \\gset\n"
        f"DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_database WHERE datname = '{cfg['DB_NAME']}') "
        f"THEN CREATE DATABASE \"{cfg['DB_NAME']}\"; END IF; END $$;"
    )

    # Simpler: use createdb which is idempotent-ish
    createdb = shutil.which('createdb')
    env = os.environ.copy()
    env['PGPASSWORD'] = cfg['DB_PASSWORD']

    if createdb:
        result = subprocess.run(
            [createdb,
             '-h', cfg['DB_HOST'], '-p', cfg['DB_PORT'],
             '-U', cfg['DB_USER'], cfg['DB_NAME']],
            env=env,
            capture_output=True, text=True,
        )
        if result.returncode != 0 and 'already exists' not in result.stderr:
            warn(f'createdb output: {result.stderr.strip()}')
        else:
            info(f"Database '{cfg['DB_NAME']}' ready.")
    else:
        warn('createdb not found — skipping DB creation. Create the database manually.')
        return

    # Tables are created by Django migrations — no manual SQL needed.
    info('Database ready. Tables will be created by Django migrations.')


def django_setup(cfg: dict) -> None:
    step('Running Django migrations…')
    python = venv_bin('python')
    env = os.environ.copy()
    env.update({
        'DJANGO_SETTINGS_MODULE': 'config.settings',
        'DB_NAME':     cfg['DB_NAME'],
        'DB_USER':     cfg['DB_USER'],
        'DB_PASSWORD': cfg['DB_PASSWORD'],
        'DB_HOST':     cfg['DB_HOST'],
        'DB_PORT':     cfg['DB_PORT'],
        'DJANGO_SECRET_KEY': cfg.get('DJANGO_SECRET_KEY', 'bootstrap-temp-key'),
    })

    # Django needs at least a stub migrations package
    migrations_dir = BASE_DIR / 'finance' / 'migrations'
    migrations_dir.mkdir(exist_ok=True)
    init_file = migrations_dir / '__init__.py'
    if not init_file.exists():
        init_file.write_text('')

    result = subprocess.run(
        [str(python), str(MANAGE_PY), 'migrate'],
        cwd=str(BASE_DIR),
        env=env,
    )
    if result.returncode != 0:
        warn(
            'Django migrate had non-zero exit. Check DB credentials in .env and retry. '
            'If tables already exist from a previous install, run: '
            'python manage.py migrate --fake-initial'
        )
    else:
        info('Django migrations applied.')


def print_instructions(cfg: dict) -> None:
    activate = str(venv_bin('activate'))
    python   = str(venv_bin('python'))
    print()
    print('─' * 60)
    print(' 🚀  Setup complete!  Next steps:')
    print('─' * 60)
    if IS_WIN:
        print(f'  1. Activate venv:   {VENV_DIR}\\Scripts\\activate')
    else:
        print(f'  1. Activate venv:   source {activate}')
    print(f'  2. Edit .env:       set real DB credentials if needed')
    print(f'  3. Start server:    python manage.py runserver')
    print(f'  4. Open browser:    http://127.0.0.1:8000/')
    print()
    print(f'  DB:  {cfg["DB_NAME"]}  on  {cfg["DB_HOST"]}:{cfg["DB_PORT"]}')
    print('─' * 60)


# ── Main ───────────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Bootstrap the Money Manager Django project.')
    parser.add_argument('--db-name',     default=None)
    parser.add_argument('--db-user',     default=None)
    parser.add_argument('--db-password', default=None)
    parser.add_argument('--db-host',     default=None)
    parser.add_argument('--db-port',     default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    create_env_file()
    env_vals = load_env()

    cfg = {
        'DB_NAME':     args.db_name     or env_vals.get('DB_NAME',     'money_manager'),
        'DB_USER':     args.db_user     or env_vals.get('DB_USER',     'postgres'),
        'DB_PASSWORD': args.db_password or env_vals.get('DB_PASSWORD', 'postgres'),
        'DB_HOST':     args.db_host     or env_vals.get('DB_HOST',     '127.0.0.1'),
        'DB_PORT':     args.db_port     or env_vals.get('DB_PORT',     '5432'),
        'DJANGO_SECRET_KEY': env_vals.get('DJANGO_SECRET_KEY', 'bootstrap-temp-key'),
    }

    create_venv()
    install_deps()
    create_database(cfg)
    django_setup(cfg)
    print_instructions(cfg)


if __name__ == '__main__':
    main()

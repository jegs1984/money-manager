# Installation

Money Manager has two web-app installation paths. Choose one; do not point both
at the same PostgreSQL database unless you understand the database configuration.

## Option A: native macOS installer

This is the easiest local setup on macOS. It installs Homebrew dependencies,
creates a virtual environment and local PostgreSQL database, and creates a
Desktop launcher.

```bash
git clone https://github.com/jegs1984/money-manager.git
cd money-manager
bash installer/setup.sh
```

After setup, launch **Money Manager.app** from the Desktop or run `bash start.sh`.
Open [http://127.0.0.1:8765](http://127.0.0.1:8765).

The installer may install software and create a database user. Read the script
and accept its system prompts only on a machine you administer.

## Option B: Docker Compose

Install and start Docker Desktop, then from the repository root run:

```bash
cp .env.example .env
# Edit .env and change DJANGO_SECRET_KEY and DB_PASSWORD.
docker compose up --build
```

Open [http://127.0.0.1:8765](http://127.0.0.1:8765). The stack runs migrations
automatically and stores PostgreSQL data in a named Docker volume. See the
[Docker guide](../docker/README.md) for operations and safety notes.

## Manual Python setup

Use this when PostgreSQL and Python are already installed:

```bash
cp .env.example .env
# Set DB_* in .env for a database you own.
python3 bootstrap.py
source venv/bin/activate
python manage.py runserver 127.0.0.1:8765
```

`bootstrap.py` creates the database when `createdb` is available, then applies
Django migrations. If it cannot create the database, create it with your normal
PostgreSQL administration workflow and rerun `python manage.py migrate`.

## First-use checklist

1. Change the placeholder secret key and database password in `.env`.
2. Create an application user with `python manage.py createsuperuser` if login
   is required by your configuration.
3. Open the app and create a period, categories, and budget items before
   importing statements.
4. Take a first backup after entering important data.

## Android setup

Install JDK 17 and Android Studio, then:

```bash
cd android
./gradlew test --no-daemon
./gradlew assembleDebug
```

The Android client is offline and does not sync with the web application. See
the [Android guide](../android/README.md) for installation and notification
permissions.

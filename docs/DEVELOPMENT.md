# Development guide

## Local web workflow

Use the native installer or the manual setup instructions in
[Installation](INSTALLATION.md). With the virtual environment active:

```bash
python manage.py runserver 127.0.0.1:8765
python manage.py check
python manage.py test
```

For Docker, use `docker compose up --build`; the source directory is mounted for
development and the entrypoint applies migrations at startup.

## Database changes

Django migrations are the authoritative schema history. When changing a Django
model, create and review a migration:

```bash
python manage.py makemigrations finance
python manage.py migrate
python manage.py makemigrations --check --dry-run
```

Do not edit an already-applied migration. The `sql/` directory is historical or
reference material and is not an alternative installation/migration path.

Model validation enforces transaction dates within their budget period; the
database migration also establishes the corresponding data-integrity protection.

## Android workflow

```bash
cd android
./gradlew test --no-daemon
./gradlew assembleDebug
```

Keep Kotlin tests under `android/app/src/test/` and name them `*Test.kt`. Room
schema changes need a tested, non-destructive migration; do not use destructive
fallback migration behavior.

## Project conventions

- Keep Django request handlers thin; place parsing, reconciliation, and export
  logic in `finance/services.py`.
- Use four-space indentation, `snake_case` in Python, and `PascalCase` for
  Django models/forms and Kotlin classes/composables.
- Keep Compose screens and ViewModels paired by feature.
- Keep `.env`, database dumps, local Gradle/Python caches, and keystores out of
  version control.

## Before a pull request

Run the checks relevant to your change, including `python manage.py test` for
web behavior and `./gradlew test --no-daemon` for Android code. For Docker
configuration changes, run:

```bash
docker compose config --quiet
```

Document user-visible behavior, migrations/configuration changes, and the
validation you ran. Include screenshots for Django or Compose UI changes.

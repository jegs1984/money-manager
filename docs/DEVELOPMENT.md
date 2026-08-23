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
Budget items are uniquely identified by period, category, and direction; import
commits retain an immutable staging source and source fingerprint. Do not edit
the historical `sql/` scripts to change the live application schema.

## Android workflow

```bash
cd android
./gradlew test --no-daemon
./gradlew assembleDebug
```

Keep Kotlin tests under `android/app/src/test/` and name them `*Test.kt`. Room
schema changes need a tested, non-destructive migration; do not use destructive
fallback migration behavior.

The web bundle format is versioned and encrypted with `cryptography`'s
AES-GCM implementation. Any format change must preserve idempotent import and
must be documented before Android support is added.

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

GitHub Actions repeats Django migration checks/tests against PostgreSQL and
Android unit/debug builds for every push and pull request. Keep schema-parity
notes in the migration and Room-migration comments whenever a shared field
changes.

See [schema parity](SCHEMA_PARITY.md) for the explicit web/Android change checklist.

## Fixtures bancarios

`finance/tests/fixtures/` contiene cartolas sintéticas y sus resultados esperados. Úsalas para pruebas de parser; no agregues cuentas, comercios, montos o cartolas reales al repositorio.
# Estilos locales

Las plantillas usan `static/finance/tailwind.css`, no una CDN. Después de cambiar las clases de las plantillas o `static/finance/tailwind-input.css`, recompila el archivo versionado con:

```bash
npx --yes tailwindcss@3.4.17 -i static/finance/tailwind-input.css -o static/finance/tailwind.css --content 'templates/**/*.html,finance/**/*.py' --minify
```

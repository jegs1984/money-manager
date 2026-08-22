# Backup and recovery

Financial data is stored in PostgreSQL for the web app. Take a backup before an
upgrade or any action that removes a database or Docker volume. Store backups in
private, encrypted storage where appropriate.

## Docker backup

From the repository root, create a portable SQL dump:

```bash
docker compose exec -T db pg_dump -U money_manager_user money_manager > money-manager-backup.sql
```

Verify that the resulting file is non-empty and keep it outside the repository.

To restore into a deliberately prepared empty Docker database:

```bash
docker compose exec -T db psql -U money_manager_user -d money_manager < money-manager-backup.sql
```

Restoring writes database records and may replace or conflict with existing data.
Stop and take a fresh backup before using it on any database that contains data.

## Native PostgreSQL backup

Use the database details from `.env`:

```bash
PGPASSWORD='your-db-password' pg_dump \
  -h 127.0.0.1 -p 5432 -U money_manager_user money_manager \
  > money-manager-backup.sql
```

For restoration, create or select an empty target database, then use `psql`:

```bash
PGPASSWORD='your-db-password' psql \
  -h 127.0.0.1 -p 5432 -U money_manager_user -d money_manager \
  < money-manager-backup.sql
```

Use a protected secret-management method rather than placing real passwords in
shell history when operating outside a local development machine.

## Docker volume warning

`docker compose down -v` permanently removes the named PostgreSQL volume. It is
appropriate only when you intentionally want to discard the database and have a
verified backup. Plain `docker compose down` preserves the volume.

## Recovery check

After a restore, start the app, sign in if login is enabled, and confirm that
periods, categories, budget items, and recent transactions appear as expected.
Run `python manage.py migrate` (or start the Docker web service) so the restored
database has all current migrations applied.

# Web / Android schema parity

Django migrations are the authoritative schema for the web ledger. Android Room
is a local, offline companion schema and must retain the shared core names:
`finance_period`, `finance_category`, `finance_budget_item`,
`finance_transaction`, and the two staging tables.

When a shared field changes:

1. add a Django migration and review `python manage.py makemigrations --check`;
2. add an append-only Room migration in `RoomMigrations.kt` and export the Room
   schema with the KSP `room.schemaLocation` option;
3. update this document if the change cannot be represented on Android yet;
4. run both CI jobs. Django uses PostgreSQL and Android runs unit tests plus a
   debug compilation on every push and pull request.

Neither client may use a destructive migration fallback for financial data.

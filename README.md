# Money Manager

A local-first personal finance and bank-reconciliation tool built with **Django + PostgreSQL**
and a native **Android** companion app (Kotlin · Jetpack Compose · Room · Hilt).

Import Scotiabank Chile `.dat` debit statements or `.xls` credit-card statements, classify
transactions into flexible budget categories, track spending against projected budgets
across custom date periods, and export clean PDF budget reports.

---

## Project Structure

```
money-manager/
├── bootstrap.py              ← one-shot setup script (run first)
├── manage.py
├── requirements.txt
├── start.sh                  ← macOS one-command launcher
├── .env.example              ← copy to .env and fill in credentials
│
├── config/
│   ├── settings.py
│   ├── urls.py
│   └── wsgi.py
│
├── finance/
│   ├── models.py             ← Period, Category, BudgetItem, Transaction,
│   │                            StagingTransaction, StagingCCTransaction
│   ├── services.py           ← ETL parsers · business logic · PDF export
│   ├── views.py              ← CBVs (thin — all logic in services.py)
│   ├── forms.py
│   └── urls.py
│
├── templates/finance/        ← Tailwind dark-mode HTML templates
│
├── sql/
│   └── init_db.sql           ← raw schema reference (bootstrap uses migrations)
│
└── android/                  ← Kotlin / Compose companion app
    ├── app/src/main/java/com/moneymanager/
    │   ├── data/db/          ← Room entities + DAOs (mirrors Django models)
    │   ├── data/repository/  ← FinanceRepository (single source of truth)
    │   ├── domain/usecase/   ← ParseDebitStatementUseCase, ParseCCStatementUseCase,
    │   │                        DetectStagingDuplicatesUseCase
    │   ├── notifications/    ← BankNotificationService (push-notification ingestion)
    │   ├── di/               ← Hilt DatabaseModule
    │   └── ui/               ← Compose screens, ViewModels, Theme, NavGraph
    └── gradle/libs.versions.toml
```

---

## Prerequisites

### Web App

| Requirement | Minimum | Notes |
|---|---|---|
| Python | 3.11 | |
| PostgreSQL | 14 | |
| `psql` / `createdb` on PATH | | For bootstrap auto-setup |
| LibreOffice (`soffice`) | any | CC `.xls` → CSV conversion |
| reportlab | 4.0 | Installed via `pip` automatically |

Install LibreOffice:

```bash
# macOS
brew install --cask libreoffice

# Ubuntu / Debian
sudo apt install libreoffice-calc
```

### Android App

| Requirement | Notes |
|---|---|
| Android Studio Hedgehog+ | Includes Gradle + SDK |
| JDK 17 | Required by the build system |
| Android device API 26+ (Android 8) | minSdk = 26 |
| Apache POI (`poi:3.17`) | Bundled via Gradle — no manual install |

---

## Setup (Web — First Time)

### 1. Clone and configure

```bash
cp .env.example .env
```

Edit `.env`:

```env
DJANGO_SECRET_KEY=your-long-random-secret-key
DJANGO_DEBUG=True
DJANGO_ALLOWED_HOSTS=127.0.0.1 localhost

DB_NAME=money_manager
DB_USER=postgres
DB_PASSWORD=yourpassword
DB_HOST=127.0.0.1
DB_PORT=5432
```

### 2. Run bootstrap

```bash
python bootstrap.py
```

This will:
- Create `./venv` and install all dependencies (including `reportlab`)
- Create the PostgreSQL database
- Run `python manage.py migrate`

Optional flags:

```bash
python bootstrap.py --db-name money_manager --db-user postgres --db-password secret
```

**Existing install** (tables created by previous raw-SQL run):

```bash
source venv/bin/activate
python manage.py migrate --fake-initial
```

---

## Running the Web App

### macOS — one command

```bash
bash start.sh
```

Starts PostgreSQL if needed, activates the venv, launches Django on
`http://127.0.0.1:8765`, and opens the browser automatically.

First time on a Mac:

```bash
bash installer/setup.sh   # installs all deps + creates DB
bash start.sh             # every subsequent launch
```

### Manual

```bash
source venv/bin/activate          # macOS / Linux
# venv\Scripts\activate           # Windows

python manage.py runserver 127.0.0.1:8765
# → http://127.0.0.1:8765/
```

---

## Core Concepts

### Period

A custom date range (`start_date` → `end_date`). Transactions are matched to a Period
by date overlap — not calendar months. Create Periods before importing statements.

### Category + Group

A Category tags what a transaction represents. Every Category belongs to one Group:

| Group | Examples |
|---|---|
| `AhorroeInversion` | Emergency fund, ETFs |
| `Alimentación` | Groceries, delivery |
| `Cuenta` | Bank fees, transfers |
| `Deuda` | Loan payments, credit card payment |
| `Gastos` | General spending |
| `GastosExtraordinarios` | One-off large expenses |
| `IngresoFijo` | Salary |
| `IngresoVariableExtra` | Freelance, bonuses |
| `Lujo` | Dining out, subscriptions |
| `Pension` | AFP, compulsory savings |
| `Personal` | Healthcare, clothing |
| `Transporte` | Uber, metro, petrol |
| `ViviendaHogar` | Rent, utilities, maintenance |

### BudgetItem

Joins a Period + Category and stores a `projected_amount`. Type is `IN` (income) or
`OUT` (expense).

### Transaction

A committed ledger entry linked to a BudgetItem. Real amounts are compared against
projected amounts to produce the dashboard metrics.

### Staging

`StagingTransaction` (debit) and `StagingCCTransaction` (credit card) hold raw ETL
data pending manual categorisation. Once committed they become `Transaction` rows.

---

## Importing Bank Statements

### Debit — Scotiabank `.dat`

Format: semicolon-delimited cartola with a `;`-prefixed header block.

```
;Cartola
;Numero Cuenta : 98-09719-10
;Fecha Desde   : 21/04/2026
;Fecha Hasta   : 05/06/2026
Fecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo
   21042026;REDCOMPRA SUPERMERCADO;13244336;0000000005230,00;;+0000000032190,00
```

- `Cargos` (charge) → type `OUT`
- `Abonos` (credit) → type `IN`
- `NroDoc.` of all zeros → treated as absent

**Steps:**

1. **Import Statement** → upload `.dat` file → **Parse & Stage**
2. **Staging Review** → assign a Category to each row
   - Rows left on `— Skip —` are not committed
   - **Duplicate detection**: rows whose `(date, amount, description)` triple already
     exists in the active period ledger are highlighted in amber with a
     **Keep / Remove from staging** radio control
3. **Commit to Ledger**

### Credit Card — Scotiabank `.xls`

Format: Scotiabank Chile *Estado de Cuenta Nacional de Tarjeta de Crédito* wide
spreadsheet (~75 columns). Requires LibreOffice or `xlrd`+`pandas` on the server.

- Purchases → type `OUT`
- Payments / credits → type `IN`
- Installment (`N° cuota / total`) detail is parsed and displayed

**Steps:** same 3-step flow via **Import Credit Card → CC Staging Review → Commit CC to Ledger**.

---

## Duplicate Detection

When staging transactions are loaded, Money Manager automatically cross-references them
against the active period's committed `Transaction` rows using the triple
`(original_date, amount, description)`.

Duplicate rows are:
- Highlighted with an **amber left border** and "Already in ledger" badge
- Presented with a **Keep / Remove** radio choice instead of a plain category dropdown
- When **Remove** is selected the category dropdown hides and the row is deleted from
  staging on commit (no ledger entry is created)
- When **Keep** is selected the row is treated as a normal staging row and committed
  as a new transaction (useful for legitimate repeat payments)

---

## Dashboard

The Dashboard shows the active Period (switch periods from the dropdown).

| Metric | Meaning |
|---|---|
| Projected Income | Sum of all `IN` budget item projected amounts |
| Projected Expenses | Sum of all `OUT` budget item projected amounts |
| Projection | Projected Income − Projected Expenses |
| Income | Real income received so far this period |
| Expenses | Real expenses paid so far |
| Safe to Spend | Real Income − Real Expenses |
| Used (Burn Rate) | Real Expenses ÷ Real Income × 100 % |

Capacity bars per budget item:
- 🟢 Green — under 80 % of projected used
- 🟡 Amber — 80–100 %
- 🔴 Red — over budget

---

## PDF Export

Export a **projected budget report** for any period directly from the Dashboard.

### How to export

- Click **Export PDF** in the top-right header actions bar, or
- Click the small **PDF** link in the Budget Analysis table header

The report is period-aware: if you have a specific period selected in the dropdown,
the export uses that period. Otherwise it uses the active period.

### What the PDF contains

| Section | Content |
|---|---|
| Header | "Money Manager — Projected Budget Report" + generation timestamp |
| Period block | Period name + date range |
| KPI cards | Projected Income · Projected Expenses · Net Projection (colour-coded) |
| Budget table | Category · Group · Type · **Projected amount only** |
| Income subtotal | Sum of all `IN` items |
| Expenses subtotal | Sum of all `OUT` items |
| Net Projection row | Income subtotal − Expenses subtotal |
| Footer note | Disclaimer that real expenses are not included |

Real expense amounts are **intentionally excluded** — this report is for planning and
sharing projected budgets, not for reviewing actuals.

### URL

```
GET /dashboard/pdf/?period=<period_id>
```

Returns `application/pdf` with `Content-Disposition: attachment`.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DJANGO_SECRET_KEY` | (required) | Django secret key |
| `DJANGO_DEBUG` | `True` | Set `False` in production |
| `DJANGO_ALLOWED_HOSTS` | `127.0.0.1 localhost` | Space-separated list |
| `DB_NAME` | `money_manager` | PostgreSQL database name |
| `DB_USER` | `postgres` | PostgreSQL user |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `DB_HOST` | `127.0.0.1` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |

---

## Android Companion App

The Android app mirrors the web app's domain model and ETL pipeline as a local-first
mobile experience.

### Architecture

| Layer | Contents |
|---|---|
| `data/db/` | 6 Room entities (1-to-1 with Django models), 6 DAOs, `Converters.kt` (`LocalDate ↔ String`). Amounts stored as `String` and converted via `BigDecimal` to preserve decimal precision. |
| `data/repository/` | `FinanceRepository` — single source of truth. Mirrors `services.py`: `processStagingBatch`, `processCCStagingBatch`, `calculateStats`, `isDuplicateInPeriod`. |
| `domain/usecase/` | `ParseDebitStatementUseCase` — mirrors the `.dat` parser. `ParseCCStatementUseCase` — mirrors the XLS parser using Apache POI. `DetectStagingDuplicatesUseCase` — mirrors `get_duplicate_staging_ids`. |
| `notifications/` | `BankNotificationService` (`NotificationListenerService`) — intercepts push notifications from whitelisted Chilean bank apps, extracts CLP amounts via regex, and inserts `StagingTransactionEntity` rows for review. |
| `di/` | Hilt `DatabaseModule` — provides `AppDatabase` + all 6 DAOs as singletons. |
| `ui/` | Compose screens: **Dashboard**, **Staging Review** (debit), **CC Staging Review**. ViewModels: `DashboardViewModel`, `StagingViewModel`, `CCStagingViewModel`, `CategoryViewModel`. Theme mirrors Tailwind palette (Emerald-500, Red-500, Zinc-950…). |

### Schema Parity Rule

Any change to Django `finance/models.py` requires a corresponding update to the Room
entities in `android/app/src/main/java/com/moneymanager/data/db/Entities.kt`.
Both layers must stay in sync.

### Duplicate Detection (Android)

`DetectStagingDuplicatesUseCase` runs against the active period's committed `Transaction`
rows using the same `(date, amount, description)` triple as the web app.
Duplicate staging rows appear with amber borders and **Keep / Remove** radio buttons
in the Staging Review and CC Staging Review screens.

### Notification Access

`BankNotificationService` requires manual permission grant:

```
Settings → Apps → Special app access → Notification access → Money Manager
```

Whitelisted package IDs (extend in `BankNotificationService.kt`):

| App | Package |
|---|---|
| Scotiabank Chile | `cl.scotiabankchile.banca` |
| BCI | `cl.bci.bci` |
| Santander | `cl.santander.mobile` |
| BancoEstado | `cl.bancoestado.app` |
| Itaú | `cl.itau` |
| Banco Falabella | `com.falabella.falabellabank` |

### Building

**Debug (daily development):**

1. Open `android/` in Android Studio
2. Wait for Gradle sync
3. Connect device via USB with USB Debugging enabled
4. Press the green **Run** button (Shift+F10)

**Release APK:**

```bash
# Generate a signing keystore (one-time)
keytool -genkeypair \
  -alias money-manager \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -keystore money-manager-release.jks \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD

# Set credentials as env vars (don't commit passwords)
export RELEASE_STORE_PASSWORD=YOUR_STORE_PASSWORD
export RELEASE_KEY_PASSWORD=YOUR_KEY_PASSWORD

# Build
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

**Install via ADB:**

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Production Checklist (Web)

- [ ] `DJANGO_DEBUG=False`
- [ ] Strong random `DJANGO_SECRET_KEY`
- [ ] `DJANGO_ALLOWED_HOSTS` set to your domain
- [ ] `python manage.py collectstatic`
- [ ] Gunicorn behind nginx (or similar reverse proxy)
- [ ] Credentials in a secrets manager, not `.env`

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `Category.group` uses `db_column='"group"'` | `group` is a PostgreSQL reserved word — quoted column name prevents SQL syntax errors |
| All ETL amounts as `Decimal` (Python) / `String`→`BigDecimal` (Kotlin) | Avoids floating-point rounding on Chilean peso amounts |
| Scotiabank `.xls` decoded as `latin-1` / `cp1252` | LibreOffice CSV output defaults to this encoding — never assume UTF-8 |
| Staging → Ledger is `@db_transaction.atomic` | Prevents partial commits if categorisation or period lookup fails mid-batch |
| Duplicate detection keyed on `(date, amount, description)` | Matches the bank's natural uniqueness constraints; doc number is unreliable |
| PDF export shows projected amounts only | Intended for budget planning / sharing, not actuals review |

---

## License

Private / personal use.

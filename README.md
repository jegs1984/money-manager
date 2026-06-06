# Money Manager

A personal finance and bank-reconciliation web application built with Django + PostgreSQL.
Import Scotiabank `.dat` statements, classify transactions into budget categories,
and track spending against projected budgets across flexible date periods.

---

## Project Structure

```
money_manager/
├── bootstrap.py          ← one-shot setup script (run first)
├── manage.py
├── requirements.txt
├── .env.example          ← copy to .env and fill in credentials
├── config/
│   ├── settings.py
│   ├── urls.py
│   └── wsgi.py
├── finance/
│   ├── models.py         ← Period, Category, BudgetItem, Transaction,
│   │                        StagingTransaction, StagingCCTransaction
│   ├── services.py       ← ETL parsers (bank + CC) + business logic
│   ├── views.py          ← CBVs
│   ├── forms.py
│   └── urls.py
├── templates/finance/    ← Tailwind-styled HTML templates
└── sql/
    ├── init_db.sql                  ← full schema (fresh install)
    └── migrate_add_cc_staging.sql   ← migration for existing installs
```

---

## Prerequisites

| Requirement | Minimum version | Notes |
|---|---|---|
| Python | 3.11 | |
| PostgreSQL | 14 | |
| `psql` / `createdb` on PATH | | For bootstrap auto-setup |
| LibreOffice Calc | any | For CC `.xls` parsing |

Install LibreOffice on Ubuntu/Debian:
```bash
sudo apt install libreoffice-calc
```

On macOS:
```bash
brew install --cask libreoffice
```

---

## Setup (first time)

### 1 — Clone / copy the project

Put all files in the same folder. The expected root is `money_manager/`.

### 2 — Configure environment

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

### 3 — Run the bootstrap script

```bash
python bootstrap.py
```

The script will:
- Create a Python virtual environment (`./venv`)
- Install all dependencies from `requirements.txt`
- Create the PostgreSQL database (requires `createdb` on PATH)
- Run `python manage.py migrate` — Django creates all tables automatically

You can also pass DB credentials directly:

```bash
python bootstrap.py --db-name money_manager --db-user postgres --db-password secret
```

If `psql`/`createdb` are not on PATH, create the database manually first:

```bash
createdb -U postgres money_manager
# then re-run bootstrap — it will skip the createdb step
python bootstrap.py
```

**Existing install (tables already created by a previous raw-SQL run)?**
Run this once to let Django record the migrations as applied without re-creating tables:

```bash
source venv/bin/activate
python manage.py migrate --fake-initial
```

---

## Startup

```bash
# Activate the virtual environment
source venv/bin/activate          # macOS / Linux
venv\Scripts\activate             # Windows

# Start the development server
python manage.py runserver

# Open in browser
http://127.0.0.1:8000/
```

---

## How To

### Create a Period

A **Period** is any date range you want to budget against (monthly, bi-weekly, per project, etc.).

1. Go to **Manage → Periods → + New Period**
2. Give it a name (e.g. `"May 2026"`, `"Pay Cycle 12"`)
3. Set Start Date and End Date
4. Check **Active** to make it the default on the Dashboard

> Imported transactions are matched to a Period by date overlap
> (`start_date ≤ transaction_date ≤ end_date`).
> Create Periods before importing statements.

---

### Create Categories

Categories tag what each transaction represents.

1. Go to **Manage → Categories → + New Category**
2. Set a name and assign a group:

| Group | Examples |
|---|---|
| `ESSENTIAL` | Rent, Utilities, Groceries |
| `LIFESTYLE` | Dining, Entertainment, Travel |
| `SAVINGS` | Emergency fund, Investments |
| `INCOME` | Salary, Freelance, Transfers in |

---

### Add Budget Items

A **Budget Item** connects a Period + Category and sets a projected amount.

1. Go to **Dashboard → + Budget Item**
2. Select Period, Category, Type (`IN` for income / `OUT` for expense)
3. Enter a projected amount

---

### Import a Credit Card Statement

Supported format: **Scotiabank Chile** *Estado de Cuenta Nacional de Tarjeta de Crédito* `.xls`.

**Field rules:**
- Cardholder name, card number and statement date are extracted from the header block
- Purchases / charges → type `OUT` (positive amounts in the file)
- Payments, credits, point redemptions → type `IN` (negative amounts in the file)
- Installment purchases show `N° cuota / total` (e.g. `06/06`) and the value due this period
- Amounts in CLP with period thousands separator: `$ 42.720` = 42,720 pesos

**Steps:**

1. Go to **Import Credit Card**
2. Drag & drop or browse for your `.xls` Estado de Cuenta file
3. Click **Parse & Stage CC Transactions**
4. On the **CC Staging Review** screen, assign a Category to each row
   - Rows left on `— Skip —` are not committed
   - The card identity (holder + number) is shown in the info bar
   - Installment detail is visible inline — assign the same category across all cuotas
5. Click **Commit CC to Ledger**

> The parser requires LibreOffice on the server PATH to convert `.xls` → CSV internally.
> It handles the wide (~75 column) spreadsheet format and all section separators automatically.

**Existing install** — if you ran the raw SQL yourself before, apply Django's migration record:
```bash
source venv/bin/activate
python manage.py migrate --fake-initial
```
For a fresh install `python bootstrap.py` handles everything automatically.

---

### Import a Bank Statement

Supported format: **Scotiabank Chile `.dat`** (semicolon-delimited cartola).

```
;Cartola
;Numero Cuenta : 98-09719-10
;Fecha Desde   : 21/04/2026
;Fecha Hasta   : 05/06/2026
Fecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo
   21042026;REDCOMPRA SUPERMERCADO     ;13244336;0000000005230,00;;+0000000032190,00
   22042026;TEF 15641702-5 Juan Gaete  ;00000000;;0000000040000,00;+0000000070400,00
```

**Field rules:**
- Date: `DDMMYYYY` with optional leading spaces
- `Cargos` (debit/OUT) and `Abonos` (credit/IN) are mutually exclusive per row
- Amounts: zero-padded with comma decimal (`0000000005230,00`)
- `NroDoc.` of all zeros is treated as absent
- Balance column (6th) is stored for reference, not used in calculations

**Steps:**

1. Go to **Import Statement**
2. Drag & drop or browse for your `.dat` / `.csv` file
3. Click **Parse & Stage Transactions**
4. On the **Staging Review** screen, assign a Category to each row
5. Rows left on `— Skip —` are not committed
6. Click **Commit to Ledger**

> The parser handles mixed CRLF/LF line endings, UTF-8 with BOM,
> and trims all whitespace padding from the original fixed-width fields.

---

### Dashboard

The Dashboard shows the **active Period** (or a period you select from the dropdown).

| Metric | Meaning |
|---|---|
| Safe to Spend | Income received minus expenses paid so far |
| Burn Rate | Expenses ÷ Income (%) |
| Income | Real vs projected |
| Expenses | Real vs projected |

Each budget item row shows a capacity bar:
- 🟢 Green — under 80 % of budget used
- 🟡 Amber — 80–100 %
- 🔴 Red — over budget

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `DJANGO_SECRET_KEY` | (required in prod) | Django secret key |
| `DJANGO_DEBUG` | `True` | Set `False` in production |
| `DJANGO_ALLOWED_HOSTS` | `127.0.0.1 localhost` | Space-separated list |
| `DB_NAME` | `money_manager` | PostgreSQL database name |
| `DB_USER` | `postgres` | PostgreSQL user |
| `DB_PASSWORD` | `postgres` | PostgreSQL password |
| `DB_HOST` | `127.0.0.1` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |

---

## Production Checklist

- [ ] Set `DJANGO_DEBUG=False`
- [ ] Set a strong `DJANGO_SECRET_KEY`
- [ ] Set `DJANGO_ALLOWED_HOSTS` to your real domain
- [ ] Run `python manage.py collectstatic`
- [ ] Serve with gunicorn behind nginx (or similar)
- [ ] Use a proper secret manager for credentials

---

## License

Private / personal use.

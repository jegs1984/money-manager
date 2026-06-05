# Money Manager

A full-stack personal finance and bank reconciliation application designed to ingest Scotiabank Chile bank statements, stage them for bulk category assignment, and map them to a monthly budget matrix.

## Prerequisites

### System Dependencies

- **Python 3.11+** – Minimum supported version
- **PostgreSQL 12+** – Relational database backend
- **pip** (bundled with Python 3.8+) or **virtualenv** – For dependency isolation
- **Git** – For version control

### Verified Stack

```
Django 4.2.0
psycopg2-binary 2.9.6
python-dotenv 1.0.0
```

## Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/money-manager.git
cd money-manager
```

### 2. Create and Activate a Virtual Environment

**macOS / Linux:**

```bash
python3.11 -m venv venv
source venv/bin/activate
```

**Windows (PowerShell):**

```powershell
python -m venv venv
venv\Scripts\Activate.ps1
```

**Windows (Command Prompt):**

```cmd
python -m venv venv
venv\Scripts\activate.bat
```

### 3. Install Dependencies

```bash
pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
```

### 4. Configure Environment Variables

Create a `.env` file in the root directory (same level as `manage.py`):

```env
# Django Settings
SECRET_KEY=your-secret-key-change-this-in-production
DEBUG=True
ALLOWED_HOSTS=localhost,127.0.0.1

# Database Configuration
DATABASE_ENGINE=django.db.backends.postgresql
DATABASE_NAME=money_manager
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
DATABASE_HOST=localhost
DATABASE_PORT=5432

# Security (Production)
SECURE_SSL_REDIRECT=False
SESSION_COOKIE_SECURE=False
CSRF_COOKIE_SECURE=False
```

**Important:** Ensure `DATABASE_NAME` matches the PostgreSQL database you created earlier. If you used a different name (e.g., `myapp`), update it here.

The `config/settings.py` automatically loads these variables using `python-dotenv`:

```python
import os
from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / '.env')

SECRET_KEY = os.getenv('SECRET_KEY', 'django-insecure-development-key-change-in-production')
DEBUG = os.getenv('DEBUG', 'True') == 'True'
ALLOWED_HOSTS = os.getenv('ALLOWED_HOSTS', '*').split(',')

DATABASES = {
    'default': {
        'ENGINE': os.getenv('DATABASE_ENGINE', 'django.db.backends.postgresql'),
        'NAME': os.getenv('DATABASE_NAME', 'money_manager'),
        'USER': os.getenv('DATABASE_USER', 'postgres'),
        'PASSWORD': os.getenv('DATABASE_PASSWORD', 'postgres'),
        'HOST': os.getenv('DATABASE_HOST', 'localhost'),
        'PORT': os.getenv('DATABASE_PORT', '5432'),
    }
}
```

## Database Initialization

### 1. Ensure PostgreSQL is Running

```bash
# macOS (Homebrew)
brew services start postgresql@14

# Linux (systemd)
sudo systemctl start postgresql

# Verify connection
psql -U postgres -h localhost -c "SELECT 1;"
```

### 2. Create the Database

```bash
createdb -U postgres money_manager
```

Or via `psql`:

```bash
psql -U postgres -c "CREATE DATABASE money_manager;"
```

### 3. Apply Raw SQL Schema

```bash
psql -U postgres -d money_manager -f sql/init_db.sql
```

This creates all finance tables with proper constraints:
- `finance_budget_month` – Monthly budget containers
- `finance_category` – Transaction categories
- `finance_budget_item` – Budget projections per category
- `finance_transaction` – Final reconciled transactions
- `finance_staging_transaction` – Import staging table

### 4. Run Django Migrations

```bash
python manage.py migrate
```

This creates Django system tables (`auth_*`, `django_*`, etc.) and applies any app-specific migrations.

### 5. Verify Schema

```bash
psql -U postgres -d money_manager -c "\dt"
```

Expected tables:
- `auth_*` (Django authentication)
- `django_*` (Django internals)
- `finance_budget_month`
- `finance_category`
- `finance_budget_item`
- `finance_transaction`
- `finance_staging_transaction`

## Core Architecture & Data Flow

### System Overview

Money Manager follows a **Service Layer + CBV architecture** with a decoupled ETL pipeline for bank statement ingestion.

### Components

1. **Models Layer** (`finance/models.py`)
   - `BudgetMonth` – Monthly budget container
   - `Category` – Transaction categories (ESSENTIAL, LIFESTYLE, SAVINGS, INCOME)
   - `BudgetItem` – Projected income/expense per category per month
   - `Transaction` – Final reconciled transactions linked to budget items
   - `StagingTransaction` – Unprocessed import staging table

2. **Service Layer** (`finance/services.py`)
   - `parse_scotiabank_statement()` – Parses semicolon-separated Scotiabank cartolas
   - `process_staging_batch()` – Bulk ingests staged transactions via `transaction.atomic()`
   - Additional business logic isolation

3. **View Layer** (`finance/views.py`)
   - Class-Based Views (CBVs) only – No function-based views
   - `StatementUploadView` – File upload handler
   - `StagingReviewView` – Bulk category assignment UI with `modelformset_factory`
   - CRUD views for budget management

4. **Form Layer** (`finance/forms.py`)
   - `StagingTransactionFormSet` – Dynamic formset for bulk-editing staging records
   - Strict read-only fields (date, amount, description locked)
   - Editable field: `assigned_category` (dropdown)

### Data Flow: Bank Statement → Budget Integration

```
1. Upload Bank Statement
   ↓
   StatementUploadView receives .txt or .csv (Scotiabank format)
   
2. Parse & Stage
   ↓
   parse_scotiabank_statement() reads CSV/text
   ↓
   Creates StagingTransaction records (is_processed=False)
   
3. Bulk Review & Assignment
   ↓
   StagingReviewView renders modelformset_factory
   ↓
   User selects assigned_category for each row
   ↓
   Form post saved back to StagingTransaction
   
4. Final Ingestion (Atomic)
   ↓
   process_staging_batch() iterates staged records
   ↓
   Creates Transaction linked to BudgetItem (based on category + current month)
   ↓
   Uses transaction.atomic() — all or nothing
   ↓
   Marks StagingTransaction as is_processed=True
   
5. Dashboard Reflects Actuals
   ↓
   DashboardView aggregates Transaction amounts per BudgetItem
   ↓
   Compares real vs projected amounts
```

### Key Design Patterns

- **Atomic Batch Operations** – `transaction.atomic()` ensures consistency
- **Service Isolation** – Business logic in `services.py`, views remain thin
- **Formset-Driven UI** – Bulk edits via Django `modelformset_factory`
- **Staging Pattern** – Decouples import from posting (allows review)
- **Read-Only Import Fields** – Prevents accidental data corruption

## Usage Guide

### Local Development

#### Start the Development Server

```bash
python manage.py runserver
```

Default: `http://127.0.0.1:8000/`

#### Access the Dashboard

Navigate to:

```
http://localhost:8000/
```

Dashboard displays:
- Active budget month
- Budget items (category-based projections)
- Real vs. projected comparison
- Safe-to-spend calculation
- Burn rate (actual spend / budgeted spend)

### Bank Reconciliation Workflow: Step-by-Step

#### Step 1: Prepare Your Bank Statement

Export a Scotiabank Chile cartola in the following format:

**Semicolon-separated, columns:** `Fecha;Descripcion;Cargos;Abonos`

Example:
```
01062025;COMPRA SUPERMERCADO;150000;
02062025;TRANSFERENCIA ENTRADA;0;2000000
05062025;PAGO SERVICIOS;85000;
```

**Date Format:** `DDMMYYYY`  
**Amount Format:** Numeric with optional comma as decimal (e.g., `1500,50` = 1500.50)  
**Cargos (Debits):** Expenses (type = OUT)  
**Abonos (Credits):** Income (type = IN)

#### Step 2: Upload Statement

1. Navigate to `http://localhost:8000/upload-statement/`
2. Click **"Choose File"** and select your `.txt` or `.csv`
3. Click **"Upload"**
4. System parses the file and creates `StagingTransaction` records
5. Redirect to staging review page

#### Step 3: Review & Bulk Assign Categories

1. Page displays formset with all staged transactions
2. Each row shows:
   - **Fecha** (Date) – read-only
   - **Descripcion** (Description) – read-only
   - **Amount** – read-only
   - **Type** (IN/OUT) – read-only
   - **Assigned Category** – dropdown (editable)
3. For each transaction:
   - Click the dropdown
   - Select appropriate category (ESSENTIAL, LIFESTYLE, SAVINGS, INCOME)
4. Click **"Save & Commit"** to finalize

#### Step 4: Commit to Final Ledger

When you click "Save & Commit":

- `process_staging_batch()` is invoked
- For each staged transaction:
  - Finds or creates matching `BudgetMonth` (based on parsed date)
  - Looks up `BudgetItem` (BudgetMonth + assigned_category)
  - Creates `Transaction` record linked to `BudgetItem`
  - Marks `StagingTransaction.is_processed = True`
- **If any error occurs**, entire batch rolls back (atomic)
- Dashboard refreshes with new actuals

#### Step 5: Verify on Dashboard

Return to `http://localhost:8000/`:

- Budget items now show real amounts
- Projected vs. actual comparison updates
- Safe-to-spend recalculates

### Manual Budget Management

#### Add a Budget Month

1. Navigate to `http://localhost:8000/months/`
2. Click **"+ New Budget Month"**
3. Enter month date (e.g., 2025-06-01)
4. Toggle **"Is Active"** if this is the current month
5. Save

#### Add Categories

1. Navigate to `http://localhost:8000/categories/`
2. Click **"+ New Category"**
3. Enter name (e.g., "Groceries")
4. Select group: ESSENTIAL, LIFESTYLE, SAVINGS, or INCOME
5. Save

#### Create Budget Items

1. Navigate to `http://localhost:8000/budget-items/create/`
2. Select budget month
3. Select category
4. Choose type: IN (Income) or OUT (Expense)
5. Enter projected amount (e.g., 500000 CLP)
6. Save

### Admin Interface

Access Django admin at:

```
http://localhost:8000/admin/
```

**Default credentials** (if superuser created):

```bash
python manage.py createsuperuser
```

From admin:
- Manage all budget months, categories, items, transactions
- Inspect staging transactions
- View system logs

## Development & Contributing

### Project Structure

```
money-manager/
├── config/                  # Django project settings
│   ├── settings.py         # Core configuration
│   ├── urls.py             # Root URL routing
│   └── wsgi.py             # WSGI application
├── finance/                # Main Django app
│   ├── models.py           # ORM models
│   ├── views.py            # Class-based views
│   ├── services.py         # Business logic layer
│   ├── forms.py            # Django forms & formsets
│   ├── urls.py             # App URL routing
│   └── admin.py            # Django admin config
├── templates/finance/      # HTML templates (Tailwind CSS)
├── sql/                    # Raw SQL schemas
│   └── init_db.sql        # PostgreSQL initialization
├── manage.py               # Django management script
├── requirements.txt        # Python dependencies
└── README.md              # This file
```

### Code Standards

- **Architecture:** Service Layer + CBV pattern
- **Database:** PostgreSQL with explicit indexes and constraints
- **Frontend:** Django Templates + Tailwind CSS (no external UI frameworks)
- **Formsets:** `modelformset_factory` for bulk operations

### Running Tests (Future)

Tests are not yet implemented. Add test suite using Django's built-in `TestCase`:

```bash
# Create test file
touch finance/tests.py

# Run all tests
python manage.py test

# Run with coverage (requires coverage package)
pip install coverage
coverage run --source='.' manage.py test
coverage report
```

### Linting & Formatting

No pre-configured linter. Add as needed:

```bash
# Install flake8
pip install flake8

# Check code style
flake8 finance/ config/

# Install black for auto-formatting
pip install black
black finance/ config/
```

### Database Migrations

When modifying models:

```bash
# Create migration
python manage.py makemigrations

# Apply migration
python manage.py migrate

# Show migration status
python manage.py showmigrations
```

### Debugging

Enable verbose SQL logging in `settings.py`:

```python
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
        },
    },
    'loggers': {
        'django.db.backends': {
            'handlers': ['console'],
            'level': 'DEBUG',
        },
    },
}
```

### Environment Tips

- Always activate virtual environment: `source venv/bin/activate`
- Install development dependencies: `pip install -r requirements-dev.txt` (if created)
- Use `.env` file for local overrides; never commit credentials
- Use `DEBUG=False` in production

### Git Workflow

1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes and test locally
3. Commit with clear messages: `git commit -m "Add bank statement validation"`
4. Push and create pull request

## Troubleshooting

### PostgreSQL Connection Error

```
psycopg2.OperationalError: could not connect to server
```

**Solution:**
```bash
# Start PostgreSQL service
brew services start postgresql@14  # macOS
sudo systemctl start postgresql     # Linux

# Test connection
psql -U postgres -h localhost
```

### Django Import Errors

#### "cannot import name 'Coalesce' from 'django.db.models'"

```
ImportError: cannot import name 'Coalesce' from 'django.db.models'
```

**Cause:** `Coalesce` is a function-level database expression, not a direct model import.

**Solution:** Import from `django.db.models.functions`:

```python
# ❌ Wrong
from django.db.models import Sum, F, Value, Coalesce

# ✅ Correct
from django.db.models import Sum, F, Value
from django.db.models.functions import Coalesce
```

This has been corrected in [finance/views.py](finance/views.py#L6).

### Django System Tables Not Appearing

**Problem:** After running `python manage.py migrate`, Django system tables (`auth_*`, `django_*`) don't appear when you check `psql`.

**Cause:** The `DATABASE_NAME` in `.env` doesn't match the PostgreSQL database being queried.

**Solution:**
```bash
# When checking the database, ensure you use the correct database name:
psql -U postgres -d money_manager -c "\dt"

# Update .env to match your database:
DATABASE_NAME=money_manager  # Must match your PostgreSQL database name
```

Then re-run migrations:
```bash
python manage.py migrate
```

### Migration Errors

#### "PROTECT" syntax error in init_db.sql

```
psql:sql/init_db.sql:30: ERROR:  syntax error at or near "PROTECT"
```

**Solution:**
The `ON DELETE PROTECT` syntax is Django-specific and not valid in PostgreSQL. Use `ON DELETE RESTRICT` instead:

```sql
-- ❌ Wrong
category_id BIGINT NOT NULL REFERENCES finance_category(id) ON DELETE PROTECT

-- ✅ Correct
category_id BIGINT NOT NULL REFERENCES finance_category(id) ON DELETE RESTRICT
```

The `sql/init_db.sql` file has been corrected. If you encounter this error, ensure you're using the latest version of the schema file.

#### "relation ... does not exist"

```
django.db.utils.ProgrammingError: relation "finance_staging_transaction" does not exist
```

**Solution:**
```bash
# Clear existing tables and re-apply schema
psql -U postgres -d money_manager -c "DROP TABLE IF EXISTS finance_staging_transaction CASCADE; DROP TABLE IF EXISTS finance_transaction CASCADE; DROP TABLE IF EXISTS finance_budget_item CASCADE; DROP TABLE IF EXISTS finance_budget_month CASCADE; DROP TABLE IF EXISTS finance_category CASCADE;"

# Re-run SQL initialization
psql -U postgres -d money_manager -f sql/init_db.sql

# Then run Django migrations
python manage.py migrate
```

### File Upload Errors

Ensure uploaded file:
- Is in semicolon-separated format
- Has correct column order: `Fecha;Descripcion;Cargos;Abonos`
- Uses date format `DDMMYYYY`
- Uses comma (not period) for decimal separators

### Port Already in Use

```bash
python manage.py runserver 8001
```

Or kill existing process:
```bash
lsof -ti:8000 | xargs kill -9
```

## License

[Add your license here, e.g., MIT, Apache 2.0]

## Contact

For issues, questions, or contributions, open a GitHub issue or contact the maintainer.

---

**Last Updated:** June 4, 2025  
**Version:** 1.0-beta  
**Maintainer:** [Your Name/Organization]

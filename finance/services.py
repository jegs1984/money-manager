"""
services.py
===========
Business logic layer.  All DB writes go through here; views stay thin.

BSA file format (Scotiabank Chile .dat):
-----------------------------------------
Header block  – lines starting with ';', key : value pairs
               ;Numero Cuenta : 98-09719-10
               ;Fecha Desde   : 21/04/2026
               ;Fecha Hasta   : 05/06/2026
Column row    – 'Fecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo'
Data rows     – '   DDMMYYYY;Description  padded to 40 chars;DocNum;Cargo;Abono;Saldo'

Amount encoding:
  '0000000009200,00'   -> 9200.00
  '0000023036712,00'   -> 23036712.00
  '+0000000037420,00'  -> 37420.00  (balance column, always signed)
  ''                   -> 0.00       (absent side of a transaction)

Cargo > 0  → type OUT (money leaving account)
Abono > 0  → type IN  (money entering account)
"""

import csv
import io
import re
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

from django.db import transaction as db_transaction
from django.db.models import Sum, Value, Q
from django.db.models.functions import Coalesce

from .models import BudgetItem, Category, Period, StagingCCTransaction, StagingTransaction, Transaction


# ─────────────────────────────────────────────
# Internal helpers
# ─────────────────────────────────────────────

def _parse_amount(raw: str) -> Decimal:
    s = raw.strip().lstrip('+').lstrip('-').replace(',', '.').strip()
    s = re.sub(r'[^\d.]', '', s)
    if not s:
        return Decimal('0.00')
    try:
        return Decimal(s).quantize(Decimal('0.01'))
    except InvalidOperation:
        return Decimal('0.00')


def _parse_header(lines: list[str]) -> dict:
    meta = {}
    for line in lines:
        stripped = line.strip()
        if not stripped.startswith(';'):
            break
        if ':' not in stripped:
            continue
        _, _, value = stripped.partition(':')
        value = value.strip()
        lower = stripped.lower()
        if 'numero cuenta' in lower:
            meta['account_number'] = value
        elif 'fecha desde' in lower:
            meta['date_from'] = value
        elif 'fecha hasta' in lower:
            meta['date_to'] = value
    return meta


def _parse_date(raw: str) -> date | None:
    s = raw.strip()
    digits = re.sub(r'\D', '', s)
    if len(digits) == 8:
        try:
            return datetime.strptime(digits, '%d%m%Y').date()
        except ValueError:
            pass
    try:
        return datetime.strptime(s, '%d/%m/%Y').date()
    except ValueError:
        pass
    return None


_HEADER_TOKENS = {'fecha', 'descripcion', 'nrodoc', 'cargos', 'abonos', 'saldo'}


# ─────────────────────────────────────────────
# Public: Duplicate detection
# ─────────────────────────────────────────────

def get_duplicate_staging_ids(
    period: Period,
    staging_qs,  # QuerySet[StagingTransaction] | QuerySet[StagingCCTransaction]
) -> set[int]:
    """
    Return the set of staging row IDs whose (date, amount, description) triple
    already exists in Transaction rows belonging to the given Period.

    Match key: (Transaction.date == staging.original_date)
              AND (Transaction.real_amount == staging.amount)
              AND (Transaction.description == staging.description)
    """
    # Build a set of (date, amount, description) tuples already in the ledger
    existing = set(
        Transaction.objects.filter(budget_item__period=period)
        .values_list('date', 'real_amount', 'description')
    )

    duplicate_ids: set[int] = set()
    for stx in staging_qs:
        key = (stx.original_date, stx.amount, stx.description)
        if key in existing:
            duplicate_ids.add(stx.pk)

    return duplicate_ids


# ─────────────────────────────────────────────
# Public: Bank ETL
# ─────────────────────────────────────────────

def parse_scotiabank_statement(file_obj, source_filename: str = '') -> dict:
    """
    Parse a Scotiabank .dat / .csv bank statement and bulk-insert
    StagingTransaction records (is_processed=False).

    Returns:
        {
            'count':          int,   # rows staged
            'skipped':        int,   # rows ignored (zero-amount, bad date, etc.)
            'account_number': str,
            'date_from':      str,
            'date_to':        str,
        }
    """
    if hasattr(file_obj, 'read'):
        raw = file_obj.read()
        if isinstance(raw, bytes):
            raw = raw.decode('utf-8', errors='replace')
    else:
        raw = str(file_obj)

    raw = raw.replace('\r\n', '\n').replace('\r', '\n')
    lines = raw.split('\n')

    meta           = _parse_header(lines)
    account_number = meta.get('account_number', '')

    staging_records = []
    skipped = 0

    reader = csv.reader(io.StringIO(raw), delimiter=';')

    for row in reader:
        if not row:
            continue

        first = row[0]
        first_stripped = first.strip()

        if first_stripped.startswith(';') or first_stripped == '':
            continue

        token = re.sub(r'[\s.]', '', first_stripped).lower()
        if token in _HEADER_TOKENS:
            continue

        if len(row) < 5:
            skipped += 1
            continue

        date_obj = _parse_date(first_stripped)
        if date_obj is None:
            skipped += 1
            continue

        description = re.sub(r'\s+', ' ', row[1].strip())
        if not description:
            skipped += 1
            continue

        doc_raw = row[2].strip() if len(row) > 2 else ''
        doc_number = doc_raw if doc_raw and not re.fullmatch(r'0+', doc_raw) else None

        cargo = _parse_amount(row[3]) if len(row) > 3 else Decimal('0.00')
        abono = _parse_amount(row[4]) if len(row) > 4 else Decimal('0.00')

        balance_raw = row[5].strip() if len(row) > 5 else ''
        balance = _parse_amount(balance_raw) if balance_raw else None

        if cargo > Decimal('0.00') and abono > Decimal('0.00'):
            tx_type, amount = 'OUT', cargo
        elif cargo > Decimal('0.00'):
            tx_type, amount = 'OUT', cargo
        elif abono > Decimal('0.00'):
            tx_type, amount = 'IN', abono
        else:
            skipped += 1
            continue

        staging_records.append(
            StagingTransaction(
                source_file=source_filename or None,
                account_number=account_number or None,
                original_date=date_obj,
                description=description,
                doc_number=doc_number,
                amount=amount,
                balance=balance,
                type=tx_type,
                is_processed=False,
                assigned_category=None,
            )
        )

    StagingTransaction.objects.bulk_create(staging_records)

    return {
        'count':          len(staging_records),
        'skipped':        skipped,
        'account_number': account_number,
        'date_from':      meta.get('date_from', ''),
        'date_to':        meta.get('date_to', ''),
    }


# ─────────────────────────────────────────────
# Internal: Period / BudgetItem helpers
# ─────────────────────────────────────────────

def _get_or_create_unplanned_category() -> Category:
    cat, _ = Category.objects.get_or_create(
        name='Unplanned/Extra',
        defaults={'group': 'LIFESTYLE'},
    )
    return cat


def _get_or_create_budget_item(period: Period, category: Category, tx_type: str) -> BudgetItem:
    item, _ = BudgetItem.objects.get_or_create(
        period=period,
        category=category,
        defaults={'type': tx_type, 'projected_amount': Decimal('0.00')},
    )
    return item


# ─────────────────────────────────────────────
# Public: Staging batch processor
# ─────────────────────────────────────────────

@db_transaction.atomic
def process_staging_batch(staging_ids_with_categories: list[dict], remove_ids: set[int] | None = None) -> int:
    """
    Accept: [{'staging_id': int, 'category_id': int}, ...]
    remove_ids: set of StagingTransaction PKs marked "remove" by the user
                (duplicate rows the user chose to discard).

    For each entry:
      - Finds the Period whose date range contains original_date.
      - Creates a Transaction linked to the correct BudgetItem.
      - Marks StagingTransaction.is_processed = True.

    Rows in remove_ids are deleted from staging without creating a Transaction.
    Rows with no matching Period are silently skipped.
    Returns count of committed transactions.
    """
    if remove_ids:
        StagingTransaction.objects.filter(id__in=remove_ids, is_processed=False).delete()

    processed = 0

    for entry in staging_ids_with_categories:
        staging_id  = entry.get('staging_id')
        category_id = entry.get('category_id')
        if not staging_id or not category_id:
            continue

        try:
            stx = StagingTransaction.objects.select_for_update().get(
                id=staging_id, is_processed=False
            )
        except StagingTransaction.DoesNotExist:
            continue

        try:
            category = Category.objects.get(id=category_id)
        except Category.DoesNotExist:
            category = _get_or_create_unplanned_category()

        period = Period.objects.filter(
            start_date__lte=stx.original_date,
            end_date__gte=stx.original_date,
        ).first()

        if period is None:
            continue

        budget_item = _get_or_create_budget_item(period, category, stx.type)

        Transaction.objects.create(
            budget_item=budget_item,
            date=stx.original_date,
            real_amount=stx.amount,
            description=stx.description,
            notes=None,
        )

        stx.is_processed     = True
        stx.assigned_category = category
        stx.save(update_fields=['is_processed', 'assigned_category'])
        processed += 1

    return processed


# ─────────────────────────────────────────────
# Public: Standard business logic
# ─────────────────────────────────────────────

def log_transaction_service(
    tx_date: date,
    amount: Decimal,
    description: str,
    category_id: int,
    notes: str = None,
) -> Transaction:
    category = Category.objects.get(id=category_id)
    period = Period.objects.filter(
        start_date__lte=tx_date,
        end_date__gte=tx_date,
    ).first()
    if period is None:
        raise ValueError(f'No Period covers date {tx_date}. Create one first.')
    if description.strip() == '-':
        desc = category.name
    else:
        desc = description
    tx_type     = 'IN' if amount >= Decimal('0.00') else 'OUT'
    budget_item = _get_or_create_budget_item(period, category, tx_type)
    return Transaction.objects.create(
        budget_item=budget_item,
        date=tx_date,
        real_amount=abs(amount),
        description=desc,
        notes=notes,
    )


@db_transaction.atomic
def rollover_period_balance(source_period_id: int, target_period_id: int) -> Decimal:
    source = Period.objects.get(id=source_period_id)
    target = Period.objects.get(id=target_period_id)
    rollover_total = Decimal('0.00')

    for item in BudgetItem.objects.filter(period=source).prefetch_related('transactions'):
        total_real = item.transactions.aggregate(
            s=Coalesce(Sum('real_amount'), Value(Decimal('0.00')))
        )['s']
        rollover_total += item.projected_amount - total_real

    if rollover_total != Decimal('0.00'):
        cat         = _get_or_create_unplanned_category()
        target_item = _get_or_create_budget_item(target, cat, 'IN')
        Transaction.objects.create(
            budget_item=target_item,
            date=target.start_date,
            real_amount=abs(rollover_total),
            description=f'Rollover from {source}',
        )
    return rollover_total


# ─────────────────────────────────────────────
# Credit Card XLS parser
# ─────────────────────────────────────────────

def _parse_clp_amount(raw: str) -> Decimal | None:
    s = raw.strip()
    if not s:
        return None
    s = s.replace('$', '').strip()
    negative = s.startswith('-')
    s = s.lstrip('-').strip()
    s = s.replace('.', '').replace(',', '.')
    s = re.sub(r'[^\d.]', '', s)
    if not s:
        return None
    try:
        val = Decimal(s).quantize(Decimal('0.01'))
        return -val if negative else val
    except InvalidOperation:
        return None


def _parse_cc_header(rows: list[list[str]]) -> dict:
    meta = {}
    for row in rows[:10]:
        for cell_idx in range(len(row)):
            cell = row[cell_idx].strip()
            upper = cell.upper()
            if not cell or ('VISA' not in upper and 'MASTER' not in upper and 'XXXX' not in upper):
                continue
            parts = [p.strip() for p in re.split(r'\n|\\n', cell) if p.strip()]
            if len(parts) >= 1:
                meta['card_holder'] = parts[0]
            if len(parts) >= 2:
                m = re.search(r'([A-Z]+\s+[\dX-]+)', parts[1])
                meta['card_number'] = m.group(1) if m else parts[1]
            if len(parts) >= 3:
                try:
                    meta['statement_date'] = datetime.strptime(parts[2], '%d/%m/%Y').date()
                except ValueError:
                    pass
            if meta:
                break
        if meta:
            break
    return meta


def parse_scotiabank_cc_statement(file_obj, source_filename: str = '') -> dict:
    """
    Parse a Scotiabank credit card statement in .xls format.
    Returns:
        {
            'count':          int,
            'skipped':        int,
            'card_number':    str,
            'card_holder':    str,
            'statement_date': date | None,
        }
    """
    import csv as csv_mod
    import io as io_mod
    import subprocess
    import tempfile
    import os

    if hasattr(file_obj, 'read'):
        raw_bytes = file_obj.read()
    else:
        raw_bytes = file_obj

    csv_text = None

    try:
        import xlrd  # noqa: F401
        import pandas as pd
        import io as _io
        buf = _io.BytesIO(raw_bytes)
        xl = pd.ExcelFile(buf, engine='xlrd')
        df = xl.parse(xl.sheet_names[0], header=None)
        csv_buf = _io.StringIO()
        df.to_csv(csv_buf, index=False, header=False)
        csv_text = csv_buf.getvalue()
    except Exception:
        pass

    if csv_text is None:
        try:
            with tempfile.NamedTemporaryFile(suffix='.xls', delete=False) as tmp:
                tmp.write(raw_bytes)
                tmp_path = tmp.name
            out_dir = tempfile.mkdtemp()
            for binary in ('soffice', 'libreoffice'):
                result = subprocess.run(
                    [binary, '--headless', '--convert-to', 'csv',
                     '--outdir', out_dir, tmp_path],
                    capture_output=True, timeout=60,
                )
                if result.returncode == 0:
                    break
            csv_path = os.path.join(out_dir, os.path.basename(tmp_path).replace('.xls', '.csv'))
            if result.returncode == 0 and os.path.exists(csv_path):
                for enc in ('utf-8', 'latin-1', 'cp1252'):
                    try:
                        with open(csv_path, encoding=enc) as f:
                            csv_text = f.read()
                        break
                    except UnicodeDecodeError:
                        continue
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
        except Exception:
            pass

    if csv_text is None:
        raise ValueError(
            'Could not parse the XLS file. '
            'Install xlrd (pip install xlrd==1.2.0) or ensure LibreOffice is on PATH.'
        )

    rows = list(csv_mod.reader(io_mod.StringIO(csv_text)))
    meta = _parse_cc_header(rows)

    date_pat = re.compile(r'^\d{2}/\d{2}/\d{4}$')
    installment_pat = re.compile(r'^(\d+)/(\d+)$')

    SKIP_DESCRIPTIONS = {
        'período facturado', 'pagar hasta', 'período de facturación anterior',
    }

    staging_records = []
    skipped = 0

    for row in rows:
        if len(row) <= 22:
            continue

        date_raw = row[11].strip() if len(row) > 11 else ''
        if not date_pat.match(date_raw):
            continue

        try:
            tx_date = datetime.strptime(date_raw, '%d/%m/%Y').date()
        except ValueError:
            skipped += 1
            continue

        description = re.sub(r'\s+', ' ', row[22].strip()) if len(row) > 22 else ''
        if not description or description.lower() in SKIP_DESCRIPTIONS:
            skipped += 1
            continue

        amount_raw = row[54].strip() if len(row) > 54 else ''
        amount = _parse_clp_amount(amount_raw)
        if amount is None:
            skipped += 1
            continue

        location = re.sub(r'\s+', ' ', row[5].strip()) if len(row) > 5 else ''
        ref_code = row[16].strip() if len(row) > 16 else ''

        installment_current = installment_total = None
        installment_value = None
        inst_raw = row[67].strip() if len(row) > 67 else ''
        m = installment_pat.match(inst_raw)
        if m:
            installment_current = int(m.group(1))
            installment_total   = int(m.group(2))
        inst_val_raw = row[72].strip() if len(row) > 72 else ''
        installment_value = _parse_clp_amount(inst_val_raw)

        tx_type = 'IN' if amount < Decimal('0.00') else 'OUT'
        abs_amount = abs(amount)

        staging_records.append(
            StagingCCTransaction(
                source_file=source_filename or None,
                card_number=meta.get('card_number') or None,
                card_holder=meta.get('card_holder') or None,
                statement_date=meta.get('statement_date') or None,
                original_date=tx_date,
                description=description,
                location=location or None,
                ref_code=ref_code or None,
                amount=abs_amount,
                installment_current=installment_current,
                installment_total=installment_total,
                installment_value=abs(installment_value) if installment_value else None,
                type=tx_type,
                is_processed=False,
                assigned_category=None,
            )
        )

    StagingCCTransaction.objects.bulk_create(staging_records)

    return {
        'count':          len(staging_records),
        'skipped':        skipped,
        'card_number':    meta.get('card_number', ''),
        'card_holder':    meta.get('card_holder', ''),
        'statement_date': meta.get('statement_date'),
    }


@db_transaction.atomic
def process_cc_staging_batch(
    staging_ids_with_categories: list[dict],
    remove_ids: set[int] | None = None,
) -> int:
    """
    Same logic as process_staging_batch but for StagingCCTransaction.
    remove_ids: CC staging PKs the user chose to discard (duplicate → remove).
    """
    if remove_ids:
        StagingCCTransaction.objects.filter(id__in=remove_ids, is_processed=False).delete()

    processed = 0

    for entry in staging_ids_with_categories:
        staging_id  = entry.get('staging_id')
        category_id = entry.get('category_id')
        if not staging_id or not category_id:
            continue

        try:
            stx = StagingCCTransaction.objects.select_for_update().get(
                id=staging_id, is_processed=False
            )
        except StagingCCTransaction.DoesNotExist:
            continue

        try:
            category = Category.objects.get(id=category_id)
        except Category.DoesNotExist:
            category = _get_or_create_unplanned_category()

        period = Period.objects.filter(
            start_date__lte=stx.original_date,
            end_date__gte=stx.original_date,
        ).first()

        if period is None:
            continue

        budget_item = _get_or_create_budget_item(period, category, stx.type)

        Transaction.objects.create(
            budget_item=budget_item,
            date=stx.original_date,
            real_amount=stx.amount,
            description=stx.description,
            notes=f'[CC] {stx.card_number or ""} {stx.location or ""}'.strip() or None,
        )

        stx.is_processed      = True
        stx.assigned_category = category
        stx.save(update_fields=['is_processed', 'assigned_category'])
        processed += 1

    return processed


def calculate_safe_to_spend(period_id: int) -> dict:
    period = Period.objects.get(id=period_id)

    budget_totals = BudgetItem.objects.filter(period=period).aggregate(
        proj_in=Coalesce(Sum('projected_amount', filter=Q(type='IN')), Value(Decimal('0.00'))),
        proj_out=Coalesce(Sum('projected_amount', filter=Q(type='OUT')), Value(Decimal('0.00'))),
    )

    transaction_totals = Transaction.objects.filter(budget_item__period=period).aggregate(
        real_in=Coalesce(Sum('real_amount', filter=Q(budget_item__type='IN')), Value(Decimal('0.00'))),
        real_out=Coalesce(Sum('real_amount', filter=Q(budget_item__type='OUT')), Value(Decimal('0.00'))),
    )

    ti_proj = budget_totals['proj_in']
    te_proj = budget_totals['proj_out']
    ti_real = transaction_totals['real_in']
    te_real = transaction_totals['real_out']

    safe_to_spend  = ti_real - te_real
    total_projected = ti_proj - te_proj
    burn_rate = (te_real / ti_real * 100) if ti_real > Decimal('0.00') else Decimal('0.00')

    return {
        'period':                   period,
        'total_income_projected':   ti_proj,
        'total_income_real':        ti_real,
        'total_expense_projected':  te_proj,
        'total_expense_real':       te_real,
        'safe_to_spend':            safe_to_spend,
        'burn_rate':                burn_rate,
        'projection':               total_projected,
    }

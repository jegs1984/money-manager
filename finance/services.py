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
import hashlib
import io
import json
import re
import secrets
import uuid
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation

from django.db import transaction as db_transaction
from django.db.models import Sum, Value, Q
from django.db.models.functions import Coalesce
from django.core.serializers.json import DjangoJSONEncoder

from .models import (
    Account, BudgetItem, Category, Goal, ImportBatch, MerchantRule, Period, Reconciliation,
    RecurringPlan,
    StagingCCTransaction, StagingTransaction, Transaction, Transfer,
)


# ─────────────────────────────────────────────
# Internal helpers
# ─────────────────────────────────────────────

def _parse_amount(raw: str) -> Decimal:
    """Parse a debit/credit amount, which is always stored as positive."""
    s = raw.strip().lstrip('+').lstrip('-').replace(',', '.').strip()
    s = re.sub(r'[^\d.]', '', s)
    if not s:
        return Decimal('0.00')
    try:
        return Decimal(s).quantize(Decimal('0.01'))
    except InvalidOperation:
        return Decimal('0.00')


def _parse_signed_balance(raw: str) -> Decimal:
    """Parse balances without losing their sign."""
    negative = raw.strip().startswith('-')
    amount = _parse_amount(raw)
    return -amount if negative else amount


def suggest_category(description: str, transaction_type: str) -> Category | None:
    """Return a reviewable category suggestion from a user-confirmed merchant rule."""
    normalized = description.casefold()
    for rule in MerchantRule.objects.filter(is_active=True).select_related('category'):
        if rule.transaction_type and rule.transaction_type != transaction_type:
            continue
        if rule.description_pattern.casefold() in normalized:
            return rule.category
    return None


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

def get_duplicate_staging_ids(period: Period | None, staging_qs) -> set[int]:
    """
    Return the set of staging row IDs whose (date, amount, description) triple
    already exists in Transaction rows belonging to the given Period.
    """
    duplicate_ids: set[int] = set()
    for stx in staging_qs:
        row_period = Period.objects.filter(start_date__lte=stx.original_date, end_date__gte=stx.original_date).first()
        if not row_period:
            continue
        existing = Transaction.objects.filter(budget_item__period=row_period).filter(
            date=stx.original_date, real_amount=stx.amount, description=stx.description,
        ).exists()
        if existing:
            duplicate_ids.add(stx.pk)

    return duplicate_ids


# ─────────────────────────────────────────────
# Public: Bank ETL
# ─────────────────────────────────────────────

def parse_scotiabank_statement(file_obj, source_filename: str = '', import_again: bool = False) -> dict:
    if hasattr(file_obj, 'read'):
        raw = file_obj.read()
        if isinstance(raw, bytes):
            raw = raw.decode('utf-8', errors='replace')
    else:
        raw = str(file_obj)

    raw = raw.replace('\r\n', '\n').replace('\r', '\n')
    content_hash = hashlib.sha256(raw.encode('utf-8')).hexdigest()
    existing_batch = ImportBatch.objects.filter(source_type='BANK', content_hash=content_hash).first()
    if existing_batch and not import_again:
        return {'count': 0, 'skipped': 0, 'already_imported': True, 'batch_id': existing_batch.pk,
                'account_number': existing_batch.account_reference, 'date_from': '', 'date_to': ''}
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
        balance = _parse_signed_balance(balance_raw) if balance_raw else None

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
                assigned_category=suggest_category(description, tx_type),
            )
        )

    batch = ImportBatch.objects.create(
        source_type='BANK', filename=source_filename, account_reference=account_number,
        content_hash=content_hash, parser_version='scotiabank-dat-v1',
    )
    for record in staging_records:
        record.batch = batch
    StagingTransaction.objects.bulk_create(staging_records)

    return {
        'count':          len(staging_records),
        'skipped':        skipped,
        'account_number': account_number,
        'date_from':      meta.get('date_from', ''),
        'date_to':        meta.get('date_to', ''),
        'batch_id':       batch.pk,
        'already_imported': False,
    }


# ─────────────────────────────────────────────
# Internal: Period / BudgetItem helpers
# ─────────────────────────────────────────────

def _get_or_create_unplanned_category() -> Category:
    cat, _ = Category.objects.get_or_create(
        name='Unplanned/Extra',
        defaults={'group': 'Gastos'},
    )
    return cat


def _get_or_create_budget_item(period: Period, category: Category, tx_type: str) -> BudgetItem:
    item, _ = BudgetItem.objects.get_or_create(
        period=period, category=category, type=tx_type,
        defaults={'type': tx_type, 'projected_amount': Decimal('0.00')},
    )
    return item


def _assert_period_open(period: Period) -> None:
    if period.closed_at:
        raise ValueError(f'{period.name} is closed. Reopen it before changing its ledger.')


def _get_or_create_import_account(reference: str | None, kind: str) -> Account | None:
    if not reference:
        return None
    account, _ = Account.objects.get_or_create(
        external_reference=reference,
        defaults={
            'name': f'{"Card" if kind == "CREDIT_CARD" else "Account"} {reference}',
            'kind': kind,
        },
    )
    return account


# ─────────────────────────────────────────────
# Public: Staging batch processor
# ─────────────────────────────────────────────

@db_transaction.atomic
def process_staging_batch(staging_ids_with_categories: list[dict], remove_ids: set[int] | None = None) -> int:
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
        _assert_period_open(period)

        budget_item = _get_or_create_budget_item(period, category, stx.type)
        account = _get_or_create_import_account(stx.account_number, 'CHECKING')

        Transaction.objects.create(
            budget_item=budget_item,
            date=stx.original_date,
            real_amount=stx.amount,
            description=stx.description,
            notes=None,
            source_staging_transaction=stx,
            source_fingerprint=f'bank-staging:{stx.pk}',
            account=account,
        )

        stx.is_processed     = True
        stx.assigned_category = category
        stx.save(update_fields=['is_processed', 'assigned_category'])
        processed += 1

    batch_ids = {entry.get('batch_id') for entry in staging_ids_with_categories if entry.get('batch_id')}
    for batch in ImportBatch.objects.filter(id__in=batch_ids):
        if not batch.staging_transactions.filter(is_processed=False).exists():
            batch.status = 'COMMITTED'
            batch.save(update_fields=['status'])

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
    _assert_period_open(period)
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
    _assert_period_open(target)
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
# Public: Duplicate period budget items
# ─────────────────────────────────────────────

@db_transaction.atomic
def duplicate_period_budget_items(source_period_id: int, target_period_id: int) -> dict:
    """
    Copy all BudgetItems from source_period into target_period, preserving
    category, type, and projected_amount.

    - Items whose (period, category) pair already exist in target are skipped
      (no overwrite — the user may have already set amounts manually).
    - No transactions are copied; only the budget skeleton is cloned.

    Returns:
        {'created': int, 'skipped': int}
    """
    source = Period.objects.get(id=source_period_id)
    target = Period.objects.get(id=target_period_id)

    source_items = BudgetItem.objects.filter(period=source).select_related('category')

    existing_keys: set[tuple[int, str]] = set(
        BudgetItem.objects.filter(period=target).values_list('category_id', 'type')
    )

    to_create: list[BudgetItem] = []
    skipped = 0

    for item in source_items:
        if (item.category_id, item.type) in existing_keys:
            skipped += 1
            continue
        to_create.append(BudgetItem(
            period=target,
            category=item.category,
            type=item.type,
            projected_amount=item.projected_amount,
        ))

    BudgetItem.objects.bulk_create(to_create)

    return {'created': len(to_create), 'skipped': skipped}


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


def parse_scotiabank_cc_statement(file_obj, source_filename: str = '', import_again: bool = False) -> dict:
    import csv as csv_mod
    import io as io_mod
    import subprocess
    import tempfile
    import os

    if hasattr(file_obj, 'read'):
        raw_bytes = file_obj.read()
    else:
        raw_bytes = file_obj

    content_hash = hashlib.sha256(raw_bytes).hexdigest()
    existing_batch = ImportBatch.objects.filter(source_type='CREDIT_CARD', content_hash=content_hash).first()
    if existing_batch and not import_again:
        return {'count': 0, 'skipped': 0, 'already_imported': True, 'batch_id': existing_batch.pk,
                'card_number': existing_batch.account_reference, 'card_holder': '', 'statement_date': None}

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
                assigned_category=suggest_category(description, tx_type),
            )
        )

    batch = ImportBatch.objects.create(
        source_type='CREDIT_CARD', filename=source_filename,
        account_reference=meta.get('card_number', ''), content_hash=content_hash,
        parser_version='scotiabank-xls-v1',
    )
    for record in staging_records:
        record.batch = batch
    StagingCCTransaction.objects.bulk_create(staging_records)

    return {
        'count':          len(staging_records),
        'skipped':        skipped,
        'card_number':    meta.get('card_number', ''),
        'card_holder':    meta.get('card_holder', ''),
        'statement_date': meta.get('statement_date'),
        'batch_id':       batch.pk,
        'already_imported': False,
    }


@db_transaction.atomic
def process_cc_staging_batch(
    staging_ids_with_categories: list[dict],
    remove_ids: set[int] | None = None,
) -> int:
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
        _assert_period_open(period)

        budget_item = _get_or_create_budget_item(period, category, stx.type)
        account = _get_or_create_import_account(stx.card_number, 'CREDIT_CARD')

        Transaction.objects.create(
            budget_item=budget_item,
            date=stx.original_date,
            real_amount=stx.amount,
            description=stx.description,
            notes=f'[CC] {stx.card_number or ""} {stx.location or ""}'.strip() or None,
            source_staging_cc_transaction=stx,
            source_fingerprint=f'cc-staging:{stx.pk}',
            account=account,
        )

        stx.is_processed      = True
        stx.assigned_category = category
        stx.save(update_fields=['is_processed', 'assigned_category'])
        processed += 1

    batch_ids = {entry.get('batch_id') for entry in staging_ids_with_categories if entry.get('batch_id')}
    for batch in ImportBatch.objects.filter(id__in=batch_ids):
        if not batch.staging_cc_transactions.filter(is_processed=False).exists():
            batch.status = 'COMMITTED'
            batch.save(update_fields=['status'])

    return processed


# ─────────────────────────────────────────────
# Public: financial operations
# ─────────────────────────────────────────────

@db_transaction.atomic
def reverse_transaction_service(transaction_id: int, notes: str = '') -> Transaction:
    original = Transaction.objects.select_for_update().select_related(
        'budget_item__period', 'budget_item__category'
    ).get(pk=transaction_id)
    if original.reversal_of_id or hasattr(original, 'reversal'):
        raise ValueError('This transaction has already been reversed.')
    period = original.budget_item.period
    _assert_period_open(period)
    reversal_type = 'OUT' if original.budget_item.type == 'IN' else 'IN'
    reversal_item = _get_or_create_budget_item(period, original.budget_item.category, reversal_type)
    return Transaction.objects.create(
        account=original.account,
        budget_item=reversal_item,
        date=original.date,
        real_amount=original.real_amount,
        description=f'Reversal: {original.description}',
        notes=notes or f'Reversal of transaction {original.pk}',
        reversal_of=original,
        source_fingerprint=f'reversal:{original.pk}',
    )


@db_transaction.atomic
def record_transfer_service(
    source_account_id: int,
    destination_account_id: int,
    transfer_date: date,
    amount: Decimal,
    description: str = '',
) -> Transfer:
    source = Account.objects.get(pk=source_account_id)
    destination = Account.objects.get(pk=destination_account_id)
    transfer = Transfer(
        source_account=source,
        destination_account=destination,
        date=transfer_date,
        amount=amount,
        description=description,
    )
    transfer.full_clean()
    transfer.save()
    return transfer


def calculate_account_balance(account_id: int) -> Decimal:
    account = Account.objects.get(pk=account_id)
    transaction_total = Decimal('0.00')
    for tx in account.transactions.select_related('budget_item').all():
        transaction_total += tx.real_amount if tx.budget_item.type == 'IN' else -tx.real_amount
    outgoing = Transfer.objects.filter(source_account=account).aggregate(
        total=Coalesce(Sum('amount'), Value(Decimal('0.00')))
    )['total']
    incoming = Transfer.objects.filter(destination_account=account).aggregate(
        total=Coalesce(Sum('amount'), Value(Decimal('0.00')))
    )['total']
    return account.opening_balance + transaction_total + incoming - outgoing


@db_transaction.atomic
def reconcile_account_service(
    account_id: int,
    statement_date: date,
    statement_balance: Decimal,
    notes: str = '',
) -> Reconciliation:
    calculated_balance = calculate_account_balance(account_id)
    reconciliation, _ = Reconciliation.objects.update_or_create(
        account_id=account_id,
        statement_date=statement_date,
        defaults={
            'statement_balance': statement_balance,
            'calculated_balance': calculated_balance,
            'notes': notes,
        },
    )
    return reconciliation


@db_transaction.atomic
def close_period_service(period_id: int) -> Period:
    period = Period.objects.select_for_update().get(pk=period_id)
    if period.closed_at:
        return period
    has_pending = StagingTransaction.objects.filter(
        is_processed=False,
        original_date__range=(period.start_date, period.end_date),
    ).exists() or StagingCCTransaction.objects.filter(
        is_processed=False,
        original_date__range=(period.start_date, period.end_date),
    ).exists()
    if has_pending:
        raise ValueError('Review or discard every staged row in this period before closing it.')
    from django.utils import timezone
    period.closed_at = timezone.now()
    period.is_active = False
    period.save(update_fields=['closed_at', 'is_active'])
    return period


def _advance_recurring_date(current: date, frequency: str) -> date:
    if frequency == 'WEEKLY':
        return current + timedelta(days=7)
    import calendar
    year = current.year + (current.month // 12)
    month = current.month % 12 + 1
    return current.replace(year=year, month=month, day=min(current.day, calendar.monthrange(year, month)[1]))


@db_transaction.atomic
def materialize_recurring_plans(until: date | None = None) -> int:
    """Create due recurring ledger entries once, retaining a stable source fingerprint."""
    until = until or date.today()
    created = 0
    for plan in RecurringPlan.objects.select_for_update().filter(is_active=True, next_date__lte=until):
        while plan.next_date <= until:
            period = Period.objects.filter(start_date__lte=plan.next_date, end_date__gte=plan.next_date).first()
            fingerprint = f'recurring:{plan.pk}:{plan.next_date.isoformat()}'
            if period and not Transaction.objects.filter(source_fingerprint=fingerprint).exists():
                _assert_period_open(period)
                item = _get_or_create_budget_item(period, plan.category, plan.transaction_type)
                Transaction.objects.create(
                    account=plan.account,
                    budget_item=item,
                    date=plan.next_date,
                    real_amount=plan.amount,
                    description=plan.description,
                    notes=f'Recurring plan: {plan.name}',
                    source_fingerprint=fingerprint,
                )
                created += 1
            plan.next_date = _advance_recurring_date(plan.next_date, plan.frequency)
        plan.save(update_fields=['next_date'])
    return created


@db_transaction.atomic
def contribute_to_goal(goal_id: int, amount: Decimal) -> Goal:
    if amount <= 0:
        raise ValueError('A goal contribution must be greater than zero.')
    goal = Goal.objects.select_for_update().get(pk=goal_id)
    goal.saved_amount += amount
    goal.is_complete = goal.saved_amount >= goal.target_amount
    goal.save(update_fields=['saved_amount', 'is_complete'])
    return goal


# ─────────────────────────────────────────────
# Public: encrypted offline exchange
# ─────────────────────────────────────────────

_BUNDLE_MAGIC = b'MMB1'


def _bundle_key(password: str, salt: bytes) -> bytes:
    if not password:
        raise ValueError('A bundle passphrase is required.')
    return hashlib.scrypt(password.encode('utf-8'), salt=salt, n=2**14, r=8, p=1, dklen=32)


def export_finance_bundle(password: str) -> bytes:
    """Create a versioned AES-GCM encrypted backup/exchange bundle."""
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    payload = {
        'version': 1,
        'bundle_id': str(uuid.uuid4()),
        'categories': list(Category.objects.values('name', 'group')),
        'periods': list(Period.objects.values('name', 'start_date', 'end_date', 'is_active', 'closed_at')),
        'accounts': list(Account.objects.values('name', 'kind', 'external_reference', 'opening_balance', 'is_active')),
        'budget_items': list(BudgetItem.objects.values('period__name', 'category__name', 'type', 'projected_amount')),
        'transactions': list(Transaction.objects.values('id', 'account__name', 'budget_item__period__name', 'budget_item__category__name', 'budget_item__type', 'date', 'real_amount', 'description', 'notes')),
        'transfers': list(Transfer.objects.values('source_account__name', 'destination_account__name', 'date', 'amount', 'description')),
        'merchant_rules': list(MerchantRule.objects.values('description_pattern', 'category__name', 'transaction_type', 'is_active')),
        'recurring_plans': list(RecurringPlan.objects.values('name', 'category__name', 'account__name', 'transaction_type', 'amount', 'frequency', 'next_date', 'description', 'is_active')),
        'goals': list(Goal.objects.values('name', 'target_amount', 'saved_amount', 'target_date', 'notes', 'is_complete')),
    }
    raw = json.dumps(payload, cls=DjangoJSONEncoder, separators=(',', ':')).encode('utf-8')
    salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
    encrypted = AESGCM(_bundle_key(password, salt)).encrypt(nonce, raw, _BUNDLE_MAGIC)
    return _BUNDLE_MAGIC + salt + nonce + encrypted


@db_transaction.atomic
def import_finance_bundle(bundle: bytes, password: str) -> dict:
    from cryptography.exceptions import InvalidTag
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    if len(bundle) < 4 + 16 + 12 or not bundle.startswith(_BUNDLE_MAGIC):
        raise ValueError('This is not a Money Manager encrypted bundle.')
    salt, nonce, encrypted = bundle[4:20], bundle[20:32], bundle[32:]
    try:
        payload = json.loads(AESGCM(_bundle_key(password, salt)).decrypt(nonce, encrypted, _BUNDLE_MAGIC))
    except (InvalidTag, ValueError, json.JSONDecodeError) as exc:
        raise ValueError('The bundle could not be decrypted. Check its passphrase.') from exc
    if payload.get('version') != 1 or not payload.get('bundle_id'):
        raise ValueError('Unsupported bundle version.')

    categories = {}
    for row in payload.get('categories', []):
        category, _ = Category.objects.get_or_create(name=row['name'], defaults={'group': row['group']})
        categories[category.name] = category
    periods = {}
    for row in payload.get('periods', []):
        period, _ = Period.objects.get_or_create(
            name=row['name'], defaults={'start_date': row['start_date'], 'end_date': row['end_date'], 'is_active': False}
        )
        periods[period.name] = period
    accounts = {}
    for row in payload.get('accounts', []):
        account, _ = Account.objects.get_or_create(
            name=row['name'], defaults={k: row[k] for k in ('kind', 'external_reference', 'opening_balance', 'is_active')}
        )
        accounts[account.name] = account
    budget_items = {}
    for row in payload.get('budget_items', []):
        period, category = periods.get(row['period__name']), categories.get(row['category__name'])
        if period and category:
            item, _ = BudgetItem.objects.get_or_create(
                period=period, category=category, type=row['type'], defaults={'projected_amount': row['projected_amount']}
            )
            budget_items[(period.name, category.name, item.type)] = item
    imported_transactions = 0
    for row in payload.get('transactions', []):
        item = budget_items.get((row['budget_item__period__name'], row['budget_item__category__name'], row['budget_item__type']))
        if not item:
            continue
        fingerprint = f"bundle:{payload['bundle_id']}:transaction:{row['id']}"
        _, created = Transaction.objects.get_or_create(
            source_fingerprint=fingerprint,
            defaults={
                'account': accounts.get(row.get('account__name')),
                'budget_item': item, 'date': row['date'], 'real_amount': row['real_amount'],
                'description': row['description'], 'notes': row.get('notes') or '',
            },
        )
        imported_transactions += int(created)
    return {'transactions': imported_transactions, 'bundle_id': payload['bundle_id']}


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

    safe_to_spend   = ti_real - te_real
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


# ─────────────────────────────────────────────
# PDF Export
# ─────────────────────────────────────────────

def _clp(amount: Decimal) -> str:
    """Format a Decimal as Chilean peso string: $1.234.567"""
    sign = '-' if amount < 0 else ''
    raw = f'{abs(amount):,.0f}'.replace(',', '.')
    return f'{sign}${raw}'


def generate_dashboard_pdf(period_id: int) -> bytes:
    """
    Build and return PDF bytes for the Dashboard projected budget report.

    Content:
      - Report header (title + generated timestamp)
      - Period info block (name, date range)
      - KPI summary row: Projected Income / Projected Expenses / Net Projection
      - Budget table: Category | Group | Type | Projected Amount
        - Income rows first, then Expense rows
        - Section subtotals, grand net at the bottom
    """
    from io import BytesIO
    from datetime import datetime as dt

    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import (
        HRFlowable, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle,
    )

    # ── Palette (matches Tailwind dark theme values printed on white paper) ──
    C_DARK       = colors.HexColor('#18181B')   # zinc-900
    C_MID        = colors.HexColor('#3F3F46')   # zinc-700
    C_LIGHT      = colors.HexColor('#A1A1AA')   # zinc-400
    C_EMERALD    = colors.HexColor('#10B981')   # emerald-500
    C_RED        = colors.HexColor('#EF4444')   # red-500
    C_EMERALD_BG = colors.HexColor('#D1FAE5')   # emerald-100
    C_RED_BG     = colors.HexColor('#FEE2E2')   # red-100
    C_HEADER_BG  = colors.HexColor('#F4F4F5')   # zinc-100
    C_ROW_ALT    = colors.HexColor('#FAFAFA')   # near-white

    # ── Data fetch ────────────────────────────────────────────────────────────
    stats = calculate_safe_to_spend(period_id)
    period: Period = stats['period']

    items = (
        BudgetItem.objects.filter(period=period)
        .select_related('category')
        .order_by('type', 'category__group', 'category__name')
    )

    # ── Document setup ────────────────────────────────────────────────────────
    buffer = BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize        = A4,
        leftMargin      = 2 * cm,
        rightMargin     = 2 * cm,
        topMargin       = 2 * cm,
        bottomMargin    = 2 * cm,
        title           = f'Budget — {period.name}',
        author          = 'Money Manager',
    )

    styles = getSampleStyleSheet()

    s_title = ParagraphStyle(
        'MMTitle',
        fontName  = 'Helvetica-Bold',
        fontSize  = 18,
        textColor = C_DARK,
        spaceAfter = 4,
    )
    s_subtitle = ParagraphStyle(
        'MMSubtitle',
        fontName  = 'Helvetica',
        fontSize  = 10,
        textColor = C_LIGHT,
        spaceAfter = 2,
    )
    s_section = ParagraphStyle(
        'MMSection',
        fontName   = 'Helvetica-Bold',
        fontSize   = 9,
        textColor  = C_MID,
        spaceBefore = 14,
        spaceAfter  = 4,
        leading    = 12,
    )
    s_label = ParagraphStyle(
        'MMLabel',
        fontName  = 'Helvetica',
        fontSize  = 7,
        textColor = C_LIGHT,
    )
    s_kpi = ParagraphStyle(
        'MMKpi',
        fontName  = 'Helvetica-Bold',
        fontSize  = 14,
        leading   = 16,
    )

    story = []

    # ── Header ────────────────────────────────────────────────────────────────
    story.append(Paragraph('Money Manager', s_title))
    story.append(Paragraph('Projected Budget Report', s_subtitle))
    story.append(Paragraph(
        f'Generated {dt.now().strftime("%d %b %Y, %H:%M")}',
        s_subtitle,
    ))
    story.append(HRFlowable(width='100%', thickness=1, color=C_MID, spaceAfter=10))

    # ── Period block ──────────────────────────────────────────────────────────
    story.append(Paragraph('PERIOD', s_section))
    period_data = [
        [
            Paragraph(period.name, ParagraphStyle('pn', fontName='Helvetica-Bold', fontSize=13, textColor=C_DARK)),
            Paragraph(
                f'{period.start_date.strftime("%d %b %Y")} &rarr; {period.end_date.strftime("%d %b %Y")}',
                ParagraphStyle('pd', fontName='Helvetica', fontSize=10, textColor=C_LIGHT, alignment=2),
            ),
        ]
    ]
    period_tbl = Table(period_data, colWidths=['60%', '40%'])
    period_tbl.setStyle(TableStyle([
        ('VALIGN',      (0, 0), (-1, -1), 'MIDDLE'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ('TOPPADDING',    (0, 0), (-1, -1), 6),
    ]))
    story.append(period_tbl)
    story.append(Spacer(1, 6))

    # ── KPI summary cards ─────────────────────────────────────────────────────
    proj_income  = stats['total_income_projected']
    proj_expense = stats['total_expense_projected']
    net          = stats['projection']
    net_color    = C_EMERALD if net >= 0 else C_RED
    net_bg       = C_EMERALD_BG if net >= 0 else C_RED_BG

    kpi_data = [
        [
            Paragraph('PROJECTED INCOME', s_label),
            Paragraph('PROJECTED EXPENSES', s_label),
            Paragraph('NET PROJECTION', s_label),
        ],
        [
            Paragraph(_clp(proj_income),  ParagraphStyle('ki', fontName='Helvetica-Bold', fontSize=14, textColor=C_EMERALD)),
            Paragraph(_clp(proj_expense), ParagraphStyle('ke', fontName='Helvetica-Bold', fontSize=14, textColor=C_RED)),
            Paragraph(_clp(net),          ParagraphStyle('kn', fontName='Helvetica-Bold', fontSize=14, textColor=net_color)),
        ],
    ]
    kpi_tbl = Table(kpi_data, colWidths=['33%', '33%', '34%'])
    kpi_tbl.setStyle(TableStyle([
        ('BACKGROUND',    (0, 0), (1, -1), C_HEADER_BG),
        ('BACKGROUND',    (2, 0), (2, -1), net_bg),
        ('TOPPADDING',    (0, 0), (-1, -1), 10),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 10),
        ('LEFTPADDING',   (0, 0), (-1, -1), 12),
        ('RIGHTPADDING',  (0, 0), (-1, -1), 12),
        ('VALIGN',        (0, 0), (-1, -1), 'TOP'),
        ('LINEAFTER',     (0, 0), (1, -1), 0.5, C_MID),
        ('ROUNDEDCORNERS', [4]),
    ]))
    story.append(kpi_tbl)
    story.append(Spacer(1, 16))

    # ── Budget table ──────────────────────────────────────────────────────────
    story.append(HRFlowable(width='100%', thickness=0.5, color=C_MID, spaceAfter=8))
    story.append(Paragraph('PROJECTED BUDGET', s_section))

    col_widths = ['34%', '28%', '10%', '28%']

    header_style = ParagraphStyle(
        'TH', fontName='Helvetica-Bold', fontSize=8, textColor=C_DARK,
    )
    cell_style = ParagraphStyle(
        'TD', fontName='Helvetica', fontSize=9, textColor=C_DARK,
    )
    amount_style = ParagraphStyle(
        'TA', fontName='Helvetica-Bold', fontSize=9, textColor=C_DARK, alignment=2,
    )
    subtotal_style = ParagraphStyle(
        'TS', fontName='Helvetica-Bold', fontSize=9, textColor=C_DARK, alignment=2,
    )
    group_style = ParagraphStyle(
        'TG', fontName='Helvetica', fontSize=8, textColor=C_LIGHT,
    )

    table_rows = [[
        Paragraph('Category',  header_style),
        Paragraph('Group',     header_style),
        Paragraph('Type',      header_style),
        Paragraph('Projected', ParagraphStyle('THA', fontName='Helvetica-Bold', fontSize=8, textColor=C_DARK, alignment=2)),
    ]]
    table_style_cmds = [
        ('BACKGROUND',    (0, 0), (-1, 0), C_HEADER_BG),
        ('TOPPADDING',    (0, 0), (-1, 0), 7),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 7),
        ('LEFTPADDING',   (0, 0), (-1, -1), 8),
        ('RIGHTPADDING',  (0, 0), (-1, -1), 8),
        ('LINEBELOW',     (0, 0), (-1, 0), 0.75, C_MID),
        ('VALIGN',        (0, 0), (-1, -1), 'MIDDLE'),
    ]

    income_items  = [i for i in items if i.type == 'IN']
    expense_items = [i for i in items if i.type == 'OUT']

    def add_section(section_items, label: str, type_color):
        if not section_items:
            return
        # Section label row
        row_idx = len(table_rows)
        table_rows.append([
            Paragraph(label, ParagraphStyle('SL', fontName='Helvetica-Bold', fontSize=8,
                                            textColor=type_color, spaceBefore=4)),
            '', '', '',
        ])
        table_style_cmds.append(('SPAN',       (0, row_idx), (-1, row_idx)))
        table_style_cmds.append(('BACKGROUND', (0, row_idx), (-1, row_idx), C_ROW_ALT))
        table_style_cmds.append(('TOPPADDING',    (0, row_idx), (-1, row_idx), 8))
        table_style_cmds.append(('BOTTOMPADDING', (0, row_idx), (-1, row_idx), 4))

        subtotal = Decimal('0.00')
        for idx, item in enumerate(section_items):
            row_idx = len(table_rows)
            bg = colors.white if idx % 2 == 0 else C_ROW_ALT
            table_rows.append([
                Paragraph(item.category.name, cell_style),
                Paragraph(item.category.get_group_display(), group_style),
                Paragraph(item.type, ParagraphStyle(
                    'TT', fontName='Helvetica', fontSize=8, textColor=type_color,
                )),
                Paragraph(_clp(item.projected_amount), amount_style),
            ])
            table_style_cmds.append(('BACKGROUND', (0, row_idx), (-1, row_idx), bg))
            table_style_cmds.append(('TOPPADDING',    (0, row_idx), (-1, row_idx), 5))
            table_style_cmds.append(('BOTTOMPADDING', (0, row_idx), (-1, row_idx), 5))
            subtotal += item.projected_amount

        # Subtotal row
        row_idx = len(table_rows)
        table_rows.append([
            Paragraph(f'{label} Subtotal', ParagraphStyle(
                'STL', fontName='Helvetica-Bold', fontSize=8, textColor=type_color,
            )),
            '', '',
            Paragraph(_clp(subtotal), ParagraphStyle(
                'STA', fontName='Helvetica-Bold', fontSize=9, textColor=type_color, alignment=2,
            )),
        ])
        table_style_cmds.append(('SPAN',       (0, row_idx), (2, row_idx)))
        table_style_cmds.append(('LINEABOVE',  (0, row_idx), (-1, row_idx), 0.5, C_MID))
        table_style_cmds.append(('TOPPADDING',    (0, row_idx), (-1, row_idx), 6))
        table_style_cmds.append(('BOTTOMPADDING', (0, row_idx), (-1, row_idx), 6))
        table_style_cmds.append(('BACKGROUND', (0, row_idx), (-1, row_idx), C_HEADER_BG))
        return subtotal

    income_total  = add_section(income_items,  'Income',   C_EMERALD) or Decimal('0.00')
    expense_total = add_section(expense_items, 'Expenses', C_RED)     or Decimal('0.00')

    # Net total row
    net_total = income_total - expense_total
    net_row_color = C_EMERALD if net_total >= 0 else C_RED
    row_idx = len(table_rows)
    table_rows.append([
        Paragraph('Net Projection', ParagraphStyle(
            'NL', fontName='Helvetica-Bold', fontSize=10, textColor=net_row_color,
        )),
        '', '',
        Paragraph(_clp(net_total), ParagraphStyle(
            'NA', fontName='Helvetica-Bold', fontSize=10, textColor=net_row_color, alignment=2,
        )),
    ])
    table_style_cmds.extend([
        ('SPAN',       (0, row_idx), (2, row_idx)),
        ('LINEABOVE',  (0, row_idx), (-1, row_idx), 1.0, C_DARK),
        ('TOPPADDING',    (0, row_idx), (-1, row_idx), 8),
        ('BOTTOMPADDING', (0, row_idx), (-1, row_idx), 8),
        ('BACKGROUND', (0, row_idx), (-1, row_idx),
         C_EMERALD_BG if net_total >= 0 else C_RED_BG),
    ])

    budget_tbl = Table(table_rows, colWidths=col_widths, repeatRows=1)
    budget_tbl.setStyle(TableStyle(table_style_cmds))
    story.append(budget_tbl)

    # ── Footer note ───────────────────────────────────────────────────────────
    story.append(Spacer(1, 16))
    story.append(HRFlowable(width='100%', thickness=0.5, color=C_LIGHT, spaceAfter=6))
    story.append(Paragraph(
        'This report contains projected amounts only. Real expenses are not included.',
        ParagraphStyle('FN', fontName='Helvetica-Oblique', fontSize=7, textColor=C_LIGHT),
    ))

    doc.build(story)
    return buffer.getvalue()

import csv
import io
import re
import logging
from datetime import datetime
from decimal import Decimal

from django.db import transaction
from django.utils import timezone
from django.db.models import Sum, F, Value
from django.db.models.functions import Coalesce

from finance.models import (
    BudgetMonth,
    Category,
    BudgetItem,
    Transaction,
    StagingTransaction,
)

logger = logging.getLogger(__name__)


def parse_scotiabank_statement(file_obj):
    """
    Parse a Scotiabank bank statement in semicolon-separated format.
    
    Expected format:
    - Semicolon-separated values
    - Header rows start with `;` or contain column names
    - Date format: DDMMYYYY
    - Amount format: padded string with `,` as decimal separator
    - Cargos: debits (expenses, type='OUT')
    - Abonos: credits (income, type='IN')
    
    Returns: List of StagingTransaction objects created
    """
    file_obj.seek(0)
    
    try:
        content = file_obj.read()
        if isinstance(content, bytes):
            content = content.decode('utf-8')
    except Exception as e:
        raise ValueError(f"Error reading file: {str(e)}")
    
    lines = content.split('\n')
    reader = csv.DictReader(
        io.StringIO(content),
        fieldnames=['Fecha', 'Descripcion', 'NroDoc.', 'Cargos', 'Abonos', 'Saldo'],
        delimiter=';'
    )
    
    transactions_to_create = []
    
    for row in reader:
        if not row or not row.get('Fecha'):
            continue
        fecha = row.get('Fecha', '').strip()
        descripcion = row.get('Descripcion', '').strip()
        cargos = row.get('Cargos', '').strip()
        abonos = row.get('Abonos', '').strip()
        
        if not fecha or not descripcion:
            continue
        
        if fecha.startswith(';') or 'Fecha' in fecha or 'Descripcion' in descripcion:
            continue
        
        try:
            date_obj = datetime.strptime(fecha, '%d%m%Y').date()
        except (ValueError, TypeError):
            continue
        
        transaction_type = None
        amount = None
        
        if cargos and cargos != '':
            try:
                cargo_str = cargos.replace(',', '.').strip()
                cargo_amount = Decimal(cargo_str)
                if cargo_amount > 0:
                    transaction_type = 'OUT'
                    amount = cargo_amount
            except:
                pass
        
        if not transaction_type and abonos and abonos != '':
            try:
                abono_str = abonos.replace(',', '.').strip()
                abono_amount = Decimal(abono_str)
                if abono_amount > 0:
                    transaction_type = 'IN'
                    amount = abono_amount
            except:
                pass
        
        if transaction_type and amount:
            transactions_to_create.append(
                StagingTransaction(
                    original_date=date_obj,
                    description=descripcion,
                    amount=amount,
                    type=transaction_type,
                    is_processed=False,
                    assigned_category=None
                )
            )
    
    created_transactions = StagingTransaction.objects.bulk_create(transactions_to_create)
    return created_transactions


def process_staging_batch(staging_ids_with_categories):
    """
    Process a batch of StagingTransaction records and convert to final Transactions.
    
    Args:
        staging_ids_with_categories: List of dicts with keys 'staging_id' and 'category_id'
        
    Atomically:
    1. Fetch StagingTransaction
    2. Determine BudgetMonth from original_date
    3. Fetch or create BudgetItem
    4. Create Transaction
    5. Mark StagingTransaction as processed
    """
    if not staging_ids_with_categories:
        logger.warning("process_staging_batch called with empty list")
        return

    with transaction.atomic():
        logger.info(f"Processing batch of {len(staging_ids_with_categories)} items")
        for item in staging_ids_with_categories:
            staging_id = item.get('staging_id')
            category_id = item.get('category_id')
            
            try:
                staging = StagingTransaction.objects.get(id=staging_id)
            except StagingTransaction.DoesNotExist:
                logger.error(f"StagingTransaction {staging_id} not found")
                continue
            
            try:
                category = Category.objects.get(id=category_id)
            except Category.DoesNotExist:
                logger.warning(f"Category {category_id} not found for staging {staging_id}")
                category = None
            
            if category is None:
                try:
                    category = Category.objects.get(name='Unplanned/Extra')
                except Category.DoesNotExist:
                    category = Category.objects.create(
                        name='Unplanned/Extra',
                        group='LIFESTYLE'
                    )
            
            budget_month, created = BudgetMonth.objects.get_or_create(
                month_date=staging.original_date.replace(day=1)
            )
            
            budget_item, created = BudgetItem.objects.get_or_create(
                budget_month=budget_month,
                category=category,
                defaults={'type': staging.type, 'projected_amount': Decimal('0.00')}
            )
            
            Transaction.objects.create(
                budget_item=budget_item,
                date=staging.original_date,
                real_amount=staging.amount,
                description=staging.description,
                notes=None
            )
            
            staging.is_processed = True
            staging.assigned_category = category
            staging.save()


def log_transaction_service(date, amount, description, category_id, notes=None):
    """
    Standard service for logging a transaction against a budget item.
    
    Args:
        date: Transaction date
        amount: Transaction amount (Decimal)
        description: Transaction description
        category_id: Foreign key to Category
        notes: Optional transaction notes
        
    Returns: Created Transaction object
    """
    try:
        category = Category.objects.get(id=category_id)
    except Category.DoesNotExist:
        raise ValueError(f"Category with id {category_id} does not exist")
    
    transaction_date = date
    if isinstance(date, str):
        transaction_date = datetime.fromisoformat(date).date()
    
    budget_month, created = BudgetMonth.objects.get_or_create(
        month_date=transaction_date.replace(day=1)
    )
    
    determine_type = 'IN' if amount > 0 else 'OUT'
    
    budget_item, created = BudgetItem.objects.get_or_create(
        budget_month=budget_month,
        category=category,
        defaults={'type': determine_type, 'projected_amount': Decimal('0.00')}
    )
    
    new_transaction = Transaction.objects.create(
        budget_item=budget_item,
        date=transaction_date,
        real_amount=abs(amount),
        description=description,
        notes=notes
    )
    
    return new_transaction


def rollover_month_balance(source_month_id, target_month_id):
    """
    Rollover balance from one month to the next.
    
    Args:
        source_month_id: Source BudgetMonth id
        target_month_id: Target BudgetMonth id
    """
    try:
        source_month = BudgetMonth.objects.get(id=source_month_id)
        target_month = BudgetMonth.objects.get(id=target_month_id)
    except BudgetMonth.DoesNotExist:
        raise ValueError("Source or target month does not exist")
    
    source_items = BudgetItem.objects.filter(budget_month=source_month)
    
    for source_item in source_items:
        target_item, created = BudgetItem.objects.get_or_create(
            budget_month=target_month,
            category=source_item.category,
            defaults={'type': source_item.type, 'projected_amount': source_item.projected_amount}
        )
        
        if not created:
            target_item.projected_amount = source_item.projected_amount
            target_item.save()


def calculate_safe_to_spend(budget_month_id):
    """
    Calculate the safe-to-spend amount for a budget month.
    
    Safe-to-spend = Sum of all income - Sum of all expenses
    
    Args:
        budget_month_id: BudgetMonth id
        
    Returns: Decimal amount
    """
    try:
        budget_month = BudgetMonth.objects.get(id=budget_month_id)
    except BudgetMonth.DoesNotExist:
        return Decimal('0.00')
    
    income_items = BudgetItem.objects.filter(
        budget_month=budget_month,
        type='IN'
    ).aggregate(
        total=Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00')))
    )
    
    expense_items = BudgetItem.objects.filter(
        budget_month=budget_month,
        type='OUT'
    ).aggregate(
        total=Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00')))
    )
    
    total_income = income_items.get('total', Decimal('0.00'))
    total_expenses = expense_items.get('total', Decimal('0.00'))
    
    safe_to_spend = total_income - total_expenses
    
    return safe_to_spend

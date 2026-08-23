from datetime import date
from decimal import Decimal
from io import StringIO
from pathlib import Path
import json

from django.test import TestCase
from django.urls import reverse

from finance.models import Account, Category, InstallmentObligation, MerchantRule, Period, RecurringPlan, StagingCCTransaction, StagingTransaction, Transaction, TransactionSplit
from finance.services import (
    _get_or_create_budget_item,
    build_cash_flow_forecast,
    create_transaction_splits_service,
    get_shared_expenses_summary,
    get_budget_velocity_alerts,
    process_cc_staging_batch,
    process_staging_batch,
    parse_scotiabank_statement,
    reverse_transaction_service,
    suggest_category,
)
from finance.templatetags.finance_format import clp


class LedgerServiceTests(TestCase):
    def setUp(self):
        self.period = Period.objects.create(
            name='January 2026', start_date=date(2026, 1, 1), end_date=date(2026, 1, 31)
        )
        self.category = Category.objects.create(name='Food', group='Alimentación')

    def test_chilean_money_formatter_uses_dots_and_no_float_rounding(self):
        self.assertEqual(clp(Decimal('1234567.49')), '$1.234.567')
        self.assertEqual(clp(Decimal('-2300')), '-$2.300')

    def test_budget_items_keep_income_and_expense_separate(self):
        income = _get_or_create_budget_item(self.period, self.category, 'IN')
        expense = _get_or_create_budget_item(self.period, self.category, 'OUT')
        self.assertNotEqual(income.pk, expense.pk)
        self.assertEqual({income.type, expense.type}, {'IN', 'OUT'})

    def test_staged_row_has_immutable_ledger_provenance(self):
        staged = StagingTransaction.objects.create(
            original_date=date(2026, 1, 10), description='Grocery', amount=Decimal('1000'), type='OUT'
        )
        self.assertEqual(process_staging_batch([{'staging_id': staged.pk, 'category_id': self.category.pk}]), 1)
        transaction = Transaction.objects.get()
        self.assertEqual(transaction.source_staging_transaction_id, staged.pk)
        self.assertEqual(transaction.source_fingerprint, f'bank-staging:{staged.pk}')

    def test_reversal_preserves_original_and_offsets_direction(self):
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        original = Transaction.objects.create(
            budget_item=item, date=date(2026, 1, 10), real_amount=Decimal('1000'), description='Grocery'
        )
        reversal = reverse_transaction_service(original.pk)
        self.assertEqual(reversal.reversal_of_id, original.pk)
        self.assertEqual(reversal.budget_item.type, 'IN')

    def test_merchant_rules_only_suggest_matching_direction(self):
        MerchantRule.objects.create(description_pattern='market', category=self.category, transaction_type='OUT')
        self.assertEqual(suggest_category('Market purchase', 'OUT'), self.category)
        self.assertIsNone(suggest_category('Market refund', 'IN'))

    def test_credit_card_installment_creates_future_obligation(self):
        staging = StagingCCTransaction.objects.create(
            original_date=date(2026, 1, 15), description='Laptop', amount=Decimal('100.00'),
            type='OUT', installment_current=1, installment_total=3,
            installment_value=Decimal('100.00'),
        )
        self.assertEqual(process_cc_staging_batch([{'staging_id': staging.pk, 'category_id': self.category.pk}]), 1)
        obligation = InstallmentObligation.objects.get()
        self.assertEqual(obligation.remaining_installments, 2)
        self.assertEqual(obligation.remaining_amount, Decimal('200.00'))
        self.assertEqual(obligation.next_due_date, date(2026, 2, 15))

    def test_sanitized_statement_fixture_preserves_signed_balances_and_batch_counts(self):
        fixture_dir = Path(__file__).parent / 'fixtures'
        expected = json.loads((fixture_dir / 'scotiabank_sanitized.expected.json').read_text())
        result = parse_scotiabank_statement(StringIO((fixture_dir / 'scotiabank_sanitized.dat').read_text()), 'fixture.dat')
        self.assertEqual(result['count'], expected['transactions'])
        self.assertEqual(result['skipped'], expected['skipped'])
        staged = list(StagingTransaction.objects.filter(batch_id=result['batch_id']).order_by('original_date'))
        self.assertEqual(str(staged[0].balance), expected['first']['balance'])
        self.assertEqual(staged[1].type, expected['second']['type'])

    def test_malformed_statement_rows_are_skipped_without_creating_ledger_entries(self):
        result = parse_scotiabank_statement(StringIO('Fecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo\nnot-a-date;broken\n'), 'bad.dat')
        self.assertEqual(result['count'], 0)
        self.assertEqual(result['skipped'], 1)
        self.assertEqual(Transaction.objects.count(), 0)

    def test_cash_flow_forecast_calculates_monthly_inflows_and_outflows(self):
        acc = Account.objects.create(name='Checking', opening_balance=Decimal('1000.00'), is_active=True)
        RecurringPlan.objects.create(
            name='Salary', category=self.category, account=acc,
            transaction_type='IN', amount=Decimal('5000.00'), frequency='MONTHLY',
            next_date=date(2026, 1, 1), description='Monthly Salary'
        )
        RecurringPlan.objects.create(
            name='Rent', category=self.category, account=acc,
            transaction_type='OUT', amount=Decimal('2000.00'), frequency='MONTHLY',
            next_date=date(2026, 1, 1), description='Apartment Rent'
        )

        forecast = build_cash_flow_forecast(months=3, start_date=date(2026, 1, 1))
        self.assertEqual(forecast['starting_total_balance'], '1000.00')
        self.assertEqual(len(forecast['monthly_forecasts']), 3)
        m1 = forecast['monthly_forecasts'][0]
        self.assertEqual(m1['inflows'], '5000.00')
        self.assertEqual(m1['outflows'], '2000.00')
        self.assertEqual(m1['net_flow'], '3000.00')
        self.assertEqual(m1['ending_balance'], '4000.00')
        self.assertFalse(m1['is_shortfall'])

    def test_cash_flow_forecast_view_returns_200(self):
        response = self.client.get(reverse('finance:cash_flow_forecast'))
        self.assertEqual(response.status_code, 200)
        self.assertIn('forecast', response.context)

    def test_transaction_splits_service_creates_splits_when_totals_match(self):
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        tx = Transaction.objects.create(
            budget_item=item, date=date(2026, 1, 10), real_amount=Decimal('100.00'), description='Supermarket'
        )
        cat2 = Category.objects.create(name='Home', group='Hogar')
        splits_data = [
            {'category_id': self.category.pk, 'amount': Decimal('60.00'), 'description': 'Groceries', 'shared_with': 'Juan', 'is_reimbursable': True},
            {'category_id': cat2.pk, 'amount': Decimal('40.00'), 'description': 'Cleaning Supplies', 'shared_with': 'Juan', 'is_reimbursable': False},
        ]
        created = create_transaction_splits_service(tx.pk, splits_data)
        self.assertEqual(len(created), 2)
        self.assertEqual(tx.splits.count(), 2)

    def test_transaction_splits_service_raises_error_when_totals_mismatch(self):
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        tx = Transaction.objects.create(
            budget_item=item, date=date(2026, 1, 10), real_amount=Decimal('100.00'), description='Supermarket'
        )
        splits_data = [
            {'category_id': self.category.pk, 'amount': Decimal('50.00'), 'description': 'Part 1'},
        ]
        with self.assertRaises(ValueError):
            create_transaction_splits_service(tx.pk, splits_data)

    def test_shared_expenses_summary_aggregates_reimbursable_and_shared_items(self):
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        tx = Transaction.objects.create(
            budget_item=item, date=date(2026, 1, 10), real_amount=Decimal('100.00'), description='Dinner'
        )
        create_transaction_splits_service(tx.pk, [
            {'category_id': self.category.pk, 'amount': Decimal('50.00'), 'description': 'My share', 'shared_with': 'Alice', 'is_reimbursable': False},
            {'category_id': self.category.pk, 'amount': Decimal('50.00'), 'description': 'Alice share', 'shared_with': 'Alice', 'is_reimbursable': True},
        ])
        summary = get_shared_expenses_summary()
        self.assertEqual(summary['total_shared_amount'], '100.00')
        self.assertEqual(summary['total_reimbursable_amount'], '50.00')
        self.assertEqual(len(summary['persons']), 1)
        self.assertEqual(summary['persons'][0]['person'], 'Alice')

    def test_shared_expense_list_view_returns_200(self):
        response = self.client.get(reverse('finance:shared_expense_list'))
        self.assertEqual(response.status_code, 200)
        self.assertIn('summary', response.context)

    def test_get_budget_velocity_alerts_detects_overbudget_items(self):
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        item.projected_amount = Decimal('100.00')
        item.save()

        Transaction.objects.create(
            budget_item=item, date=date(2026, 1, 10), real_amount=Decimal('150.00'), description='Overbudget expense'
        )

        res = get_budget_velocity_alerts(self.period.pk)
        self.assertEqual(res['critical_count'], 1)
        self.assertEqual(res['alerts'][0]['status'], 'CRITICAL')

    def test_budget_velocity_alerts_view_returns_200(self):
        response = self.client.get(reverse('finance:velocity_alerts'))
        self.assertEqual(response.status_code, 200)
        self.assertIn('velocity_data', response.context)




from datetime import date
from decimal import Decimal

from django.test import TestCase

from finance.models import Category, InstallmentObligation, MerchantRule, Period, StagingCCTransaction, StagingTransaction, Transaction
from finance.services import (
    _get_or_create_budget_item,
    process_cc_staging_batch,
    process_staging_batch,
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

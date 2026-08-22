from datetime import date
from decimal import Decimal

from django.test import TestCase

from finance.models import Category, MerchantRule, Period, StagingTransaction, Transaction
from finance.services import (
    _get_or_create_budget_item,
    process_staging_batch,
    reverse_transaction_service,
    suggest_category,
)


class LedgerServiceTests(TestCase):
    def setUp(self):
        self.period = Period.objects.create(
            name='January 2026', start_date=date(2026, 1, 1), end_date=date(2026, 1, 31)
        )
        self.category = Category.objects.create(name='Food', group='Alimentación')

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

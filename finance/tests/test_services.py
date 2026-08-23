from datetime import date
from decimal import Decimal
from io import StringIO
from pathlib import Path
import json

from django.test import TestCase, override_settings
from django.urls import reverse

from finance.models import Account, Category, InstallmentObligation, MerchantRule, Period, RecurringPlan, StagingCCTransaction, StagingTransaction, Transaction, TransactionSplit
from finance.services import (
    _get_or_create_budget_item,
    build_cash_flow_forecast,
    create_transaction_splits_service,
    get_shared_expenses_summary,
    get_budget_velocity_alerts,
    get_staging_merchant_suggestions,
    process_cc_staging_batch,
    process_staging_batch,
    parse_scotiabank_statement,
    reverse_transaction_service,
    suggest_category,
    suggest_merchant_rule_candidates,
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

    def test_merchant_rule_suggestions_return_ranked_primary_and_no_suggestion_when_uncertain(self):
        category_home = Category.objects.create(name='Home', group='ViviendaHogar')
        MerchantRule.objects.create(description_pattern='mercado', category=self.category, transaction_type='OUT')
        MerchantRule.objects.create(description_pattern='condes', category=category_home, transaction_type='OUT')
        item = _get_or_create_budget_item(self.period, self.category, 'OUT')
        Transaction.objects.create(
            budget_item=item,
            date=date(2026, 1, 10),
            real_amount=Decimal('1500.00'),
            description='Mercado Las Condes',
        )
        result = suggest_merchant_rule_candidates('Mercado Las Condes compra', 'OUT')
        self.assertTrue(result['has_suggestion'])
        self.assertEqual(result['primary']['category_id'], self.category.pk)
        self.assertGreaterEqual(result['primary']['confidence'], 0.65)
        self.assertTrue(result['alternatives'])
        self.assertIn('rule match', result['primary']['reasons'][0].lower())

        no_suggestion = suggest_merchant_rule_candidates('random impossible merchant xyz', 'OUT', min_confidence=0.8)
        self.assertFalse(no_suggestion['has_suggestion'])
        self.assertIsNone(no_suggestion['primary'])
        self.assertEqual(no_suggestion['alternatives'], [])

        result_in = suggest_merchant_rule_candidates('Mercado Las Condes compra', 'IN')
        self.assertFalse(result_in['has_suggestion'])
        self.assertEqual(result_in['alternatives'], [])

    def test_staging_merchant_suggestions_return_reviewable_dtos(self):
        MerchantRule.objects.create(description_pattern='mercado', category=self.category, transaction_type='OUT')
        staging = StagingTransaction.objects.create(
            original_date=date(2026, 1, 11),
            description='Mercado Las Condes compra',
            amount=Decimal('100.00'),
            type='OUT',
        )
        suggestions = get_staging_merchant_suggestions(StagingTransaction.objects.filter(pk=staging.pk))
        self.assertIn(staging.pk, suggestions)
        self.assertTrue(suggestions[staging.pk]['has_suggestion'])
        self.assertEqual(suggestions[staging.pk]['primary']['category_id'], self.category.pk)
        self.assertIn('rule match', suggestions[staging.pk]['primary']['reasons'][0].lower())

    @override_settings(FEATURE_MERCHANT_SUGGESTIONS_ENABLED=False)
    def test_suggestions_respect_feature_flag(self):
        result = suggest_merchant_rule_candidates('Mercado', 'OUT')
        self.assertFalse(result['has_suggestion'])

    @override_settings(FEATURE_AUTO_RULE_CREATION_ENABLED=False)
    def test_accept_does_not_create_rule_when_disabled(self):
        from finance.services import accept_staging_suggestion

        staging = StagingTransaction.objects.create(
            original_date=date(2026, 1, 15), type='OUT',
            description='Disabled Rule Merchant', amount=Decimal('50.00')
        )
        result = accept_staging_suggestion(
            staging_id=staging.pk, category_id=self.category.pk,
            create_rule=True, rule_pattern='Disabled Rule Merchant',
        )

        self.assertTrue(result['success'])
        self.assertFalse(MerchantRule.objects.filter(
            description_pattern='Disabled Rule Merchant'
        ).exists())
        self.assertIn('disabled', result['rule_warning'].lower())

    def test_accept_feedback_preserves_suggestion_evidence(self):
        from finance.models import SuggestionFeedback
        from finance.services import accept_staging_suggestion

        MerchantRule.objects.create(
            description_pattern='Evidence Merchant', category=self.category,
            transaction_type='OUT',
        )
        staging = StagingTransaction.objects.create(
            original_date=date(2026, 1, 15), type='OUT',
            description='Evidence Merchant purchase', amount=Decimal('50.00')
        )
        accept_staging_suggestion(
            staging_id=staging.pk, category_id=self.category.pk,
        )

        feedback = SuggestionFeedback.objects.get(
            staging_transaction=staging, event='ACCEPTED'
        )
        self.assertEqual(feedback.suggested_category_id, self.category.pk)
        self.assertGreater(feedback.suggested_confidence, 0)
        self.assertEqual(feedback.suggested_source, 'merchant_rule')

    def test_staging_review_views_expose_merchant_suggestions_for_display(self):
        MerchantRule.objects.create(description_pattern='mercado', category=self.category, transaction_type='OUT')
        staging = StagingTransaction.objects.create(
            original_date=date(2026, 1, 11),
            description='Mercado Las Condes compra',
            amount=Decimal('100.00'),
            type='OUT',
        )

        response = self.client.get(reverse('finance:staging_review'))
        self.assertEqual(response.status_code, 200)
        self.assertIn(staging.pk, response.context['merchant_suggestions'])
        self.assertTrue(response.context['merchant_suggestions'][staging.pk]['has_suggestion'])

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

    # ─────────────────────────────────────────────────────────────
    # 4.2: Feedback/Provenance Tracking (4.6, 4.7, 4.9)
    # ─────────────────────────────────────────────────────────────

    def test_record_suggestion_feedback_creates_feedback_entry(self):
        """Test that suggestion feedback is recorded for monitoring."""
        from finance.services import record_suggestion_feedback
        from finance.models import SuggestionFeedback

        staging = StagingTransaction.objects.create(
            batch=None, original_date=date(2026, 1, 15), type='OUT',
            description='Test Merchant', amount=Decimal('50.00')
        )

        record_suggestion_feedback(
            'SHOWN',
            staging_id=staging.pk,
            suggested_category_id=self.category.pk,
            confidence=0.85,
            source='merchant_rule',
        )

        feedback = SuggestionFeedback.objects.filter(
            staging_transaction=staging, event='SHOWN'
        ).first()
        self.assertIsNotNone(feedback)
        self.assertEqual(feedback.suggested_confidence, 0.85)
        self.assertEqual(feedback.suggested_source, 'merchant_rule')

    def test_accept_staging_suggestion_assigns_category(self):
        """Test that accepting a suggestion assigns the category to staging row."""
        from finance.services import accept_staging_suggestion

        staging = StagingTransaction.objects.create(
            batch=None, original_date=date(2026, 1, 15), type='OUT',
            description='Supermarket Purchase', amount=Decimal('100.00')
        )
        self.assertIsNone(staging.assigned_category)

        result = accept_staging_suggestion(
            staging_id=staging.pk,
            category_id=self.category.pk,
        )

        self.assertTrue(result['success'])
        staging.refresh_from_db()
        self.assertEqual(staging.assigned_category_id, self.category.pk)

    def test_accept_staging_suggestion_with_rule_creation(self):
        """Test that accept + rule option creates a merchant rule."""
        from finance.services import accept_staging_suggestion

        staging = StagingTransaction.objects.create(
            batch=None, original_date=date(2026, 1, 15), type='OUT',
            description='Mercado Las Condes', amount=Decimal('150.00')
        )

        result = accept_staging_suggestion(
            staging_id=staging.pk,
            category_id=self.category.pk,
            create_rule=True,
            rule_pattern='Mercado Las Condes',
        )

        self.assertTrue(result['success'])
        self.assertTrue(result.get('rule_created', False))

        rule = MerchantRule.objects.filter(
            description_pattern='Mercado Las Condes'
        ).first()
        self.assertIsNotNone(rule)
        self.assertEqual(rule.category_id, self.category.pk)

    def test_dismiss_staging_suggestion_records_event(self):
        """Test that dismissing a suggestion records the event."""
        from finance.services import dismiss_staging_suggestion
        from finance.models import SuggestionFeedback

        staging = StagingTransaction.objects.create(
            batch=None, original_date=date(2026, 1, 15), type='OUT',
            description='Test Merchant', amount=Decimal('50.00')
        )

        result = dismiss_staging_suggestion(staging_id=staging.pk)

        self.assertTrue(result['success'])
        feedback = SuggestionFeedback.objects.filter(
            staging_transaction=staging, event='DISMISSED'
        ).first()
        self.assertIsNotNone(feedback)

    def test_create_merchant_rule_detects_exact_match_conflict(self):
        """Test that creating a rule detects exact-match conflicts."""
        from finance.services import create_merchant_rule_from_suggestion

        existing = MerchantRule.objects.create(
            description_pattern='Mercado Las Condes',
            category=self.category,
            transaction_type='OUT',
        )

        result = create_merchant_rule_from_suggestion(
            'Mercado Las Condes',
            self.category.pk,
            'OUT',
            evidence_description='Test',
        )

        self.assertFalse(result['success'])
        self.assertIn('already exists', result['message'])

    def test_create_merchant_rule_allows_different_category_overlap(self):
        """Test that creating a rule with a different category warns but may succeed if not exact."""
        from finance.services import create_merchant_rule_from_suggestion

        other_category = Category.objects.create(name='Other', group='Gastos')
        existing = MerchantRule.objects.create(
            description_pattern='Mercado',
            category=other_category,
            transaction_type='OUT',
        )

        result = create_merchant_rule_from_suggestion(
            'Mercado Las Condes',
            self.category.pk,
            'OUT',
            evidence_description='Longer pattern',
        )

        # Should still warn about overlaps
        self.assertGreater(len(result['conflicts']), 0)

    def test_create_merchant_rule_with_provenance(self):
        """Test that created rules have provenance tracking."""
        from finance.services import create_merchant_rule_from_suggestion
        from finance.models import MerchantRuleProvenance

        result = create_merchant_rule_from_suggestion(
            'New Test Merchant',
            self.category.pk,
            'OUT',
            evidence_description='Created from accepted suggestion',
            evidence_count=5,
        )

        self.assertTrue(result['success'])
        rule = MerchantRule.objects.get(pk=result['rule_id'])
        provenance = MerchantRuleProvenance.objects.filter(rule=rule).first()
        self.assertIsNotNone(provenance)
        self.assertEqual(provenance.source, 'SUGGESTION')
        self.assertEqual(provenance.evidence_transactions, 5)

    def test_suggestion_feedback_for_cc_transactions(self):
        """Test that feedback can be recorded for CC staging transactions."""
        from finance.services import record_suggestion_feedback
        from finance.models import SuggestionFeedback

        cc_staging = StagingCCTransaction.objects.create(
            batch=None, original_date=date(2026, 1, 15), type='OUT',
            description='CC Purchase', amount=Decimal('75.00')
        )

        record_suggestion_feedback(
            'ACCEPTED',
            staging_cc_id=cc_staging.pk,
            user_action_category_id=self.category.pk,
        )

        feedback = SuggestionFeedback.objects.filter(
            staging_cc_transaction=cc_staging, event='ACCEPTED'
        ).first()
        self.assertIsNotNone(feedback)





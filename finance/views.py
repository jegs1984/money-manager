from decimal import Decimal

from django.contrib import messages
from django.db.models import F, Sum, Value, Case, When
from django.db.models.functions import Coalesce
from django.http import HttpResponse, JsonResponse
from django.shortcuts import get_object_or_404, redirect
from django.urls import reverse_lazy
from django.views.decorators.http import require_POST
from django.views.generic import (
    CreateView, DeleteView, FormView, ListView, TemplateView, UpdateView, View,
)

from .forms import (
    AccountForm, BudgetItemForm, CategoryForm, PeriodForm, ReconciliationForm, TransferForm,
    StagingReviewFormset, StatementUploadForm, TransactionForm,
    CCStatementUploadForm, StagingCCReviewFormset,
)
from .models import Account, BudgetItem, Category, ImportBatch, Period, StagingCCTransaction, StagingTransaction, Transaction
from .services import (
    calculate_safe_to_spend, generate_dashboard_pdf, get_duplicate_staging_ids,
    parse_scotiabank_statement, process_staging_batch,
    parse_scotiabank_cc_statement, process_cc_staging_batch,
    calculate_account_balance, close_period_service, reconcile_account_service,
    record_transfer_service, reverse_transaction_service,
)


# ─────────────────────────────────────────────
# Dashboard
# ─────────────────────────────────────────────

class DashboardView(TemplateView):
    template_name = 'finance/dashboard.html'

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        period_id    = self.request.GET.get('period')
        all_periods  = Period.objects.order_by('-start_date')

        if period_id:
            active_period = Period.objects.filter(pk=period_id).first()
        else:
            active_period = all_periods.filter(is_active=True).first() or all_periods.first()

        if active_period:
            stats = calculate_safe_to_spend(active_period.id)

            total_real_expr = Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00')))

            items = (
                BudgetItem.objects.filter(period=active_period)
                .select_related('category')
                .annotate(
                    total_real=Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00'))),
                    diff=Case(
                        When(type='IN', then=total_real_expr - F('projected_amount')),
                        When(type='OUT', then=F('projected_amount') - total_real_expr),
                        default=Value(Decimal('0.00'))
                    )
                )
                .order_by('type', 'category__group', 'category__name')
            )
            ctx.update(stats)
            ctx['budget_items'] = items

        ctx['active_period'] = active_period
        ctx['all_periods']   = all_periods
        return ctx


# ─────────────────────────────────────────────
# Dashboard PDF Export
# ─────────────────────────────────────────────

class DashboardPDFView(View):
    def get(self, request, *args, **kwargs):
        period_id = request.GET.get('period')

        if period_id:
            period = Period.objects.filter(pk=period_id).first()
        else:
            period = (
                Period.objects.filter(is_active=True).first()
                or Period.objects.order_by('-start_date').first()
            )

        if not period:
            messages.error(request, 'No period found to export.')
            return redirect('finance:dashboard')

        try:
            pdf_bytes = generate_dashboard_pdf(period.id)
        except Exception as exc:
            messages.error(request, f'PDF generation failed: {exc}')
            return redirect('finance:dashboard')

        filename = f'budget_{period.name.replace(" ", "_")}.pdf'
        response = HttpResponse(pdf_bytes, content_type='application/pdf')
        response['Content-Disposition'] = f'attachment; filename="{filename}"'
        return response


# ─────────────────────────────────────────────
# Group Dashboard
# ─────────────────────────────────────────────

class GroupDashboardView(TemplateView):
    template_name = 'finance/group_dashboard.html'

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        period_id   = self.request.GET.get('period')
        all_periods = Period.objects.order_by('-start_date')

        if period_id:
            active_period = Period.objects.filter(pk=period_id).first()
        else:
            active_period = all_periods.filter(is_active=True).first() or all_periods.first()

        if active_period:
            stats = calculate_safe_to_spend(active_period.id)

            total_real_expr = Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00')))

            items = (
                BudgetItem.objects.filter(period=active_period)
                .select_related('category')
                .annotate(
                    total_real=Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00'))),
                    diff=Case(
                        When(type='IN',  then=total_real_expr - F('projected_amount')),
                        When(type='OUT', then=F('projected_amount') - total_real_expr),
                        default=Value(Decimal('0.00'))
                    )
                )
                .order_by('type', 'category__group', 'category__name')
            )

            from collections import defaultdict
            groups = defaultdict(lambda: {
                'group': '',
                'type': '',
                'projected': Decimal('0.00'),
                'actual':    Decimal('0.00'),
                'diff':      Decimal('0.00'),
                'items':     [],
            })

            for item in items:
                key = (item.type, item.category.group)
                g   = groups[key]
                g['group']     = item.category.group
                g['type']      = item.type
                g['projected'] += item.projected_amount
                g['actual']    += item.total_real
                g['diff']      += item.diff
                g['items'].append(item)

            sorted_groups = sorted(groups.values(), key=lambda g: (0 if g['type'] == 'IN' else 1, g['group']))

            ctx.update(stats)
            ctx['groups'] = sorted_groups

        ctx['active_period'] = active_period
        ctx['all_periods']   = all_periods
        return ctx


# ─────────────────────────────────────────────
# Period CRUD
# ─────────────────────────────────────────────

class PeriodListView(ListView):
    model               = Period
    template_name       = 'finance/period_list.html'
    context_object_name = 'periods'


class PeriodCreateView(CreateView):
    model         = Period
    form_class    = PeriodForm
    template_name = 'finance/period_form.html'
    success_url   = reverse_lazy('finance:period_list')


class PeriodUpdateView(UpdateView):
    model         = Period
    form_class    = PeriodForm
    template_name = 'finance/period_form.html'
    success_url   = reverse_lazy('finance:period_list')


class PeriodDeleteView(DeleteView):
    model         = Period
    template_name = 'finance/confirm_delete.html'
    success_url   = reverse_lazy('finance:period_list')


class PeriodCloseView(View):
    def post(self, request, pk, *args, **kwargs):
        try:
            close_period_service(pk)
        except (Period.DoesNotExist, ValueError) as exc:
            messages.error(request, str(exc))
        else:
            messages.success(request, 'Period closed. Its ledger is now read-only.')
        return redirect('finance:period_list')


class PeriodDuplicateBudgetView(View):
    """Preview and confirm copying a period's budget skeleton."""
    def get(self, request, pk, *args, **kwargs):
        source = Period.objects.get(pk=pk)
        candidates = Period.objects.exclude(pk=pk).order_by('-start_date')
        return self.render_to_response({
            'source': source,
            'source_item_count': source.budget_items.count(),
            'candidate_periods': candidates,
        })

    template_name = 'finance/period_duplicate_budget.html'

    def render_to_response(self, context):
        from django.shortcuts import render
        return render(self.request, self.template_name, context)

    def post(self, request, pk, *args, **kwargs):
        from .services import duplicate_period_budget_items
        target_id = request.POST.get('target_period')
        if not target_id or not target_id.isdigit() or int(target_id) == pk:
            messages.error(request, 'Choose a different target period.')
            return redirect('finance:period_duplicate_budget', pk=pk)
        try:
            result = duplicate_period_budget_items(pk, int(target_id))
        except Period.DoesNotExist:
            messages.error(request, 'The selected period no longer exists.')
            return redirect('finance:period_list')
        messages.success(request, f"Copied {result['created']} budget item(s); {result['skipped']} already existed.")
        return redirect('finance:period_list')


# ─────────────────────────────────────────────
# Category CRUD
# ─────────────────────────────────────────────

class CategoryListView(ListView):
    model               = Category
    template_name       = 'finance/category_list.html'
    context_object_name = 'categories'


class CategoryCreateView(CreateView):
    model         = Category
    form_class    = CategoryForm
    template_name = 'finance/category_form.html'
    success_url   = reverse_lazy('finance:category_list')


class CategoryUpdateView(UpdateView):
    model         = Category
    form_class    = CategoryForm
    template_name = 'finance/category_form.html'
    success_url   = reverse_lazy('finance:category_list')


class CategoryDeleteView(DeleteView):
    model         = Category
    template_name = 'finance/confirm_delete.html'
    success_url   = reverse_lazy('finance:category_list')


# ─────────────────────────────────────────────
# Accounts, transfers, and reconciliation
# ─────────────────────────────────────────────

class AccountListView(ListView):
    model = Account
    template_name = 'finance/account_list.html'
    context_object_name = 'accounts'

    def get_context_data(self, **kwargs):
        context = super().get_context_data(**kwargs)
        context['accounts_with_balances'] = [
            (account, calculate_account_balance(account.pk))
            for account in context['accounts']
        ]
        return context


class AccountCreateView(CreateView):
    model = Account
    form_class = AccountForm
    template_name = 'finance/financial_form.html'
    success_url = reverse_lazy('finance:account_list')


class AccountUpdateView(UpdateView):
    model = Account
    form_class = AccountForm
    template_name = 'finance/financial_form.html'
    success_url = reverse_lazy('finance:account_list')


class TransferCreateView(FormView):
    template_name = 'finance/financial_form.html'
    form_class = TransferForm
    success_url = reverse_lazy('finance:account_list')

    def form_valid(self, form):
        try:
            record_transfer_service(
                form.cleaned_data['source_account'].pk,
                form.cleaned_data['destination_account'].pk,
                form.cleaned_data['date'],
                form.cleaned_data['amount'],
                form.cleaned_data['description'],
            )
        except ValueError as exc:
            form.add_error(None, str(exc))
            return self.form_invalid(form)
        messages.success(self.request, 'Transfer recorded without changing your budget totals.')
        return super().form_valid(form)


class ReconciliationCreateView(FormView):
    template_name = 'finance/financial_form.html'
    form_class = ReconciliationForm
    success_url = reverse_lazy('finance:account_list')

    def form_valid(self, form):
        reconciliation = reconcile_account_service(
            form.cleaned_data['account'].pk,
            form.cleaned_data['statement_date'],
            form.cleaned_data['statement_balance'],
            form.cleaned_data['notes'],
        )
        difference = reconciliation.statement_balance - reconciliation.calculated_balance
        messages.success(self.request, f'Reconciliation saved. Difference: ${difference:,.0f}.')
        return super().form_valid(form)


# ─────────────────────────────────────────────
# BudgetItem CRUD
# ─────────────────────────────────────────────

class BudgetItemCreateView(CreateView):
    model         = BudgetItem
    form_class    = BudgetItemForm
    template_name = 'finance/budgetitem_form.html'
    success_url   = reverse_lazy('finance:dashboard')


class BudgetItemUpdateView(UpdateView):
    model         = BudgetItem
    form_class    = BudgetItemForm
    template_name = 'finance/budgetitem_form.html'
    success_url   = reverse_lazy('finance:dashboard')


class BudgetItemDeleteView(DeleteView):
    model         = BudgetItem
    template_name = 'finance/confirm_delete.html'
    success_url   = reverse_lazy('finance:dashboard')


# ─────────────────────────────────────────────
# Transaction CRUD
# ─────────────────────────────────────────────

class TransactionListView(ListView):
    model               = Transaction
    template_name       = 'finance/transaction_list.html'
    context_object_name = 'transactions'
    paginate_by         = 50

    def get_queryset(self):
        return Transaction.objects.select_related('budget_item__category', 'budget_item__period')


class TransactionCreateView(CreateView):
    model         = Transaction
    form_class    = TransactionForm
    template_name = 'finance/transaction_form.html'
    success_url   = reverse_lazy('finance:dashboard')


class TransactionUpdateView(UpdateView):
    model         = Transaction
    form_class    = TransactionForm
    template_name = 'finance/transaction_form.html'
    success_url   = reverse_lazy('finance:dashboard')


class TransactionDeleteView(DeleteView):
    model         = Transaction
    template_name = 'finance/confirm_delete.html'
    success_url   = reverse_lazy('finance:dashboard')


class TransactionReverseView(View):
    def post(self, request, pk, *args, **kwargs):
        try:
            reverse_transaction_service(pk, request.POST.get('notes', ''))
        except (Transaction.DoesNotExist, ValueError) as exc:
            messages.error(request, str(exc))
        else:
            messages.success(request, 'A compensating reversal was added; the original transaction is preserved.')
        return redirect('finance:transaction_list')


# ─────────────────────────────────────────────
# Statement Upload
# ─────────────────────────────────────────────

class StatementUploadView(FormView):
    template_name = 'finance/statement_upload.html'
    form_class    = StatementUploadForm

    def form_valid(self, form):
        file_obj = form.cleaned_data['statement_file']
        result   = parse_scotiabank_statement(
            file_obj, source_filename=file_obj.name,
            import_again=form.cleaned_data['import_again'],
        )
        if result.get('already_imported'):
            messages.warning(self.request, 'This file was already imported. Tick “import again” to stage another copy.')
            return redirect('finance:staging_review')
        if result['count']:
            messages.success(
                self.request,
                f"{result['count']} transactions staged "
                f"(account {result['account_number']}, "
                f"{result['date_from']} → {result['date_to']}). "
                f"{result['skipped']} rows skipped.",
            )
        else:
            messages.warning(
                self.request,
                f"No transactions found. {result['skipped']} rows skipped. "
                "Check the file format.",
            )
        return redirect(f"{reverse_lazy('finance:staging_review')}?batch={result['batch_id']}")

    def form_invalid(self, form):
        messages.error(self.request, 'Invalid upload. Please select a valid statement file.')
        return super().form_invalid(form)


# ─────────────────────────────────────────────
# Staging Review (Debit)
# ─────────────────────────────────────────────

class StagingReviewView(TemplateView):
    template_name = 'finance/staging_review.html'

    def _qs(self):
        batch_id = self.request.GET.get('batch') or self.request.POST.get('batch')
        qs = StagingTransaction.objects.filter(is_processed=False)
        if batch_id and batch_id.isdigit():
            return qs.filter(batch_id=batch_id).order_by('original_date')
        batch = qs.exclude(batch__isnull=True).order_by('-batch__imported_at').values_list('batch_id', flat=True).first()
        return qs.filter(batch_id=batch).order_by('original_date') if batch else qs.filter(batch__isnull=True).order_by('original_date')

    def _active_period(self):
        return (
            Period.objects.filter(is_active=True).first()
            or Period.objects.order_by('-start_date').first()
        )

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        qs  = self._qs()
        period = self._active_period()
        duplicate_ids = get_duplicate_staging_ids(period, qs) if period else set()

        ctx['formset']       = StagingReviewFormset(queryset=qs)
        ctx['pending_count'] = qs.count()
        ctx['duplicate_ids'] = duplicate_ids
        ctx['active_period'] = period
        ctx['delete_url']    = 'finance:staging_delete'
        ctx['batch'] = qs.first().batch if qs.first() else None
        return ctx

    def post(self, request, *args, **kwargs):
        qs      = self._qs()
        formset = StagingReviewFormset(request.POST, queryset=qs)

        if formset.is_valid():
            remove_ids: set[int] = set()
            entries = []

            for f in formset:
                sid = f.instance.id
                dup_action = request.POST.get(f'dup_action_{sid}')

                if dup_action == 'remove':
                    remove_ids.add(sid)
                    continue

                cat = f.cleaned_data.get('assigned_category')
                if cat:
                    entries.append({'staging_id': sid, 'category_id': cat.id, 'batch_id': f.instance.batch_id})

            processed = process_staging_batch(entries, remove_ids=remove_ids)
            removed   = len(remove_ids)
            msg_parts = []
            if processed:
                msg_parts.append(f'{processed} transaction{"s" if processed != 1 else ""} committed to the ledger')
            if removed:
                msg_parts.append(f'{removed} duplicate{"s" if removed != 1 else ""} removed from staging')
            messages.success(request, '. '.join(msg_parts) + '.')
            return redirect('finance:staging_review')

        period = self._active_period()
        duplicate_ids = get_duplicate_staging_ids(period, qs) if period else set()
        ctx = self.get_context_data()
        ctx['formset']       = formset
        ctx['duplicate_ids'] = duplicate_ids
        return self.render_to_response(ctx)


# ─────────────────────────────────────────────
# Staging Delete (Debit)
# ─────────────────────────────────────────────

class StagingDeleteView(View):
    """
    POST /staging/delete/
    Body (form-encoded):
      ids   – one or more staging IDs to hard-delete (unprocessed only)
      all   – if present and truthy, delete ALL unprocessed staging rows

    Responds with JSON so the template can remove rows without a page reload,
    or redirects normally when JS is unavailable.
    """

    def post(self, request, *args, **kwargs):
        batch_id = request.GET.get('batch') or request.POST.get('batch')
        qs = StagingTransaction.objects.filter(is_processed=False)
        if batch_id and batch_id.isdigit():
            qs = qs.filter(batch_id=batch_id)
        delete_all = request.POST.get('all')
        if delete_all:
            deleted, _ = qs.delete()
            n = deleted
        else:
            raw_ids = request.POST.getlist('ids')
            ids = [int(i) for i in raw_ids if i.isdigit()]
            deleted, _ = qs.filter(id__in=ids).delete()
            n = deleted

        if request.headers.get('X-Requested-With') == 'XMLHttpRequest':
            return JsonResponse({'deleted': n})

        if n:
            messages.success(request, f'{n} staged transaction{"s" if n != 1 else ""} deleted.')
        return redirect('finance:staging_review')


# ─────────────────────────────────────────────
# Credit Card Upload
# ─────────────────────────────────────────────

class CCStatementUploadView(FormView):
    template_name = 'finance/cc_statement_upload.html'
    form_class    = CCStatementUploadForm

    def form_valid(self, form):
        file_obj = form.cleaned_data['statement_file']
        try:
            result = parse_scotiabank_cc_statement(
                file_obj, source_filename=file_obj.name,
                import_again=form.cleaned_data['import_again'],
            )
        except ValueError as e:
            messages.error(self.request, str(e))
            return self.form_invalid(form)

        if result.get('already_imported'):
            messages.warning(self.request, 'This file was already imported. Tick “import again” to stage another copy.')
            return redirect('finance:cc_staging_review')
        if result['count']:
            messages.success(
                self.request,
                f"{result['count']} CC transactions staged "
                f"({result['card_holder']} · {result['card_number']}). "
                f"{result['skipped']} rows skipped.",
            )
        else:
            messages.warning(
                self.request,
                f"No transactions found. {result['skipped']} rows skipped. "
                "Check the file format.",
            )
        return redirect(f"{reverse_lazy('finance:cc_staging_review')}?batch={result['batch_id']}")

    def form_invalid(self, form):
        messages.error(self.request, 'Invalid upload. Please select a valid .xls statement file.')
        return super().form_invalid(form)


# ─────────────────────────────────────────────
# Credit Card Staging Review
# ─────────────────────────────────────────────

class CCStagingReviewView(TemplateView):
    template_name = 'finance/cc_staging_review.html'

    def _qs(self):
        batch_id = self.request.GET.get('batch') or self.request.POST.get('batch')
        qs = StagingCCTransaction.objects.filter(is_processed=False)
        if batch_id and batch_id.isdigit():
            return qs.filter(batch_id=batch_id).order_by('original_date')
        batch = qs.exclude(batch__isnull=True).order_by('-batch__imported_at').values_list('batch_id', flat=True).first()
        return qs.filter(batch_id=batch).order_by('original_date') if batch else qs.filter(batch__isnull=True).order_by('original_date')

    def _active_period(self):
        return (
            Period.objects.filter(is_active=True).first()
            or Period.objects.order_by('-start_date').first()
        )

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        qs  = self._qs()
        period = self._active_period()
        duplicate_ids = get_duplicate_staging_ids(period, qs) if period else set()

        ctx['formset']       = StagingCCReviewFormset(queryset=qs)
        ctx['pending_count'] = qs.count()
        ctx['duplicate_ids'] = duplicate_ids
        ctx['active_period'] = period
        ctx['delete_url']    = 'finance:cc_staging_delete'
        ctx['batch'] = qs.first().batch if qs.first() else None
        first = qs.first()
        ctx['card_number'] = first.card_number if first else ''
        ctx['card_holder'] = first.card_holder if first else ''
        return ctx

    def post(self, request, *args, **kwargs):
        qs      = self._qs()
        formset = StagingCCReviewFormset(request.POST, queryset=qs)

        if formset.is_valid():
            remove_ids: set[int] = set()
            entries = []

            for f in formset:
                sid = f.instance.id
                dup_action = request.POST.get(f'dup_action_{sid}')

                if dup_action == 'remove':
                    remove_ids.add(sid)
                    continue

                cat = f.cleaned_data.get('assigned_category')
                if cat:
                    entries.append({'staging_id': sid, 'category_id': cat.id, 'batch_id': f.instance.batch_id})

            processed = process_cc_staging_batch(entries, remove_ids=remove_ids)
            removed   = len(remove_ids)
            msg_parts = []
            if processed:
                msg_parts.append(f'{processed} CC transaction{"s" if processed != 1 else ""} committed to the ledger')
            if removed:
                msg_parts.append(f'{removed} duplicate{"s" if removed != 1 else ""} removed from staging')
            messages.success(request, '. '.join(msg_parts) + '.')
            return redirect('finance:dashboard')

        period = self._active_period()
        duplicate_ids = get_duplicate_staging_ids(period, qs) if period else set()
        ctx = self.get_context_data()
        ctx['formset']       = formset
        ctx['duplicate_ids'] = duplicate_ids
        return self.render_to_response(ctx)


# ─────────────────────────────────────────────
# CC Staging Delete
# ─────────────────────────────────────────────

class CCStagingDeleteView(View):
    """
    POST /cc/staging/delete/
    Same contract as StagingDeleteView but operates on StagingCCTransaction.
    """

    def post(self, request, *args, **kwargs):
        batch_id = request.GET.get('batch') or request.POST.get('batch')
        qs = StagingCCTransaction.objects.filter(is_processed=False)
        if batch_id and batch_id.isdigit():
            qs = qs.filter(batch_id=batch_id)
        delete_all = request.POST.get('all')
        if delete_all:
            deleted, _ = qs.delete()
            n = deleted
        else:
            raw_ids = request.POST.getlist('ids')
            ids = [int(i) for i in raw_ids if i.isdigit()]
            deleted, _ = qs.filter(id__in=ids).delete()
            n = deleted

        if request.headers.get('X-Requested-With') == 'XMLHttpRequest':
            return JsonResponse({'deleted': n})

        if n:
            messages.success(request, f'{n} CC staged transaction{"s" if n != 1 else ""} deleted.')
        return redirect('finance:cc_staging_review')

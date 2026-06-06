from decimal import Decimal

from django.contrib import messages
from django.db.models import F, Sum, Value, Case, When
from django.db.models.functions import Coalesce
from django.shortcuts import redirect
from django.urls import reverse_lazy
from django.views.generic import (
    CreateView, DeleteView, FormView, ListView, TemplateView, UpdateView,
)

from .forms import (
    BudgetItemForm, CategoryForm, PeriodForm,
    StagingReviewFormset, StatementUploadForm, TransactionForm,
    CCStatementUploadForm, StagingCCReviewFormset,
)
from .models import BudgetItem, Category, Period, StagingCCTransaction, StagingTransaction, Transaction
from .services import (
    calculate_safe_to_spend, parse_scotiabank_statement, process_staging_batch,
    parse_scotiabank_cc_statement, process_cc_staging_batch,
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


# ─────────────────────────────────────────────
# Statement Upload
# ─────────────────────────────────────────────

class StatementUploadView(FormView):
    template_name = 'finance/statement_upload.html'
    form_class    = StatementUploadForm

    def form_valid(self, form):
        file_obj = form.cleaned_data['statement_file']
        result   = parse_scotiabank_statement(file_obj, source_filename=file_obj.name)
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
        return redirect('finance:staging_review')

    def form_invalid(self, form):
        messages.error(self.request, 'Invalid upload. Please select a valid statement file.')
        return super().form_invalid(form)


# ─────────────────────────────────────────────
# Staging Review
# ─────────────────────────────────────────────

class StagingReviewView(TemplateView):
    template_name = 'finance/staging_review.html'

    def _qs(self):
        return StagingTransaction.objects.filter(is_processed=False).order_by('original_date')

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        qs  = self._qs()
        ctx['formset']       = StagingReviewFormset(queryset=qs)
        ctx['pending_count'] = qs.count()
        return ctx

    def post(self, request, *args, **kwargs):
        qs      = self._qs()
        formset = StagingReviewFormset(request.POST, queryset=qs)

        if formset.is_valid():
            entries = [
                {'staging_id': f.instance.id, 'category_id': f.cleaned_data['assigned_category'].id}
                for f in formset
                if f.cleaned_data.get('assigned_category')
            ]
            processed = process_staging_batch(entries)
            messages.success(request, f'{processed} transactions committed to the ledger.')
            return redirect('finance:dashboard')

        ctx            = self.get_context_data()
        ctx['formset'] = formset
        return self.render_to_response(ctx)


# ─────────────────────────────────────────────
# Credit Card Upload
# ─────────────────────────────────────────────

class CCStatementUploadView(FormView):
    template_name = 'finance/cc_statement_upload.html'
    form_class    = CCStatementUploadForm

    def form_valid(self, form):
        file_obj = form.cleaned_data['statement_file']
        try:
            result = parse_scotiabank_cc_statement(file_obj, source_filename=file_obj.name)
        except ValueError as e:
            messages.error(self.request, str(e))
            return self.form_invalid(form)

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
        return redirect('finance:cc_staging_review')

    def form_invalid(self, form):
        messages.error(self.request, 'Invalid upload. Please select a valid .xls statement file.')
        return super().form_invalid(form)


# ─────────────────────────────────────────────
# Credit Card Staging Review
# ─────────────────────────────────────────────

class CCStagingReviewView(TemplateView):
    template_name = 'finance/cc_staging_review.html'

    def _qs(self):
        return StagingCCTransaction.objects.filter(is_processed=False).order_by('original_date')

    def get_context_data(self, **kwargs):
        ctx = super().get_context_data(**kwargs)
        qs  = self._qs()
        ctx['formset']       = StagingCCReviewFormset(queryset=qs)
        ctx['pending_count'] = qs.count()
        # Pass first record's card info for the header
        first = qs.first()
        ctx['card_number'] = first.card_number if first else ''
        ctx['card_holder'] = first.card_holder if first else ''
        return ctx

    def post(self, request, *args, **kwargs):
        qs      = self._qs()
        formset = StagingCCReviewFormset(request.POST, queryset=qs)

        if formset.is_valid():
            entries = [
                {'staging_id': f.instance.id, 'category_id': f.cleaned_data['assigned_category'].id}
                for f in formset
                if f.cleaned_data.get('assigned_category')
            ]
            processed = process_cc_staging_batch(entries)
            messages.success(request, f'{processed} CC transactions committed to the ledger.')
            return redirect('finance:dashboard')

        ctx            = self.get_context_data()
        ctx['formset'] = formset
        return self.render_to_response(ctx)

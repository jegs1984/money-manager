from django.shortcuts import render, redirect, get_object_or_404
from django.views.generic import (
    ListView, CreateView, UpdateView, DeleteView, DetailView, FormView, TemplateView
)
from django.urls import reverse_lazy
from django.db.models import Sum, F, Value, DecimalField
from django.db.models.functions import Coalesce
from django.contrib import messages
from django.http import HttpResponse
from django.db import transaction as db_transaction
from decimal import Decimal

from finance.models import (
    Group,
    BudgetMonth,
    Category,
    BudgetItem,
    Transaction,
    StagingTransaction,
)
from finance.forms import (
    GroupForm,
    BudgetMonthForm,
    CategoryForm,
    BudgetItemForm,
    TransactionForm,
    StatementUploadForm,
    StagingTransactionFormSet,
)
from finance.services import (
    parse_scotiabank_statement,
    process_staging_batch,
    log_transaction_service,
    rollover_month_balance,
    calculate_safe_to_spend,
)


class GroupListView(ListView):
    model = Group
    template_name = 'finance/group_list.html'
    context_object_name = 'groups'
    paginate_by = 20


class GroupCreateView(CreateView):
    model = Group
    form_class = GroupForm
    template_name = 'finance/group_form.html'
    success_url = reverse_lazy('group-list')


class GroupUpdateView(UpdateView):
    model = Group
    form_class = GroupForm
    template_name = 'finance/group_form.html'
    success_url = reverse_lazy('group-list')


class GroupDeleteView(DeleteView):
    model = Group
    template_name = 'finance/group_confirm_delete.html'
    success_url = reverse_lazy('group-list')


class DashboardView(TemplateView):
    template_name = 'finance/dashboard.html'
    
    def get_context_data(self, **kwargs):
        context = super().get_context_data(**kwargs)
        
        active_month = BudgetMonth.objects.filter(is_active=True).first()
        context['active_month'] = active_month
        
        if active_month:
            budget_items = BudgetItem.objects.filter(
                budget_month=active_month
            ).select_related('category').annotate(
                total_real=Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00'), output_field=DecimalField())),
                diff=F('projected_amount') - Coalesce(Sum('transactions__real_amount'), Value(Decimal('0.00'), output_field=DecimalField()))
            )
            
            context['budget_items'] = budget_items
            
            income_total = budget_items.filter(type='IN').aggregate(
                total=Coalesce(Sum('total_real'), Value(Decimal('0.00'), output_field=DecimalField()))
            )['total']
            
            expense_total = budget_items.filter(type='OUT').aggregate(
                total=Coalesce(Sum('total_real'), Value(Decimal('0.00'), output_field=DecimalField()))
            )['total']
            
            context['total_income'] = income_total
            context['total_expenses'] = expense_total
            context['safe_to_spend'] = income_total - expense_total
            
            max_projected = budget_items.filter(type='OUT').aggregate(
                max=Coalesce(Sum('projected_amount'), Value(Decimal('0.00'), output_field=DecimalField()))
            )['max']
            
            context['max_projected'] = max_projected if max_projected > 0 else 1
            
            if max_projected > 0:
                context['burn_rate'] = (expense_total / max_projected) * 100
            else:
                context['burn_rate'] = 0
        
        context['all_months'] = BudgetMonth.objects.all()
        
        return context


class BudgetMonthListView(ListView):
    model = BudgetMonth
    template_name = 'finance/budgetmonth_list.html'
    context_object_name = 'months'
    paginate_by = 12


class BudgetMonthCreateView(CreateView):
    model = BudgetMonth
    form_class = BudgetMonthForm
    template_name = 'finance/budgetmonth_form.html'
    success_url = reverse_lazy('budget-month-list')


class BudgetMonthUpdateView(UpdateView):
    model = BudgetMonth
    form_class = BudgetMonthForm
    template_name = 'finance/budgetmonth_form.html'
    success_url = reverse_lazy('budget-month-list')


class BudgetMonthDeleteView(DeleteView):
    model = BudgetMonth
    template_name = 'finance/budgetmonth_confirm_delete.html'
    success_url = reverse_lazy('budget-month-list')


class CategoryListView(ListView):
    model = Category
    template_name = 'finance/category_list.html'
    context_object_name = 'categories'
    paginate_by = 20


class CategoryCreateView(CreateView):
    model = Category
    form_class = CategoryForm
    template_name = 'finance/category_form.html'
    success_url = reverse_lazy('category-list')


class CategoryUpdateView(UpdateView):
    model = Category
    form_class = CategoryForm
    template_name = 'finance/category_form.html'
    success_url = reverse_lazy('category-list')


class CategoryDeleteView(DeleteView):
    model = Category
    template_name = 'finance/category_confirm_delete.html'
    success_url = reverse_lazy('category-list')


class BudgetItemCreateView(CreateView):
    model = BudgetItem
    form_class = BudgetItemForm
    template_name = 'finance/budgetitem_form.html'
    success_url = reverse_lazy('dashboard')


class BudgetItemUpdateView(UpdateView):
    model = BudgetItem
    form_class = BudgetItemForm
    template_name = 'finance/budgetitem_form.html'
    success_url = reverse_lazy('dashboard')


class BudgetItemDeleteView(DeleteView):
    model = BudgetItem
    template_name = 'finance/budgetitem_confirm_delete.html'
    success_url = reverse_lazy('dashboard')


class TransactionCreateView(CreateView):
    model = Transaction
    form_class = TransactionForm
    template_name = 'finance/transaction_form.html'
    success_url = reverse_lazy('dashboard')


class TransactionUpdateView(UpdateView):
    model = Transaction
    form_class = TransactionForm
    template_name = 'finance/transaction_form.html'
    success_url = reverse_lazy('dashboard')


class TransactionDeleteView(DeleteView):
    model = Transaction
    template_name = 'finance/transaction_confirm_delete.html'
    success_url = reverse_lazy('dashboard')


class StatementUploadView(FormView):
    form_class = StatementUploadForm
    template_name = 'finance/statement_upload.html'
    success_url = reverse_lazy('staging-review')
    
    def form_valid(self, form):
        try:
            statement_file = form.cleaned_data['statement_file']
            parse_scotiabank_statement(statement_file)
            messages.success(self.request, 'Statement uploaded and parsed successfully.')
            return super().form_valid(form)
        except Exception as e:
            messages.error(self.request, f'Error parsing statement: {str(e)}')
            return self.form_invalid(form)


class StagingReviewView(TemplateView):
    template_name = 'finance/staging_review.html'
    
    def get_context_data(self, **kwargs):
        context = super().get_context_data(**kwargs)
        
        unprocessed_staging = StagingTransaction.objects.filter(
            is_processed=False
        ).order_by('-original_date')
        
        if 'formset' not in context:
            context['formset'] = StagingTransactionFormSet(queryset=unprocessed_staging)

        context['staging_transactions'] = unprocessed_staging
        
        return context
    
    def post(self, request, *args, **kwargs):
        unprocessed_staging = StagingTransaction.objects.filter(
            is_processed=False
        ).order_by('-original_date')
        
        formset = StagingTransactionFormSet(
            request.POST,
            queryset=unprocessed_staging
        )
        
        if formset.is_valid():
            staging_ids_with_categories = []
            
            for form in formset.forms:
                if form.cleaned_data:
                    staging_id = form.instance.id
                    assigned_category = form.cleaned_data.get('assigned_category')
                    
                    if assigned_category:
                        staging_ids_with_categories.append({
                            'staging_id': staging_id,
                            'category_id': assigned_category.id
                        })
            
            if staging_ids_with_categories:
                try:
                    process_staging_batch(staging_ids_with_categories)
                    messages.success(request, f'Processed {len(staging_ids_with_categories)} transactions.')
                except Exception as e:
                    messages.error(request, f'Error processing transactions: {str(e)}')
            
            return redirect('dashboard')
        else:
            return self.render_to_response(self.get_context_data(formset=formset, **kwargs))

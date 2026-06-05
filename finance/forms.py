from django import forms
from django.forms import modelformset_factory
from finance.models import (
    BudgetMonth,
    Category,
    BudgetItem,
    Transaction,
    StagingTransaction,
)


class BudgetMonthForm(forms.ModelForm):
    class Meta:
        model = BudgetMonth
        fields = ['month_date', 'is_active']
        widgets = {
            'month_date': forms.DateInput(attrs={'type': 'date', 'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'is_active': forms.CheckboxInput(attrs={'class': 'h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded'}),
        }


class CategoryForm(forms.ModelForm):
    class Meta:
        model = Category
        fields = ['name', 'group']
        widgets = {
            'name': forms.TextInput(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'group': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
        }


class BudgetItemForm(forms.ModelForm):
    class Meta:
        model = BudgetItem
        fields = ['budget_month', 'category', 'type', 'projected_amount']
        widgets = {
            'budget_month': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'category': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'type': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'projected_amount': forms.NumberInput(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm', 'step': '0.01'}),
        }


class TransactionForm(forms.ModelForm):
    class Meta:
        model = Transaction
        fields = ['budget_item', 'date', 'real_amount', 'description', 'notes']
        widgets = {
            'budget_item': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'date': forms.DateInput(attrs={'type': 'date', 'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'real_amount': forms.NumberInput(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm', 'step': '0.01'}),
            'description': forms.TextInput(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
            'notes': forms.Textarea(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm', 'rows': 3}),
        }


class StatementUploadForm(forms.Form):
    statement_file = forms.FileField(
        label='Bank Statement File',
        widget=forms.FileInput(attrs={'accept': '.csv,.txt'})
    )


class StagingTransactionForm(forms.ModelForm):
    class Meta:
        model = StagingTransaction
        fields = ['original_date', 'description', 'amount', 'type', 'assigned_category']
        widgets = {
            'original_date': forms.DateInput(attrs={'disabled': True}),
            'description': forms.TextInput(attrs={'disabled': True}),
            'amount': forms.NumberInput(attrs={'disabled': True, 'step': '0.01'}),
            'type': forms.Select(attrs={'disabled': True}),
            'assigned_category': forms.Select(attrs={'class': 'block w-full px-3 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-blue-500 focus:border-blue-500 sm:text-sm'}),
        }


StagingTransactionFormSet = modelformset_factory(
    StagingTransaction,
    form=StagingTransactionForm,
    extra=0,
    can_delete=False
)

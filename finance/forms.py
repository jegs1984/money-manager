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
            'month_date': forms.DateInput(attrs={'type': 'date'}),
            'is_active': forms.CheckboxInput(),
        }


class CategoryForm(forms.ModelForm):
    class Meta:
        model = Category
        fields = ['name', 'group']
        widgets = {
            'name': forms.TextInput(attrs={'class': 'form-control'}),
            'group': forms.Select(attrs={'class': 'form-control'}),
        }


class BudgetItemForm(forms.ModelForm):
    class Meta:
        model = BudgetItem
        fields = ['budget_month', 'category', 'type', 'projected_amount']
        widgets = {
            'budget_month': forms.Select(attrs={'class': 'form-control'}),
            'category': forms.Select(attrs={'class': 'form-control'}),
            'type': forms.Select(attrs={'class': 'form-control'}),
            'projected_amount': forms.NumberInput(attrs={'class': 'form-control', 'step': '0.01'}),
        }


class TransactionForm(forms.ModelForm):
    class Meta:
        model = Transaction
        fields = ['budget_item', 'date', 'real_amount', 'description', 'notes']
        widgets = {
            'budget_item': forms.Select(attrs={'class': 'form-control'}),
            'date': forms.DateInput(attrs={'type': 'date', 'class': 'form-control'}),
            'real_amount': forms.NumberInput(attrs={'class': 'form-control', 'step': '0.01'}),
            'description': forms.TextInput(attrs={'class': 'form-control'}),
            'notes': forms.Textarea(attrs={'class': 'form-control', 'rows': 3}),
        }


class StatementUploadForm(forms.Form):
    statement_file = forms.FileField(
        label='Upload Bank Statement',
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
            'assigned_category': forms.Select(attrs={'class': 'form-control'}),
        }


StagingTransactionFormSet = modelformset_factory(
    StagingTransaction,
    form=StagingTransactionForm,
    extra=0,
    can_delete=False
)

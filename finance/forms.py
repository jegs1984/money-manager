from django import forms
from django.forms import modelformset_factory

from .models import BudgetItem, Category, Period, StagingCCTransaction, StagingTransaction, Transaction


class PeriodForm(forms.ModelForm):
    class Meta:
        model  = Period
        fields = ['name', 'start_date', 'end_date', 'is_active']
        widgets = {
            'name':       forms.TextInput(attrs={'class': 'form-input'}),
            'start_date': forms.DateInput(attrs={'type': 'date', 'class': 'form-input'}),
            'end_date':   forms.DateInput(attrs={'type': 'date', 'class': 'form-input'}),
            'is_active':  forms.CheckboxInput(attrs={'class': 'form-checkbox'}),
        }


class CategoryForm(forms.ModelForm):
    class Meta:
        model  = Category
        fields = ['name', 'group']
        widgets = {
            'name':  forms.TextInput(attrs={'class': 'form-input'}),
            'group': forms.Select(attrs={'class': 'form-select'}),
        }


class BudgetItemForm(forms.ModelForm):
    class Meta:
        model  = BudgetItem
        fields = ['period', 'category', 'type', 'projected_amount']
        widgets = {
            'period':           forms.Select(attrs={'class': 'form-select'}),
            'category':         forms.Select(attrs={'class': 'form-select'}),
            'type':             forms.Select(attrs={'class': 'form-select'}),
            'projected_amount': forms.NumberInput(attrs={'class': 'form-input', 'step': '0.01', 'min': '0'}),
        }


class TransactionForm(forms.ModelForm):
    class Meta:
        model  = Transaction
        fields = ['budget_item', 'date', 'real_amount', 'description', 'notes']
        widgets = {
            'budget_item': forms.Select(attrs={'class': 'form-select'}),
            'date':        forms.DateInput(attrs={'type': 'date', 'class': 'form-input'}),
            'real_amount': forms.NumberInput(attrs={'class': 'form-input', 'step': '0.01'}),
            'description': forms.TextInput(attrs={'class': 'form-input'}),
            'notes':       forms.Textarea(attrs={'class': 'form-input', 'rows': 3}),
        }


class StatementUploadForm(forms.Form):
    statement_file = forms.FileField(
        label='Bank Statement (.dat / .csv / .txt)',
        widget=forms.ClearableFileInput(attrs={'accept': '.dat,.csv,.txt', 'class': 'hidden', 'id': 'id_statement_file'}),
    )


class StagingTransactionReviewForm(forms.ModelForm):
    class Meta:
        model  = StagingTransaction
        fields = ['assigned_category']
        widgets = {
            'assigned_category': forms.Select(attrs={'class': 'form-select text-xs'}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['assigned_category'].required  = False
        self.fields['assigned_category'].queryset  = Category.objects.order_by('group', 'name')
        self.fields['assigned_category'].empty_label = '— Skip —'


StagingReviewFormset = modelformset_factory(
    StagingTransaction,
    form=StagingTransactionReviewForm,
    extra=0,
)


class CCStatementUploadForm(forms.Form):
    statement_file = forms.FileField(
        label='Credit Card Statement (.xls)',
        widget=forms.ClearableFileInput(
            attrs={'accept': '.xls,.xlsx', 'class': 'hidden', 'id': 'id_cc_statement_file'}
        ),
    )


class StagingCCTransactionReviewForm(forms.ModelForm):
    class Meta:
        model  = StagingCCTransaction
        fields = ['assigned_category']
        widgets = {
            'assigned_category': forms.Select(attrs={'class': 'form-select text-xs'}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['assigned_category'].required   = False
        self.fields['assigned_category'].queryset   = Category.objects.order_by('group', 'name')
        self.fields['assigned_category'].empty_label = '— Skip —'


StagingCCReviewFormset = modelformset_factory(
    StagingCCTransaction,
    form=StagingCCTransactionReviewForm,
    extra=0,
)

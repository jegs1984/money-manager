from django import forms
from django.forms import modelformset_factory
from django.utils import timezone

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

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        
        # 1. Sort the category dropdown by name
        self.fields['category'].queryset = Category.objects.order_by('name')
        
        # 2. Concatenate name and group for the display label
        # (Adjust 'obj.group' if your group field is a ForeignKey, e.g., 'obj.group.name')
        self.fields['category'].label_from_instance = lambda obj: f"{obj.name} ({obj.group})"


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
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # Only set the default if this is a new form (not editing an existing instance)
        if not self.instance.pk and 'date' in self.fields:
            self.fields['date'].initial = timezone.now().date()
            self.fields['description'].initial = '—'

class StatementUploadForm(forms.Form):
    statement_file = forms.FileField(
        label='Bank Statement (.dat / .csv / .txt)',
        widget=forms.ClearableFileInput(attrs={'accept': '.dat,.csv,.txt', 'class': 'hidden', 'id': 'id_statement_file'}),
    )


class CategoryModelChoiceField(forms.ModelChoiceField):
    def label_from_instance(self, obj):
        # Assuming your Category model has 'code' and 'name' fields
        # Change these to whatever fields you actually want to concatenate
        return f"{obj.name} - {obj.group}"
    
class StagingTransactionReviewForm(forms.ModelForm):
    assigned_category = CategoryModelChoiceField(
        queryset=Category.objects.none(), # Will be populated in __init__
        widget=forms.Select(attrs={'class': 'form-select text-xs'})
    )
        
    class Meta:
        model  = StagingTransaction
        fields = ['assigned_category']
        widgets = {
            'assigned_category': forms.Select(attrs={'class': 'form-select text-xs'}),
        }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['assigned_category'].required  = False
        self.fields['assigned_category'].queryset  = Category.objects.order_by('name')
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

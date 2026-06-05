from django.contrib import admin
from finance.models import Group, BudgetMonth, Category, BudgetItem, Transaction, StagingTransaction


@admin.register(Group)
class GroupAdmin(admin.ModelAdmin):
    list_display = ['name']
    search_fields = ['name']


@admin.register(BudgetMonth)
class BudgetMonthAdmin(admin.ModelAdmin):
    list_display = ['month_date', 'is_active']
    search_fields = ['month_date']
    list_filter = ['is_active']


@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    list_display = ['name', 'group']
    search_fields = ['name', 'group__name']
    list_filter = ['group']


@admin.register(BudgetItem)
class BudgetItemAdmin(admin.ModelAdmin):
    list_display = ['budget_month', 'category', 'type', 'projected_amount']
    search_fields = ['budget_month__month_date', 'category__name']
    list_filter = ['type', 'budget_month']


@admin.register(Transaction)
class TransactionAdmin(admin.ModelAdmin):
    list_display = ['date', 'description', 'real_amount', 'budget_item']
    search_fields = ['description', 'budget_item__category__name']
    list_filter = ['date', 'budget_item__category']


@admin.register(StagingTransaction)
class StagingTransactionAdmin(admin.ModelAdmin):
    list_display = ['original_date', 'description', 'amount', 'type', 'is_processed', 'assigned_category']
    search_fields = ['description']
    list_filter = ['type', 'is_processed', 'assigned_category']

from django.urls import path
from . import views

app_name = 'finance'

urlpatterns = [
    path('',                              views.DashboardView.as_view(),       name='dashboard'),
    path('dashboard/pdf/',                views.DashboardPDFView.as_view(),    name='dashboard_pdf'),
    path('groups/',                       views.GroupDashboardView.as_view(),  name='group_dashboard'),

    path('periods/',                      views.PeriodListView.as_view(),      name='period_list'),
    path('periods/create/',               views.PeriodCreateView.as_view(),    name='period_create'),
    path('periods/<int:pk>/edit/',        views.PeriodUpdateView.as_view(),    name='period_update'),
    path('periods/<int:pk>/delete/',      views.PeriodDeleteView.as_view(),    name='period_delete'),
    path('periods/<int:pk>/close/',       views.PeriodCloseView.as_view(),     name='period_close'),
    path('periods/<int:pk>/duplicate-budget/', views.PeriodDuplicateBudgetView.as_view(), name='period_duplicate_budget'),

    path('categories/',                   views.CategoryListView.as_view(),    name='category_list'),
    path('categories/create/',            views.CategoryCreateView.as_view(),  name='category_create'),
    path('categories/<int:pk>/edit/',     views.CategoryUpdateView.as_view(),  name='category_update'),
    path('categories/<int:pk>/delete/',   views.CategoryDeleteView.as_view(),  name='category_delete'),

    path('accounts/',                     views.AccountListView.as_view(),       name='account_list'),
    path('accounts/create/',              views.AccountCreateView.as_view(),     name='account_create'),
    path('accounts/<int:pk>/edit/',       views.AccountUpdateView.as_view(),     name='account_update'),
    path('transfers/create/',             views.TransferCreateView.as_view(),    name='transfer_create'),
    path('reconciliations/create/',       views.ReconciliationCreateView.as_view(), name='reconciliation_create'),
    path('merchant-rules/',               views.MerchantRuleListView.as_view(), name='merchant_rule_list'),
    path('merchant-rules/create/',        views.MerchantRuleCreateView.as_view(), name='merchant_rule_create'),
    path('recurring/',                    views.RecurringPlanListView.as_view(), name='recurring_plan_list'),
    path('recurring/create/',             views.RecurringPlanCreateView.as_view(), name='recurring_plan_create'),
    path('goals/',                        views.GoalListView.as_view(), name='goal_list'),
    path('goals/create/',                 views.GoalCreateView.as_view(), name='goal_create'),

    path('budget-items/create/',          views.BudgetItemCreateView.as_view(),  name='budgetitem_create'),
    path('budget-items/<int:pk>/edit/',   views.BudgetItemUpdateView.as_view(),  name='budgetitem_update'),
    path('budget-items/<int:pk>/delete/', views.BudgetItemDeleteView.as_view(),  name='budgetitem_delete'),

    path('transactions/',                 views.TransactionListView.as_view(),   name='transaction_list'),
    path('transactions/create/',          views.TransactionCreateView.as_view(), name='transaction_create'),
    path('transactions/<int:pk>/edit/',   views.TransactionUpdateView.as_view(), name='transaction_update'),
    path('transactions/<int:pk>/delete/', views.TransactionDeleteView.as_view(), name='transaction_delete'),
    path('transactions/<int:pk>/reverse/', views.TransactionReverseView.as_view(), name='transaction_reverse'),

    path('upload/',          views.StatementUploadView.as_view(),      name='statement_upload'),
    path('staging/',         views.StagingReviewView.as_view(),        name='staging_review'),
    path('staging/delete/',  views.StagingDeleteView.as_view(),        name='staging_delete'),

    path('cc/upload/',          views.CCStatementUploadView.as_view(),   name='cc_statement_upload'),
    path('cc/staging/',         views.CCStagingReviewView.as_view(),     name='cc_staging_review'),
    path('cc/staging/delete/',  views.CCStagingDeleteView.as_view(),     name='cc_staging_delete'),
]

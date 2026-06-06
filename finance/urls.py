from django.urls import path
from . import views

app_name = 'finance'

urlpatterns = [
    path('',                              views.DashboardView.as_view(),       name='dashboard'),

    path('periods/',                      views.PeriodListView.as_view(),      name='period_list'),
    path('periods/create/',               views.PeriodCreateView.as_view(),    name='period_create'),
    path('periods/<int:pk>/edit/',        views.PeriodUpdateView.as_view(),    name='period_update'),
    path('periods/<int:pk>/delete/',      views.PeriodDeleteView.as_view(),    name='period_delete'),

    path('categories/',                   views.CategoryListView.as_view(),    name='category_list'),
    path('categories/create/',            views.CategoryCreateView.as_view(),  name='category_create'),
    path('categories/<int:pk>/edit/',     views.CategoryUpdateView.as_view(),  name='category_update'),
    path('categories/<int:pk>/delete/',   views.CategoryDeleteView.as_view(),  name='category_delete'),

    path('budget-items/create/',          views.BudgetItemCreateView.as_view(),  name='budgetitem_create'),
    path('budget-items/<int:pk>/edit/',   views.BudgetItemUpdateView.as_view(),  name='budgetitem_update'),
    path('budget-items/<int:pk>/delete/', views.BudgetItemDeleteView.as_view(),  name='budgetitem_delete'),

    path('transactions/',                 views.TransactionListView.as_view(),   name='transaction_list'),
    path('transactions/create/',          views.TransactionCreateView.as_view(), name='transaction_create'),
    path('transactions/<int:pk>/edit/',   views.TransactionUpdateView.as_view(), name='transaction_update'),
    path('transactions/<int:pk>/delete/', views.TransactionDeleteView.as_view(), name='transaction_delete'),

    path('upload/',   views.StatementUploadView.as_view(), name='statement_upload'),
    path('staging/',  views.StagingReviewView.as_view(),   name='staging_review'),

    path('cc/upload/',  views.CCStatementUploadView.as_view(), name='cc_statement_upload'),
    path('cc/staging/', views.CCStagingReviewView.as_view(),   name='cc_staging_review'),
]

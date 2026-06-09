package com.moneymanager.ui.navigation

object Routes {
    const val DASHBOARD           = "dashboard"
    const val PERIODS             = "periods"
    const val PERIOD_FORM         = "period_form/{periodId}"
    const val CATEGORIES          = "categories"
    const val CATEGORY_FORM       = "category_form/{categoryId}"
    const val BUDGET_ITEMS        = "budget_items/{periodId}"
    const val BUDGET_ITEM_FORM    = "budget_item_form/{budgetItemId}"
    const val TRANSACTIONS        = "transactions"
    const val TRANSACTION_FORM    = "transaction_form/{transactionId}"
    const val IMPORT_DAT          = "import_dat"
    const val STAGING_REVIEW      = "staging_review/{periodId}"
    const val IMPORT_CC           = "import_cc"
    const val STAGING_CC_REVIEW   = "staging_cc_review/{periodId}"
    const val NOTIFICATIONS       = "notifications"

    fun periodForm(periodId: Long = 0L)           = "period_form/$periodId"
    fun categoryForm(categoryId: Long = 0L)       = "category_form/$categoryId"
    fun budgetItems(periodId: Long)               = "budget_items/$periodId"
    fun budgetItemForm(budgetItemId: Long = 0L)   = "budget_item_form/$budgetItemId"
    fun transactionForm(transactionId: Long = 0L) = "transaction_form/$transactionId"
    fun stagingReview(periodId: Long)             = "staging_review/$periodId"
    fun stagingCCReview(periodId: Long)           = "staging_cc_review/$periodId"
}

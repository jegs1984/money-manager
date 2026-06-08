package com.moneymanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneymanager.ui.screens.budgetitems.BudgetItemFormScreen
import com.moneymanager.ui.screens.budgetitems.BudgetItemsScreen
import com.moneymanager.ui.screens.categories.CategoryFormScreen
import com.moneymanager.ui.screens.categories.CategoriesScreen
import com.moneymanager.ui.screens.dashboard.DashboardScreen
import com.moneymanager.ui.screens.importflow.*
import com.moneymanager.ui.screens.notifications.NotificationPermissionScreen
import com.moneymanager.ui.screens.periods.PeriodFormScreen
import com.moneymanager.ui.screens.periods.PeriodsScreen
import com.moneymanager.ui.screens.transactions.TransactionFormScreen
import com.moneymanager.ui.screens.transactions.TransactionsScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(nav)
        }

        // ── Periods ────────────────────────────────────────────────────────
        composable(Routes.PERIODS) { PeriodsScreen(nav) }
        composable(
            Routes.PERIOD_FORM,
            arguments = listOf(navArgument("periodId") { type = NavType.LongType; defaultValue = 0L }),
        ) { PeriodFormScreen(nav, it.arguments?.getLong("periodId") ?: 0L) }

        // ── Categories ─────────────────────────────────────────────────────
        composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
        composable(
            Routes.CATEGORY_FORM,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType; defaultValue = 0L }),
        ) { CategoryFormScreen(nav, it.arguments?.getLong("categoryId") ?: 0L) }

        // ── Budget Items ───────────────────────────────────────────────────
        composable(
            Routes.BUDGET_ITEMS,
            arguments = listOf(navArgument("periodId") { type = NavType.LongType }),
        ) { BudgetItemsScreen(nav, it.arguments!!.getLong("periodId")) }
        composable(
            Routes.BUDGET_ITEM_FORM,
            arguments = listOf(navArgument("budgetItemId") { type = NavType.LongType; defaultValue = 0L }),
        ) { BudgetItemFormScreen(nav, it.arguments?.getLong("budgetItemId") ?: 0L) }

        // ── Transactions ───────────────────────────────────────────────────
        composable(Routes.TRANSACTIONS) { TransactionsScreen(nav) }
        composable(
            Routes.TRANSACTION_FORM,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType; defaultValue = 0L }),
        ) { TransactionFormScreen(nav, it.arguments?.getLong("transactionId") ?: 0L) }

        // ── Import DAT ─────────────────────────────────────────────────────
        composable(Routes.IMPORT_DAT) { ImportDatScreen(nav) }
        composable(
            Routes.STAGING_REVIEW,
            arguments = listOf(navArgument("periodId") { type = NavType.LongType }),
        ) { StagingReviewScreen(nav, it.arguments!!.getLong("periodId")) }

        // ── Import CC ──────────────────────────────────────────────────────
        composable(Routes.IMPORT_CC) { ImportCCScreen(nav) }
        composable(
            Routes.STAGING_CC_REVIEW,
            arguments = listOf(navArgument("periodId") { type = NavType.LongType }),
        ) { StagingCCReviewScreen(nav, it.arguments!!.getLong("periodId")) }

        // ── Notification Monitor ───────────────────────────────────────────
        composable(Routes.NOTIFICATIONS) { NotificationPermissionScreen() }
    }
}

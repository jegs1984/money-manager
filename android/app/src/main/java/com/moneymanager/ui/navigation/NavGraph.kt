package com.moneymanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moneymanager.ui.screen.DashboardScreen
import com.moneymanager.ui.screen.StagingReviewScreen
import com.moneymanager.ui.screen.CCStagingReviewScreen

sealed class Screen(val route: String) {
    object Dashboard      : Screen("dashboard")
    object StagingReview  : Screen("staging_review")
    object CCStagingReview: Screen("cc_staging_review")
}

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToStaging   = { navController.navigate(Screen.StagingReview.route) },
                onNavigateToCCStaging = { navController.navigate(Screen.CCStagingReview.route) },
            )
        }
        composable(Screen.StagingReview.route) {
            StagingReviewScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.CCStagingReview.route) {
            CCStagingReviewScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

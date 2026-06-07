package com.moneymanager.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Permission gate screen for the notification listener feature.
 * Displayed when [NotificationViewModel.permissionGranted] is false.
 * Navigates away once the user grants access and returns to the app.
 */
@Composable
fun NotificationPermissionScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val granted by viewModel.permissionGranted.collectAsState()
    val lifecycle = LocalLifecycleOwner.current

    // Re-check permission every time the app returns to foreground (after settings round-trip).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermission()
        }
    }

    if (granted) {
        NotificationDashboardScreen(viewModel = viewModel)
        return
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.NotificationsOff,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = "Notification Access Required",
            style     = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "Money Manager needs permission to read bank notifications " +
                        "so it can automatically log your transactions.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = viewModel::openPermissionSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Notification Settings")
        }
    }
}

/**
 * Minimal dashboard shown once permission is granted.
 * Wire this into [com.moneymanager.ui.navigation.NavGraph] as appropriate.
 */
@Composable
fun NotificationDashboardScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val latest by viewModel.latestTransaction.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bank Notification Monitor", style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()

        if (latest == null) {
            Text(
                "Listening for bank notifications…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            latest?.let { tx ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Latest captured transaction", style = MaterialTheme.typography.labelSmall)
                        Text(tx.title, style = MaterialTheme.typography.titleMedium)
                        Text(tx.rawContent, style = MaterialTheme.typography.bodySmall)
                        tx.amount?.let {
                            Text(
                                "Amount: ${"%.2f".format(it)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

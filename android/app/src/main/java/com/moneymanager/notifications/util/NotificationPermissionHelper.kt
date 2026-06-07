package com.moneymanager.notifications.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationPermissionHelper {

    /**
     * BIND_NOTIFICATION_LISTENER_SERVICE is not a runtime permission — it lives in
     * system settings. The only reliable way to check it is via the enabled-listeners
     * list that NotificationManagerCompat exposes.
     */
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * Opens the system Notification Access screen. There is no programmatic grant path;
     * the user must toggle the switch manually.
     * FLAG_ACTIVITY_NEW_TASK is required when launching from a non-Activity context.
     */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

package com.moneymanager.notifications

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.domain.usecase.BankNotificationParser
import com.moneymanager.notifications.model.RawBankNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Intercepts push notifications from whitelisted Chilean bank apps.
 * Extracts amount + description and inserts a StagingTransaction for review.
 *
 * The user must grant Notification Access manually:
 *   Settings → Apps → Special app access → Notification access → Money Manager
 */
@AndroidEntryPoint
class BankNotificationService : NotificationListenerService() {

    @Inject lateinit var repo: FinanceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Whitelist: Scotiabank Chile package IDs. Extend as needed.
    private val ALLOWED_PACKAGES = setOf(
        "cl.scotiabankchile.banca",
        "cl.bci.bci",
        "cl.santander.mobile",
        "cl.bancoestado.app",
        "cl.itau",
        "com.falabella.falabellabank",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in ALLOWED_PACKAGES) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val raw = RawBankNotification(sbn.packageName, title, text, sbn.postTime)
        notificationFlow.tryEmit(raw)
        val parsed = BankNotificationParser.parse(raw) ?: return

        scope.launch {
            repo.insertStagingRows(
                listOf(
                    StagingTransactionEntity(
                        sourceFile   = "notification:${sbn.packageName}",
                        originalDate = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate(),
                        description  = parsed.description,
                        amount       = parsed.amount.toPlainString(),
                        type         = parsed.type,
                    )
                )
            )
        }
    }

    override fun onListenerDisconnected() {
        // requestRebind if needed in future
    }

    companion object {
        val notificationFlow = kotlinx.coroutines.flow.MutableSharedFlow<RawBankNotification>(extraBufferCapacity = 32)
    }
}

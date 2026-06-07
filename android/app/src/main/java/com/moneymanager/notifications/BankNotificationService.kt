package com.moneymanager.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymanager.data.entity.StagingTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.domain.usecase.ParseBankNotificationUseCase
import com.moneymanager.notifications.model.RawBankNotification
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * System-bound service that intercepts status bar notifications and re-emits
 * those originating from whitelisted banking apps as [RawBankNotification] events.
 *
 * Lifecycle notes:
 *  - The OS binds/unbinds this service independently of the app's process lifecycle.
 *  - [serviceScope] is tied to the service instance; it is cancelled in [onDestroy]
 *    to prevent coroutine leaks across OS rebind cycles.
 *  - [notificationFlow] lives in the companion object (process singleton) so that
 *    collectors in ViewModels can subscribe before the service is first bound,
 *    avoiding a startup race condition.
 */
class BankNotificationService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BankNotificationServiceEntryPoint {
        fun parseBankNotificationUseCase(): ParseBankNotificationUseCase
        fun financeRepository(): FinanceRepository
    }

    // SupervisorJob: a failed emit does not cancel sibling coroutines or the scope.
    // IO dispatcher: SharedFlow.emit() may suspend under backpressure; keep off Main.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            BankNotificationServiceEntryPoint::class.java
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // ── SECURITY GATE ─────────────────────────────────────────────────────
        // Hard whitelist check is the very first operation. If the package is not
        // a known bank we return immediately without touching any notification data,
        // protecting the user's privacy (messages, email, social, etc. are ignored).
        if (sbn.packageName !in BANK_PACKAGE_WHITELIST) return

        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: return

        val raw = RawBankNotification(
            bankAppId = sbn.packageName,
            title     = title,
            content   = content,
            timestamp = sbn.postTime,
        )

        // Emit off the main thread so this OS callback is never blocked.
        serviceScope.launch {
            // 1. Emit for real-time UI updates
            _notificationFlow.emit(raw)

            // 2. Persistence: Parse and save to Staging table
            val parsed = entryPoint.parseBankNotificationUseCase().parse(raw)
            if (parsed != null && parsed.amount != null) {
                entryPoint.financeRepository().insertStagingRows(
                    listOf(
                        StagingTransactionEntity(
                            sourceFile   = "Notification: ${sbn.packageName}",
                            originalDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                .format(java.util.Date(sbn.postTime)),
                            description  = "${parsed.title}: ${parsed.rawContent}",
                            amount       = parsed.amount,
                            type         = if (parsed.amount < 0) "OUT" else "IN"
                        )
                    )
                )
            }
        }
    }

    companion object {
        /**
         * SharedFlow is used instead of Channel because:
         *  - Multiple downstream collectors can subscribe simultaneously
         *    (parser use case, audit logger, analytics, etc.).
         *  - replay=1 lets a late subscriber receive the most recent emission
         *    without reprocessing the full history.
         *  - extraBufferCapacity=16 absorbs OTP/alert bursts without dropping events.
         *
         * Exposed as the read-only [SharedFlow] interface so only this service emits.
         */
        private val _notificationFlow = MutableSharedFlow<RawBankNotification>(
            replay              = 1,
            extraBufferCapacity = 16,
        )
        val notificationFlow: SharedFlow<RawBankNotification> = _notificationFlow.asSharedFlow()

        /**
         * Chilean and international banks relevant to MoneyManager.
         * Extend this set or inject it via a repository for testability / remote config.
         */
        val BANK_PACKAGE_WHITELIST: Set<String> = setOf(
            // ── Chile ──────────────────────────────────────────────────────────
            "cl.bancochile.mi_banco",          // Banco de Chile — Mi Banco
            "com.scotiabank.banking",           // Scotiabank Chile
            "cl.bci.android",                  // BCI
            "cl.santander.app",                // Santander Chile
            "com.falabella.bank",              // Banco Falabella
            "cl.itau.banking",                 // Itaú Chile
            // ── International fallbacks ────────────────────────────────────────
            "com.chase.sig.android",            // Chase Mobile
            "com.bofa.mBanking",               // Bank of America
        )
    }
}

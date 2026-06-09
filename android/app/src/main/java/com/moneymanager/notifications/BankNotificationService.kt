package com.moneymanager.notifications

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.regex.Pattern
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

    // Regex for Chilean peso amounts: $1.234 or $1.234,56
    private val AMOUNT_PATTERN = Pattern.compile(
        "\\$\\s?([\\d\\.]+(?:,\\d{1,2})?)"
    )

    // Heuristic: "Compra", "Cargo", "Débito" → OUT; "Abono", "Depósito" → IN
    private val OUT_KEYWORDS = listOf("compra", "cargo", "débito", "debito", "pago", "transferencia salida")
    private val IN_KEYWORDS  = listOf("abono", "depósito", "deposito", "transferencia entrada", "recibiste")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in ALLOWED_PACKAGES) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val full  = "$title $text"

        val amount = extractAmount(full) ?: return
        val type   = detectType(full)
        val description = buildDescription(title, text)

        scope.launch {
            repo.insertStagingRows(
                listOf(
                    StagingTransactionEntity(
                        sourceFile   = "notification:${sbn.packageName}",
                        originalDate = LocalDate.now(),
                        description  = description,
                        amount       = amount.toPlainString(),
                        type         = type,
                    )
                )
            )
        }
    }

    private fun extractAmount(text: String): BigDecimal? {
        val m = AMOUNT_PATTERN.matcher(text)
        if (!m.find()) return null
        val raw = m.group(1)
            ?.replace(".", "")   // thousands separator
            ?.replace(",", ".") // decimal separator
            ?: return null
        return runCatching { BigDecimal(raw).setScale(2) }.getOrNull()
    }

    private fun detectType(text: String): String {
        val lower = text.lowercase()
        if (IN_KEYWORDS.any { it in lower }) return "IN"
        return "OUT"  // default: treat unknown as expense
    }

    private fun buildDescription(title: String, text: String): String {
        val combined = "$title – $text".replace(Regex("\\s+"), " ").trim()
        return combined.take(255)
    }

    override fun onListenerDisconnected() {
        // requestRebind if needed in future
    }
}

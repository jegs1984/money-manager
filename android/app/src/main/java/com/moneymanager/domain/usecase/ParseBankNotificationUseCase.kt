package com.moneymanager.domain.usecase

import com.moneymanager.notifications.BankNotificationService
import com.moneymanager.notifications.model.RawBankNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

data class ParsedTransaction(
    val bankAppId:  String,
    val title:      String,
    val rawContent: String,
    val amount:     Double?,  // null = no parseable amount found; caller decides what to do
    val timestamp:  Long,
)

/**
 * Collects raw notifications from [BankNotificationService] and attempts to
 * extract a monetary amount via Regex. Non-parseable notifications are filtered
 * out via [mapNotNull] rather than propagated as nulls.
 *
 * This is a pure domain class: no Android framework dependencies, trivially testable.
 */
class ParseBankNotificationUseCase @Inject constructor() {

    /**
     * Returns a cold-ish Flow backed by the process-singleton SharedFlow.
     * The ViewModel (not this use case) owns collection and the coroutine lifecycle.
     */
    operator fun invoke(): Flow<ParsedTransaction> =
        BankNotificationService.notificationFlow
            .mapNotNull { raw -> parse(raw) }

    private fun parse(raw: RawBankNotification): ParsedTransaction? {
        val amount = extractAmount(raw.content)
        // Drop notifications with no parseable amount (OTP codes, balance alerts without
        // a transaction figure, etc.). Remove this guard to forward all notifications.
        if (amount == null) return null

        return ParsedTransaction(
            bankAppId  = raw.bankAppId,
            title      = raw.title,
            rawContent = raw.content,
            amount     = amount,
            timestamp  = raw.timestamp,
        )
    }

    /**
     * Matches common Chilean and international banking amount formats, e.g.:
     *   $15.00 | $ 15.00 | $1,234.56
     *   CLP $500 | CLP$ 1.200 | USD 1,200.00
     *   -$50.00 (debit / charge)
     *
     * Group 1 captures the raw numeric string; commas and dots are normalised
     * before [toDoubleOrNull] to handle both CLP (dot-thousands) and USD formats.
     *
     * Limitation: does not yet distinguish CLP dot-separator from USD decimal point.
     * A production implementation would branch on [bankAppId] to apply locale-aware parsing.
     */
    private val amountPattern = Regex(
        """(?:CLP|USD|EUR|MXN|UF)?\s*\$\s*(-?[\d]{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)"""
    )

    private fun extractAmount(text: String): Double? {
        val raw = amountPattern.find(text)?.groupValues?.getOrNull(1) ?: return null
        // Normalise: remove thousand-separator commas/dots, keep the decimal portion.
        // Heuristic: if the last separator has exactly 2 digits after it, treat as decimal.
        val normalised = if (raw.matches(Regex(""".*[.,]\d{2}$"""))) {
            raw.dropLast(3).replace(",", "").replace(".", "") + "." + raw.takeLast(2)
        } else {
            raw.replace(",", "").replace(".", "")
        }
        return normalised.toDoubleOrNull()
    }
}

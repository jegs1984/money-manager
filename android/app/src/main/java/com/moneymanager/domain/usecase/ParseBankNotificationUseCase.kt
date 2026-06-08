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

    fun parse(raw: RawBankNotification): ParsedTransaction? {
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

    // Pattern matches numbers like 1.234,56 or 1,234.56 with an optional leading context.
    private val amountPattern = Regex(
        """(-?\s?\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})?|-?\s?\d{1,3}(?:[.,]\d{3})*|[.,]\d{1,2})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extract a numeric value from string content using a heuristic that handles
     * both dot-thousands (CLP/EUR) and comma-thousands (USD) formats.
     */
    private fun extractAmount(text: String): Double? {
        val currencySymbols = listOf("$", "£", "€", "CLP", "USD", "EUR", "MXN", "UF")
        
        val matches = amountPattern.findAll(text).toList()
        if (matches.isEmpty()) return null

        // Strategy: find the match with the most "financial" context.
        val match = matches.find { m ->
            val index = m.range.first
            val nearbyText = text.substring(
                kotlin.math.max(0, index - 5),
                kotlin.math.min(text.length, index + m.value.length + 5)
            )
            currencySymbols.any { nearbyText.contains(it, ignoreCase = true) }
        } ?: matches.lastOrNull() ?: return null
        
        val matchedValue = match.value.trim()
        val index = match.range.first
        
        // Nearby text for context (specifically looking for currency symbols and signs)
        val contextText = text.substring(
            kotlin.math.max(0, index - 10),
            kotlin.math.min(text.length, index + matchedValue.length + 10)
        )
        val hasCurrencyNearby = currencySymbols.any { contextText.contains(it, ignoreCase = true) }

        // Security: Ignore plain numbers (likely IDs/OTPs) unless they have currency context nearby.
        if (matchedValue.replace("-", "").trim().length >= 4 && 
            !matchedValue.contains(Regex("""[.,]""")) && 
            !hasCurrencyNearby) {
            return null
        }

        // Step 1: Detect if the last separator is a decimal point.
        val hasCents = matchedValue.contains(Regex("""[.,]\d{2}$"""))
        // Check for minus sign in the matched value OR just before it in the context text
        val prefixText = contextText.substringBefore(matchedValue)
        val isNegative = matchedValue.startsWith("-") || prefixText.trim().endsWith("-")
        
        val normalised = if (hasCents) {
            val cleanDigits = matchedValue.replace("-", "").replace(",", "").replace(".", "").trim()
            val integerPart = cleanDigits.dropLast(2)
            val decimalPart = cleanDigits.takeLast(2)
            val sign = if (isNegative) "-" else ""
            "$sign${integerPart.ifEmpty { "0" }}.$decimalPart"
        } else {
            // No cents detected; strip all separators and treat as a whole number.
            val cleanDigits = matchedValue.replace("-", "").replace(",", "").replace(".", "").trim()
            val sign = if (isNegative) "-" else ""
            "$sign$cleanDigits"
        }

        return normalised.toDoubleOrNull()
    }
}

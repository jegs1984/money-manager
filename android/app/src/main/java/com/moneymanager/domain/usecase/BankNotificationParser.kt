package com.moneymanager.domain.usecase

import com.moneymanager.notifications.model.RawBankNotification
import java.math.BigDecimal

/** Pure, framework-free parser shared by notification ingestion and tests. */
object BankNotificationParser {
    data class Result(val amount: BigDecimal, val type: String, val description: String)

    private val amount = Regex(
        """(-?\s*\$\s*\d+(?:(?:\.\d{3})+(?:,\d{1,2})?|(?:,\d{3})+(?:\.\d{1,2})?|[.,]\d{1,2})?|-?\s*\d+(?:[,.]\d{1,2})?\s*(?:€|CLP|USD))""",
        RegexOption.IGNORE_CASE,
    )
    private val incoming = listOf("abono", "depósito", "deposito", "recibiste", "transferencia entrada")

    fun parse(raw: RawBankNotification): Result? {
        val text = "${raw.title} ${raw.content}".replace(Regex("\\s+"), " ").trim()
        val match = amount.find(text) ?: return null
        val token = match.value.replace(Regex("""\s|\$|€|CLP|USD""", RegexOption.IGNORE_CASE), "")
        val negative = token.startsWith('-')
        val unsigned = token.removePrefix("-")
        val normalized = when {
            ',' in unsigned -> unsigned.replace(".", "").replace(',', '.')
            unsigned.count { it == '.' } > 1 || unsigned.matches(Regex(""".*\.\d{3}$""")) -> unsigned.replace(".", "")
            else -> unsigned
        }
        val parsed = normalized.toBigDecimalOrNull() ?: return null
        return Result(
            amount = parsed.abs(),
            type = if (negative || incoming.any { it in text.lowercase() }) "IN" else "OUT",
            description = text.take(255),
        )
    }
}

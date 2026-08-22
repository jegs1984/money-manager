package com.moneymanager.domain.usecase

import com.moneymanager.notifications.model.RawBankNotification
import com.moneymanager.notifications.BankNotificationService
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
        val parsed = BankNotificationParser.parse(raw) ?: return null

        return ParsedTransaction(
            bankAppId  = raw.bankAppId,
            title      = raw.title,
            rawContent = raw.content,
            // Keep the legacy presentation contract signed, while staging uses
            // the parser's exact positive amount plus its explicit direction.
            amount     = if (raw.content.contains("-$")) -parsed.amount.toDouble() else parsed.amount.toDouble(),
            timestamp  = raw.timestamp,
        )
    }

}

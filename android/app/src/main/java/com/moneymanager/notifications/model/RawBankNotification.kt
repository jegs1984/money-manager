package com.moneymanager.notifications.model

/**
 * Raw payload emitted by [com.moneymanager.notifications.BankNotificationService].
 * Deliberately unprocessed — parsing is the responsibility of the domain layer.
 */
data class RawBankNotification(
    val bankAppId: String,   // Source package name; used to route to the right parser
    val title: String,
    val content: String,
    val timestamp: Long,     // sbn.postTime (epoch ms) — reliable cross-device
)

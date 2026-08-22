package com.moneymanager.domain.usecase

import com.moneymanager.notifications.model.RawBankNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParseBankNotificationUseCaseTest {

    @Test
    fun `parse extract amount with dollar sign and decimals`() {
        val raw = createRaw("Chase", "Transaction of $25.40 at Starbucks")
        val result = BankNotificationParser.parse(raw)
        
        assertNotNull("Should parse valid transaction", result)
        assertEquals("25.40", result?.amount!!.toPlainString())
        assertEquals("OUT", result.type)
    }

    @Test
    fun `parse extract amount with CLP dot format`() {
        val raw = createRaw("BancoChile", "Compra por $15.000 en Tienda X")
        val result = BankNotificationParser.parse(raw)
        
        assertNotNull("Should parse valid transaction", result)
        assertEquals("15000", result?.amount!!.toPlainString())
    }

    @Test
    fun `parse extract amount with space and currency code`() {
        val raw = createRaw("Santander", "Abono CLP $ 5.000 de Juan")
        val result = BankNotificationParser.parse(raw)
        
        assertNotNull("Should parse valid transaction", result)
        assertEquals("5000", result?.amount!!.toPlainString())
        assertEquals("IN", result.type)
    }

    @Test
    fun `parse extract negative amount`() {
        val raw = createRaw("BofA", "Debit of -$50.00 at ATM")
        val result = BankNotificationParser.parse(raw)
        
        assertNotNull("Should parse valid transaction", result)
        assertEquals("50.00", result?.amount!!.toPlainString())
        assertEquals("IN", result.type)
    }

    @Test
    fun `parse returns null for non-financial notifications`() {
        // Plain numbers that look like OTPs or IDs should be ignored if they have no context
        val raw = createRaw("Security", "Tu clave de internet es 123456")
        val result = BankNotificationParser.parse(raw)
        
        assertNull("Should ignore OTP-like numbers", result)
    }

    @Test
    fun `parse handles comma as decimal separator`() {
        val raw = createRaw("EuroBank", "Compra de 12,50 €")
        val result = BankNotificationParser.parse(raw)
        
        assertNotNull("Should parse valid transaction", result)
        assertEquals("12.50", result?.amount!!.toPlainString())
    }

    private fun createRaw(title: String, content: String) = RawBankNotification(
        bankAppId = "com.test.bank",
        title     = title,
        content   = content,
        timestamp = System.currentTimeMillis()
    )
}

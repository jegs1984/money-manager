package com.moneymanager.domain.usecase

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseDebitStatementUseCaseTest {
    @Test
    fun `parser preserves signed balances and skips malformed records`() {
        val statement = ";Numero Cuenta : 00-00000-00\nFecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo\n01012026;Mercado;1;000000001234,00;;-000000001234,00\nbad;broken\n"
        val result = ParseDebitStatementUseCase().parse(ByteArrayInputStream(statement.toByteArray()))
        assertEquals(1, result.rows.size)
        assertEquals("OUT", result.rows.single().type)
        assertEquals("-1234.00", result.rows.single().balance)
        assertEquals(1, result.skipped)
    }
}

package com.moneymanager.data.parser

import com.moneymanager.data.entity.StagingTransactionEntity
import java.io.InputStream

/**
 * Parses a Scotiabank Chile `.dat` cartola (semicolon-delimited).
 *
 * Expected format:
 *   ;Cartola
 *   ;Numero Cuenta : 98-09719-10
 *   ;Fecha Desde   : 21/04/2026
 *   ;Fecha Hasta   : 05/06/2026
 *   Fecha;Descripcion;NroDoc.;Cargos;Abonos;Saldo
 *      21042026;REDCOMPRA ...;13244336;0000000005230,00;;+0000000032190,00
 */
object DatParser {

    data class ParseResult(
        val rows: List<StagingTransactionEntity>,
        val accountNumber: String?,
        val dateFrom: String?,
        val dateTo: String?,
        val skipped: Int,
    )

    fun parse(stream: InputStream, sourceFilename: String?): ParseResult {
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()

        var accountNumber: String? = null
        var dateFrom: String?      = null
        var dateTo: String?        = null
        var skipped                = 0
        val rows = mutableListOf<StagingTransactionEntity>()

        for (raw in lines) {
            val line = raw.trimStart('\uFEFF').trimEnd()   // strip BOM + trailing whitespace

            // ── Header metadata ──────────────────────────────────────────────
            when {
                line.startsWith(";Numero Cuenta") ->
                    accountNumber = line.substringAfter(":").trim()
                line.startsWith(";Fecha Desde") ->
                    dateFrom = parseDdmmyyyy(line.substringAfter(":").trim())
                line.startsWith(";Fecha Hasta") ->
                    dateTo = parseDdmmyyyy(line.substringAfter(":").trim())
            }

            // ── Skip comment / header lines ──────────────────────────────────
            if (line.startsWith(";") || line.startsWith("Fecha") || line.isBlank()) continue

            val cols = line.split(";")
            if (cols.size < 5) { skipped++; continue }

            val rawDate   = cols[0].trim()
            val desc      = cols[1].trim()
            val docNumber = cols[2].trim().takeIf { it.isNotBlank() && it != "00000000" }
            val cargo     = cols[3].trim()
            val abono     = cols[4].trim()
            val balance   = cols.getOrNull(5)?.trim()

            val date = parseDdmmyyyy(rawDate) ?: run { skipped++; continue }
            if (desc.isBlank()) { skipped++; continue }

            val cargoAmt = parseAmount(cargo)
            val abonoAmt = parseAmount(abono)

            if (cargoAmt == null && abonoAmt == null) { skipped++; continue }

            val (amount, type) = when {
                (cargoAmt ?: 0.0) > 0.0 -> cargoAmt!! to "OUT"
                (abonoAmt ?: 0.0) > 0.0 -> abonoAmt!! to "IN"
                else -> { skipped++; continue }
            }

            rows += StagingTransactionEntity(
                sourceFile    = sourceFilename,
                accountNumber = accountNumber,
                originalDate  = date,
                description   = desc,
                docNumber     = docNumber,
                amount        = amount,
                balance       = balance?.let { parseAmount(it.removePrefix("+").removePrefix("-")) },
                type          = type,
            )
        }

        return ParseResult(rows, accountNumber, dateFrom, dateTo, skipped)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** "21042026" → "2026-04-21"; accepts optional leading spaces */
    private fun parseDdmmyyyy(raw: String): String? {
        val s = raw.filter { it.isDigit() }
        if (s.length != 8) return null
        val dd = s.substring(0, 2)
        val mm = s.substring(2, 4)
        val yy = s.substring(4, 8)
        return "$yy-$mm-$dd"
    }

    /** "0000000005230,00" → 5230.0; blank / all-zeros → null */
    private fun parseAmount(raw: String): Double? {
        val s = raw.trim().replace(".", "").replace(",", ".").trimStart('0')
        if (s.isBlank() || s == "." || s == "0.00") return null
        return s.toDoubleOrNull()
    }
}

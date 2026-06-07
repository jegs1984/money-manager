package com.moneymanager.data.parser

import com.moneymanager.data.entity.StagingCCTransactionEntity
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.InputStream

/**
 * Parses a Scotiabank Chile Credit Card statement (`.xls`, HSSF format)
 * using Apache POI — no LibreOffice required on Android.
 *
 * Handles the wide (~75 column) Scotiabank "Estado de Cuenta Nacional
 * de Tarjeta de Crédito" layout:
 *   • Header block → card holder / card number / statement date
 *   • Data rows    → date | description | location | ref | amount | cuota
 */
object XlsParser {

    data class ParseResult(
        val rows: List<StagingCCTransactionEntity>,
        val cardHolder: String?,
        val cardNumber: String?,
        val statementDate: String?,
        val skipped: Int,
    )

    fun parse(stream: InputStream, sourceFilename: String?): ParseResult {
        val wb    = HSSFWorkbook(stream)
        val sheet = wb.getSheetAt(0)

        var cardHolder: String?    = null
        var cardNumber: String?    = null
        var statementDate: String? = null
        var skipped                = 0
        val rows = mutableListOf<StagingCCTransactionEntity>()

        // ── Pass 1: scan header rows (first 20) for metadata ────────────────
        for (rIdx in 0..minOf(20, sheet.lastRowNum)) {
            val row = sheet.getRow(rIdx) ?: continue
            val c0  = cellStr(row, 0)
            val c1  = cellStr(row, 1)
            when {
                c0.contains("Titular", ignoreCase = true) -> cardHolder = c1.trim()
                c0.contains("Tarjeta", ignoreCase = true) ||
                c0.contains("N°", ignoreCase = true) && c1.any { it.isDigit() } ->
                    cardNumber = c1.trim()
                c0.contains("Fecha", ignoreCase = true) && c0.contains("Estado", ignoreCase = true) ->
                    statementDate = parseDate(c1.trim())
            }
        }

        // ── Pass 2: find data rows (col 0 looks like a date, amount present) ──
        for (rIdx in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rIdx) ?: continue
            val rawDate = cellStr(row, 0).trim()
            val date    = parseDate(rawDate) ?: continue   // skip non-date rows

            val description = cellStr(row, 1).trim().ifBlank { skipped++; continue }
            val location    = cellStr(row, 2).trim().takeIf { it.isNotBlank() }
            val refCode     = cellStr(row, 3).trim().takeIf { it.isNotBlank() }
            val rawAmount   = cellStr(row, 4).trim()
            val rawCuota    = cellStr(row, 5).trim()   // e.g. "06/06"

            val amount = parseClpAmount(rawAmount) ?: run { skipped++; continue }

            // Negative amounts in the file → payments/credits → IN
            val type   = if (amount < 0) "IN" else "OUT"
            val absAmt = kotlin.math.abs(amount)

            var cuotaCurrent: Int? = null
            var cuotaTotal: Int?   = null
            var cuotaValue: Double? = null
            if (rawCuota.contains("/")) {
                val parts = rawCuota.split("/")
                cuotaCurrent = parts[0].trim().toIntOrNull()
                cuotaTotal   = parts[1].trim().toIntOrNull()
            }
            val rawInstVal = cellStr(row, 6).trim()
            cuotaValue = parseClpAmount(rawInstVal)?.let { kotlin.math.abs(it) }

            rows += StagingCCTransactionEntity(
                sourceFile         = sourceFilename,
                cardNumber         = cardNumber,
                cardHolder         = cardHolder,
                statementDate      = statementDate,
                originalDate       = date,
                description        = description,
                location           = location,
                refCode            = refCode,
                amount             = absAmt,
                installmentCurrent = cuotaCurrent,
                installmentTotal   = cuotaTotal,
                installmentValue   = cuotaValue,
                type               = type,
            )
        }

        wb.close()
        return ParseResult(rows, cardHolder, cardNumber, statementDate, skipped)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cellStr(row: org.apache.poi.ss.usermodel.Row, col: Int): String {
        val cell = row.getCell(col) ?: return ""
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.Cell.CELL_TYPE_STRING  -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.Cell.CELL_TYPE_NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    val d = cell.dateCellValue
                    "%04d-%02d-%02d".format(
                        d.year + 1900, d.month + 1, d.date
                    )
                } else {
                    cell.numericCellValue.toLong().toString()
                }
            }
            else -> ""
        }
    }

    /** Accepts "21/04/2026", "21-04-2026", "2026-04-21" → "2026-04-21" */
    private fun parseDate(raw: String): String? {
        // Already ISO
        if (raw.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return raw
        // dd/mm/yyyy or dd-mm-yyyy
        val m = Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})""").find(raw) ?: return null
        val (dd, mm, yyyy) = m.destructured
        return "$yyyy-${mm.padStart(2,'0')}-${dd.padStart(2,'0')}"
    }

    /**
     * CLP amounts use period as thousands sep and comma as decimal:
     * "$ 42.720" = 42720, "$ -1.500,00" = -1500
     * Returns null for blank / non-numeric cells.
     */
    private fun parseClpAmount(raw: String): Double? {
        val s = raw.replace("$", "").replace(".", "").replace(",", ".").trim()
        if (s.isBlank()) return null
        return s.toDoubleOrNull()
    }
}

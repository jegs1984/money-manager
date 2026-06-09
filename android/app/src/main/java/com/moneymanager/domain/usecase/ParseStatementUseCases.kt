package com.moneymanager.domain.usecase

import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.data.db.StagingCCTransactionEntity
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Parses Scotiabank Chile debit .dat (semicolon-delimited) files.
 * Mirrors services.py parse_scotiabank_statement.
 *
 * Header lines start with ';'.
 * Column row contains tokens: Fecha, Descripcion, NroDoc, Cargos, Abonos, Saldo.
 * Data rows: DDMMYYYY;Description;DocNum;Cargo;Abono;Saldo
 */
class ParseDebitStatementUseCase @Inject constructor() {

    data class ParseResult(
        val rows: List<StagingTransactionEntity>,
        val skipped: Int,
        val accountNumber: String,
        val dateFrom: String,
        val dateTo: String,
    )

    fun parse(stream: InputStream, sourceFile: String = ""): ParseResult {
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
            .replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')

        val meta = parseHeader(lines)
        val accountNumber = meta["account_number"] ?: ""

        val HEADER_TOKENS = setOf("fecha", "descripcion", "nrodoc", "cargos", "abonos", "saldo")
        val rows = mutableListOf<StagingTransactionEntity>()
        var skipped = 0

        for (line in lines) {
            val cols = line.split(';')
            if (cols.isEmpty()) continue

            val first = cols[0]
            val firstTrimmed = first.trim()

            if (firstTrimmed.startsWith(';') || firstTrimmed.isEmpty()) continue
            val token = firstTrimmed.replace(Regex("[\\s.]"), "").lowercase()
            if (token in HEADER_TOKENS) continue
            if (cols.size < 5) { skipped++; continue }

            val dateObj = parseDate(firstTrimmed) ?: run { skipped++; return@for }
            val description = cols[1].trim().replace(Regex("\\s+"), " ")
            if (description.isEmpty()) { skipped++; continue }

            val docRaw = cols.getOrElse(2) { "" }.trim()
            val docNumber = docRaw.takeIf { it.isNotEmpty() && !it.all { c -> c == '0' } }

            val cargo = parseAmount(cols.getOrElse(3) { "" })
            val abono = parseAmount(cols.getOrElse(4) { "" })
            val balance = cols.getOrElse(5) { "" }.trim().takeIf { it.isNotEmpty() }?.let { parseAmount(it) }

            val (type, amount) = when {
                cargo > BigDecimal.ZERO && abono > BigDecimal.ZERO -> "OUT" to cargo
                cargo > BigDecimal.ZERO -> "OUT" to cargo
                abono > BigDecimal.ZERO -> "IN"  to abono
                else -> { skipped++; continue }
            }

            rows += StagingTransactionEntity(
                sourceFile    = sourceFile.ifEmpty { null },
                accountNumber = accountNumber.ifEmpty { null },
                originalDate  = dateObj,
                description   = description,
                docNumber     = docNumber,
                amount        = amount.toPlainString(),
                balance       = balance?.toPlainString(),
                type          = type,
            )
        }

        return ParseResult(rows, skipped, accountNumber, meta["date_from"] ?: "", meta["date_to"] ?: "")
    }

    private fun parseHeader(lines: List<String>): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        for (line in lines) {
            val s = line.trim()
            if (!s.startsWith(';')) break
            if (':' !in s) continue
            val value = s.substringAfter(':').trim()
            val lower = s.lowercase()
            when {
                "numero cuenta" in lower -> meta["account_number"] = value
                "fecha desde"   in lower -> meta["date_from"]      = value
                "fecha hasta"   in lower -> meta["date_to"]        = value
            }
        }
        return meta
    }

    private fun parseDate(raw: String): LocalDate? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length == 8) {
            runCatching { return LocalDate.parse(digits, DateTimeFormatter.ofPattern("ddMMyyyy")) }
        }
        runCatching { return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
        return null
    }

    private fun parseAmount(raw: String): BigDecimal {
        val s = raw.trim().trimStart('+', '-').replace(',', '.').replace(Regex("[^\\d.]"), "")
        return if (s.isEmpty()) BigDecimal.ZERO else runCatching { BigDecimal(s).setScale(2) }.getOrDefault(BigDecimal.ZERO)
    }
}

/**
 * Parses Scotiabank Chile credit card XLS (via Apache POI).
 * Mirrors services.py parse_scotiabank_cc_statement.
 *
 * Column indices (0-based) in the raw sheet:
 *   5  → location
 *  11  → date (DD/MM/YYYY)
 *  16  → ref code
 *  22  → description
 *  54  → amount ($ X.XXX or $ -X.XXX)
 *  67  → installment label (NN/NN)
 *  72  → installment value
 */
class ParseCCStatementUseCase @Inject constructor() {

    data class ParseResult(
        val rows: List<StagingCCTransactionEntity>,
        val skipped: Int,
        val cardNumber: String,
        val cardHolder: String,
        val statementDate: LocalDate?,
    )

    fun parse(stream: InputStream, sourceFile: String = ""): ParseResult {
        val workbook = org.apache.poi.hssf.usermodel.HSSFWorkbook(stream)
        val sheet = workbook.getSheetAt(0)

        val allRows = (0..sheet.lastRowNum).map { r ->
            val row = sheet.getRow(r)
            (0 until (row?.lastCellNum?.toInt() ?: 0)).map { c ->
                row?.getCell(c)?.toString()?.trim() ?: ""
            }
        }

        val meta = parseCCHeader(allRows)
        val datePattern = Regex("^\\d{2}/\\d{2}/\\d{4}$")
        val installPattern = Regex("^(\\d+)/(\\d+)$")
        val SKIP = setOf("período facturado", "pagar hasta", "período de facturación anterior")

        val rows = mutableListOf<StagingCCTransactionEntity>()
        var skipped = 0

        for (row in allRows) {
            if (row.size <= 22) continue
            val dateRaw = row.getOrElse(11) { "" }
            if (!dateRaw.matches(datePattern)) continue

            val txDate = runCatching {
                LocalDate.parse(dateRaw, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }.getOrNull() ?: run { skipped++; continue }

            val description = row.getOrElse(22) { "" }.replace(Regex("\\s+"), " ").trim()
            if (description.isEmpty() || description.lowercase() in SKIP) { skipped++; continue }

            val amountRaw = row.getOrElse(54) { "" }
            val amount = parseCLPAmount(amountRaw) ?: run { skipped++; continue }

            val location = row.getOrElse(5) { "" }.replace(Regex("\\s+"), " ").trim()
            val refCode  = row.getOrElse(16) { "" }.trim()

            val instRaw = row.getOrElse(67) { "" }
            val instMatch = installPattern.find(instRaw)
            val instCurrent = instMatch?.groupValues?.get(1)?.toIntOrNull()
            val instTotal   = instMatch?.groupValues?.get(2)?.toIntOrNull()
            val instValue   = parseCLPAmount(row.getOrElse(72) { "" })

            val type = if (amount < BigDecimal.ZERO) "IN" else "OUT"
            val absAmount = amount.abs()

            rows += StagingCCTransactionEntity(
                sourceFile         = sourceFile.ifEmpty { null },
                cardNumber         = meta.cardNumber.ifEmpty { null },
                cardHolder         = meta.cardHolder.ifEmpty { null },
                statementDate      = meta.statementDate,
                originalDate       = txDate,
                description        = description,
                location           = location.ifEmpty { null },
                refCode            = refCode.ifEmpty { null },
                amount             = absAmount.toPlainString(),
                installmentCurrent = instCurrent,
                installmentTotal   = instTotal,
                installmentValue   = instValue?.abs()?.toPlainString(),
                type               = type,
            )
        }

        workbook.close()
        return ParseResult(rows, skipped, meta.cardNumber, meta.cardHolder, meta.statementDate)
    }

    private data class CCMeta(val cardHolder: String = "", val cardNumber: String = "", val statementDate: LocalDate? = null)

    private fun parseCCHeader(rows: List<List<String>>): CCMeta {
        for (row in rows.take(10)) {
            for (cell in row) {
                val upper = cell.uppercase()
                if ("VISA" !in upper && "MASTER" !in upper && "XXXX" !in upper) continue
                val parts = cell.split('\n', '\\').map { it.trim() }.filter { it.isNotEmpty() }
                val holder = parts.getOrElse(0) { "" }
                val number = parts.getOrElse(1) { "" }.let { p ->
                    Regex("[A-Z]+\\s+[\\dX-]+").find(p)?.value ?: p
                }
                val stmtDate = parts.getOrElse(2) { "" }.let {
                    runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()
                }
                return CCMeta(holder, number, stmtDate)
            }
        }
        return CCMeta()
    }

    private fun parseCLPAmount(raw: String): BigDecimal? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val negative = s.contains('-')
        val cleaned = s.replace("$", "").replace("-", "").trim()
            .replace(".", "").replace(",", ".")
            .replace(Regex("[^\\d.]"), "")
        if (cleaned.isEmpty()) return null
        return runCatching {
            val v = BigDecimal(cleaned).setScale(2)
            if (negative) v.negate() else v
        }.getOrNull()
    }
}

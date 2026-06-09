package com.moneymanager.domain.usecase

import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.data.db.StagingCCTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Mirrors services.py get_duplicate_staging_ids.
 * Compares each staging row against the active period's committed transactions
 * by the triple (date, amount, description).
 */
class DetectStagingDuplicatesUseCase @Inject constructor(
    private val repo: FinanceRepository,
) {
    suspend fun forDebit(rows: List<StagingTransactionEntity>): Set<Long> {
        val period = repo.getActivePeriod() ?: return emptySet()
        return rows
            .filter { repo.isDuplicateInPeriod(period.id, it.originalDate, it.amount.toBigDecimal(), it.description) }
            .map { it.id }
            .toSet()
    }

    suspend fun forCC(rows: List<StagingCCTransactionEntity>): Set<Long> {
        val period = repo.getActivePeriod() ?: return emptySet()
        return rows
            .filter { repo.isDuplicateInPeriod(period.id, it.originalDate, it.amount.toBigDecimal(), it.description) }
            .map { it.id }
            .toSet()
    }
}

private fun String.toBigDecimal(): BigDecimal = BigDecimal(this)

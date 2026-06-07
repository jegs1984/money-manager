package com.moneymanager.data.repository

import com.moneymanager.data.db.dao.*
import com.moneymanager.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val periodDao:              PeriodDao,
    private val categoryDao:            CategoryDao,
    private val budgetItemDao:          BudgetItemDao,
    private val transactionDao:         TransactionDao,
    private val stagingDao:             StagingTransactionDao,
    private val stagingCCDao:           StagingCCTransactionDao,
) {

    // ── Periods ───────────────────────────────────────────────────────────────

    fun observePeriods(): Flow<List<PeriodEntity>> = periodDao.observeAll()

    suspend fun getPeriods(): List<PeriodEntity> = periodDao.getAll()

    suspend fun getActivePeriod(): PeriodEntity? = periodDao.getActive()

    suspend fun savePeriod(p: PeriodEntity): Long {
        return if (p.id == 0L) {
            if (p.isActive) periodDao.deactivateAll()
            periodDao.insert(p)
        } else {
            if (p.isActive) periodDao.deactivateAll()
            periodDao.update(p)
            p.id
        }
    }

    suspend fun deletePeriod(p: PeriodEntity) = periodDao.delete(p)

    // ── Categories ────────────────────────────────────────────────────────────

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun saveCategory(c: CategoryEntity): Long =
        if (c.id == 0L) categoryDao.insert(c) else { categoryDao.update(c); c.id }

    suspend fun deleteCategory(c: CategoryEntity) = categoryDao.delete(c)

    // ── Budget Items ──────────────────────────────────────────────────────────

    fun observeBudgetItemsWithStats(periodId: Long): Flow<List<BudgetItemWithStats>> =
        budgetItemDao.observeWithStatsByPeriod(periodId)

    suspend fun getBudgetItem(id: Long): BudgetItemEntity? = budgetItemDao.getById(id)

    suspend fun saveBudgetItem(item: BudgetItemEntity): Long =
        if (item.id == 0L) budgetItemDao.insert(item)
        else { budgetItemDao.update(item); item.id }

    suspend fun deleteBudgetItem(item: BudgetItemEntity) = budgetItemDao.delete(item)

    // ── Transactions ──────────────────────────────────────────────────────────

    fun observeTransactions(limit: Int = 50, offset: Int = 0): Flow<List<TransactionEntity>> =
        transactionDao.observePaged(limit, offset)

    fun observeTransactionsByBudgetItem(budgetItemId: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeByBudgetItem(budgetItemId)

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun saveTransaction(tx: TransactionEntity): Long =
        if (tx.id == 0L) transactionDao.insert(tx)
        else { transactionDao.update(tx); tx.id }

    suspend fun deleteTransaction(tx: TransactionEntity) = transactionDao.delete(tx)

    // ── Staging (bank DAT) ────────────────────────────────────────────────────

    fun observePendingStaging(): Flow<List<StagingTransactionEntity>> =
        stagingDao.observePending()

    suspend fun insertStagingRows(rows: List<StagingTransactionEntity>) =
        stagingDao.insertAll(rows)

    suspend fun assignStagingCategory(id: Long, categoryId: Long) =
        stagingDao.assignCategory(id, categoryId)

    /**
     * Commit all staging rows that have an assigned category:
     *  1. Look up the matching BudgetItem for the period that covers the transaction date.
     *  2. Insert a Transaction linked to it.
     *  3. Mark staging rows processed.
     */
    suspend fun commitStagingBatch(
        entries: List<StagingCommitEntry>,
        budgetItems: List<BudgetItemWithStats>,
    ): Int {
        val pending = stagingDao.getPending()
        val idMap   = entries.associateBy { it.stagingId }
        var count   = 0
        val committed = mutableListOf<Long>()

        for (row in pending) {
            val entry = idMap[row.id] ?: continue
            // Find BudgetItem in same period that matches this category
            val bi = budgetItems.firstOrNull { it.categoryId == entry.categoryId } ?: continue
            transactionDao.insert(
                TransactionEntity(
                    budgetItemId = bi.id,
                    date         = row.originalDate,
                    realAmount   = row.amount,
                    description  = row.description,
                )
            )
            committed += row.id
            count++
        }
        if (committed.isNotEmpty()) stagingDao.markProcessed(committed)
        return count
    }

    // ── Staging CC ────────────────────────────────────────────────────────────

    fun observePendingStagingCC(): Flow<List<StagingCCTransactionEntity>> =
        stagingCCDao.observePending()

    suspend fun insertStagingCCRows(rows: List<StagingCCTransactionEntity>) =
        stagingCCDao.insertAll(rows)

    suspend fun assignStagingCCCategory(id: Long, categoryId: Long) =
        stagingCCDao.assignCategory(id, categoryId)

    suspend fun commitCCStagingBatch(
        entries: List<StagingCommitEntry>,
        budgetItems: List<BudgetItemWithStats>,
    ): Int {
        val pending = stagingCCDao.getPending()
        val idMap   = entries.associateBy { it.stagingId }
        var count   = 0
        val committed = mutableListOf<Long>()

        for (row in pending) {
            val entry = idMap[row.id] ?: continue
            val bi    = budgetItems.firstOrNull { it.categoryId == entry.categoryId } ?: continue
            transactionDao.insert(
                TransactionEntity(
                    budgetItemId = bi.id,
                    date         = row.originalDate,
                    realAmount   = row.amount,
                    description  = row.description,
                    notes        = row.location,
                )
            )
            committed += row.id
            count++
        }
        if (committed.isNotEmpty()) stagingCCDao.markProcessed(committed)
        return count
    }

    // ── Dashboard calculation (mirrors services.calculate_safe_to_spend) ─────

    data class DashboardStats(
        val totalIncome:    Double,
        val totalExpenses:  Double,
        val projectedIn:    Double,
        val projectedOut:   Double,
        val safeToSpend:    Double,
        val burnRate:       Double,    // 0..1
    )

    suspend fun getDashboardStats(periodId: Long): DashboardStats {
        val items = budgetItemDao.getWithStatsByPeriod(periodId)
        val totalIncome   = items.filter { it.type == "IN"  }.sumOf { it.totalReal }
        val totalExpenses = items.filter { it.type == "OUT" }.sumOf { it.totalReal }
        val projectedIn   = items.filter { it.type == "IN"  }.sumOf { it.projectedAmount }
        val projectedOut  = items.filter { it.type == "OUT" }.sumOf { it.projectedAmount }
        val safeToSpend   = totalIncome - totalExpenses
        val burnRate      = if (totalIncome > 0) totalExpenses / totalIncome else 0.0
        return DashboardStats(totalIncome, totalExpenses, projectedIn, projectedOut, safeToSpend, burnRate)
    }
}

data class StagingCommitEntry(val stagingId: Long, val categoryId: Long)

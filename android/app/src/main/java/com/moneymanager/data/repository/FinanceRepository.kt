package com.moneymanager.data.repository

import com.moneymanager.data.db.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────
// Domain result models (thin wrappers — no extra layer needed at this scale)
// ─────────────────────────────────────────────

data class PeriodStats(
    val period: PeriodEntity,
    val totalIncomeProjected: BigDecimal,
    val totalIncomeReal: BigDecimal,
    val totalExpenseProjected: BigDecimal,
    val totalExpenseReal: BigDecimal,
) {
    val safeToSpend: BigDecimal get() = totalIncomeReal - totalExpenseReal
    val projection: BigDecimal  get() = totalIncomeProjected - totalExpenseProjected
    val burnRate: BigDecimal    get() =
        if (totalIncomeReal > BigDecimal.ZERO)
            (totalExpenseReal / totalIncomeReal * BigDecimal("100")).setScale(1, java.math.RoundingMode.HALF_UP)
        else BigDecimal.ZERO
}

data class BudgetItemWithStats(
    val item: BudgetItemEntity,
    val category: CategoryEntity,
    val totalReal: BigDecimal,
) {
    val diff: BigDecimal get() = when (item.type) {
        "IN"  -> totalReal - item.projectedAmount.toBigDecimal()
        else  -> item.projectedAmount.toBigDecimal() - totalReal
    }
}

// ─────────────────────────────────────────────
// FinanceRepository
// ─────────────────────────────────────────────
@Singleton
class FinanceRepository @Inject constructor(
    private val periodDao: PeriodDao,
    private val categoryDao: CategoryDao,
    private val budgetItemDao: BudgetItemDao,
    private val transactionDao: TransactionDao,
    private val stagingDao: StagingTransactionDao,
    private val stagingCCDao: StagingCCTransactionDao,
    private val db: AppDatabase,
) {

    // ── Periods ───────────────────────────────────────────────────────────

    fun observePeriods(): Flow<List<PeriodEntity>> = periodDao.observeAll()
    suspend fun getPeriods(): List<PeriodEntity> = periodDao.getAll()
    suspend fun getActivePeriod(): PeriodEntity? =
        periodDao.getActive() ?: periodDao.getAll().firstOrNull()
    suspend fun findPeriodByDate(date: LocalDate): PeriodEntity? = periodDao.findByDate(date)
    suspend fun savePeriod(period: PeriodEntity) = periodDao.insert(period)
    suspend fun updatePeriod(period: PeriodEntity) = periodDao.update(period)
    suspend fun deletePeriod(period: PeriodEntity) = periodDao.delete(period)

    // ── Categories ────────────────────────────────────────────────────────

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()
    suspend fun saveCategory(category: CategoryEntity) = categoryDao.insert(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun getOrCreateUnplanned(): CategoryEntity {
        return categoryDao.getByName("Unplanned/Extra") ?: run {
            val id = categoryDao.insert(CategoryEntity(name = "Unplanned/Extra", group = "Gastos"))
            categoryDao.getById(id)!!
        }
    }

    // ── BudgetItems ───────────────────────────────────────────────────────

    fun observeBudgetItems(periodId: Long): Flow<List<BudgetItemEntity>> =
        budgetItemDao.observeByPeriod(periodId)

    suspend fun getOrCreateBudgetItem(periodId: Long, categoryId: Long, type: String): BudgetItemEntity {
        return budgetItemDao.find(periodId, categoryId) ?: run {
            val entity = BudgetItemEntity(periodId = periodId, categoryId = categoryId, type = type)
            val id = budgetItemDao.insert(entity)
            budgetItemDao.find(periodId, categoryId)!!
        }
    }

    // ── Transactions ──────────────────────────────────────────────────────

    fun observeTransactions(periodId: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeByPeriod(periodId)

    suspend fun insertTransaction(tx: TransactionEntity): Long = transactionDao.insert(tx)

    /**
     * Mirror of services.py calculate_safe_to_spend.
     */
    suspend fun calculateStats(period: PeriodEntity): PeriodStats {
        val items = budgetItemDao.getByPeriod(period.id)
        val txsByItem = items.associate { item ->
            item.id to transactionDao.getByBudgetItem(item.id)
                .sumOf { it.realAmount.toBigDecimal() }
        }

        val projIn  = items.filter { it.type == "IN"  }.sumOf { it.projectedAmount.toBigDecimal() }
        val projOut = items.filter { it.type == "OUT" }.sumOf { it.projectedAmount.toBigDecimal() }
        val realIn  = items.filter { it.type == "IN"  }.sumOf { txsByItem[it.id] ?: BigDecimal.ZERO }
        val realOut = items.filter { it.type == "OUT" }.sumOf { txsByItem[it.id] ?: BigDecimal.ZERO }

        return PeriodStats(period, projIn, realIn, projOut, realOut)
    }

    suspend fun getBudgetItemsWithStats(periodId: Long): List<BudgetItemWithStats> {
        val items = budgetItemDao.getByPeriod(periodId)
        val cats  = categoryDao.getAll().associateBy { it.id }
        return items.mapNotNull { item ->
            val cat = cats[item.categoryId] ?: return@mapNotNull null
            val total = transactionDao.getByBudgetItem(item.id).sumOf { it.realAmount.toBigDecimal() }
            BudgetItemWithStats(item, cat, total)
        }
    }

    // ── Duplicate detection (mirrors get_duplicate_staging_ids) ───────────

    suspend fun isDuplicateInPeriod(
        periodId: Long,
        date: LocalDate,
        amount: BigDecimal,
        description: String,
    ): Boolean = transactionDao.findDuplicate(
        periodId, date, amount.toPlainString(), description
    ) != null

    // ── Staging (Debit) ───────────────────────────────────────────────────

    fun observePendingStaging(): Flow<List<StagingTransactionEntity>> = stagingDao.observePending()
    suspend fun getPendingStaging(): List<StagingTransactionEntity> = stagingDao.getPending()
    suspend fun insertStagingRows(rows: List<StagingTransactionEntity>) = stagingDao.insertAll(rows)

    /**
     * Mirrors process_staging_batch.
     * removeIds: rows user chose to discard (duplicate → remove).
     * entries:   list of (stagingId, categoryId).
     * Returns count of committed transactions.
     */
    suspend fun processStagingBatch(
        entries: List<Pair<Long, Long>>,
        removeIds: Set<Long> = emptySet(),
    ): Int = db.withTransaction {
        if (removeIds.isNotEmpty()) stagingDao.deleteByIds(removeIds.toList())

        var committed = 0
        for ((stagingId, categoryId) in entries) {
            val stx = stagingDao.getPending().firstOrNull { it.id == stagingId } ?: continue
            val period = findPeriodByDate(stx.originalDate) ?: continue
            val cat    = categoryDao.getById(categoryId) ?: getOrCreateUnplanned()
            val bItem  = getOrCreateBudgetItem(period.id, cat.id, stx.type)
            transactionDao.insert(
                TransactionEntity(
                    budgetItemId = bItem.id,
                    date         = stx.originalDate,
                    realAmount   = stx.amount,
                    description  = stx.description,
                    notes        = null,
                )
            )
            stagingDao.markProcessed(stagingId, cat.id)
            committed++
        }
        committed
    }

    // ── Staging (CC) ──────────────────────────────────────────────────────

    fun observePendingCCStaging(): Flow<List<StagingCCTransactionEntity>> = stagingCCDao.observePending()
    suspend fun getPendingCCStaging(): List<StagingCCTransactionEntity> = stagingCCDao.getPending()
    suspend fun insertCCStagingRows(rows: List<StagingCCTransactionEntity>) = stagingCCDao.insertAll(rows)

    suspend fun processCCStagingBatch(
        entries: List<Pair<Long, Long>>,
        removeIds: Set<Long> = emptySet(),
    ): Int = db.withTransaction {
        if (removeIds.isNotEmpty()) stagingCCDao.deleteByIds(removeIds.toList())

        var committed = 0
        for ((stagingId, categoryId) in entries) {
            val stx = stagingCCDao.getPending().firstOrNull { it.id == stagingId } ?: continue
            val period = findPeriodByDate(stx.originalDate) ?: continue
            val cat    = categoryDao.getById(categoryId) ?: getOrCreateUnplanned()
            val bItem  = getOrCreateBudgetItem(period.id, cat.id, stx.type)
            transactionDao.insert(
                TransactionEntity(
                    budgetItemId = bItem.id,
                    date         = stx.originalDate,
                    realAmount   = stx.amount,
                    description  = stx.description,
                    notes        = listOfNotNull(
                        stx.cardNumber?.let { "[CC] $it" },
                        stx.location,
                    ).joinToString(" ").ifBlank { null },
                )
            )
            stagingCCDao.markProcessed(stagingId, cat.id)
            committed++
        }
        committed
    }
}

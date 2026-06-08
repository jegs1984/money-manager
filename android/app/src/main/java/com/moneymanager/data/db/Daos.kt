package com.moneymanager.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// ─────────────────────────────────────────────
// PeriodDao
// ─────────────────────────────────────────────
@Dao
interface PeriodDao {
    @Query("SELECT * FROM finance_period ORDER BY start_date DESC")
    fun observeAll(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM finance_period ORDER BY start_date DESC")
    suspend fun getAll(): List<PeriodEntity>

    @Query("SELECT * FROM finance_period WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): PeriodEntity?

    @Query("""
        SELECT * FROM finance_period
        WHERE start_date <= :date AND end_date >= :date
        ORDER BY start_date DESC
        LIMIT 1
    """)
    suspend fun findByDate(date: LocalDate): PeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: PeriodEntity): Long

    @Update
    suspend fun update(period: PeriodEntity)

    @Delete
    suspend fun delete(period: PeriodEntity)
}

// ─────────────────────────────────────────────
// CategoryDao
// ─────────────────────────────────────────────
@Dao
interface CategoryDao {
    @Query("SELECT * FROM finance_category ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM finance_category ORDER BY name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM finance_category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM finance_category WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

// ─────────────────────────────────────────────
// BudgetItemDao
// ─────────────────────────────────────────────
@Dao
interface BudgetItemDao {
    @Query("SELECT * FROM finance_budget_item WHERE period_id = :periodId ORDER BY type, category_id")
    fun observeByPeriod(periodId: Long): Flow<List<BudgetItemEntity>>

    @Query("SELECT * FROM finance_budget_item WHERE period_id = :periodId")
    suspend fun getByPeriod(periodId: Long): List<BudgetItemEntity>

    @Query("SELECT * FROM finance_budget_item WHERE period_id = :periodId AND category_id = :categoryId LIMIT 1")
    suspend fun find(periodId: Long, categoryId: Long): BudgetItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BudgetItemEntity): Long

    @Update
    suspend fun update(item: BudgetItemEntity)

    @Delete
    suspend fun delete(item: BudgetItemEntity)
}

// ─────────────────────────────────────────────
// TransactionDao
// ─────────────────────────────────────────────
@Dao
interface TransactionDao {
    @Query("""
        SELECT * FROM finance_transaction
        WHERE budget_item_id IN (
            SELECT id FROM finance_budget_item WHERE period_id = :periodId
        )
        ORDER BY date DESC
    """)
    fun observeByPeriod(periodId: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM finance_transaction
        WHERE budget_item_id IN (
            SELECT id FROM finance_budget_item WHERE period_id = :periodId
        )
    """)
    suspend fun getByPeriod(periodId: Long): List<TransactionEntity>

    @Query("SELECT * FROM finance_transaction WHERE budget_item_id = :budgetItemId ORDER BY date DESC")
    suspend fun getByBudgetItem(budgetItemId: Long): List<TransactionEntity>

    /**
     * Duplicate-detection query: mirrors services.py get_duplicate_staging_ids.
     * Returns matching transaction IDs for a given (date, amount, description) triple.
     */
    @Query("""
        SELECT id FROM finance_transaction
        WHERE budget_item_id IN (
            SELECT id FROM finance_budget_item WHERE period_id = :periodId
        )
        AND date = :date
        AND real_amount = :amount
        AND description = :description
        LIMIT 1
    """)
    suspend fun findDuplicate(periodId: Long, date: LocalDate, amount: String, description: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Delete
    suspend fun delete(tx: TransactionEntity)
}

// ─────────────────────────────────────────────
// StagingTransactionDao
// ─────────────────────────────────────────────
@Dao
interface StagingTransactionDao {
    @Query("SELECT * FROM finance_staging_transaction WHERE is_processed = 0 ORDER BY original_date ASC")
    fun observePending(): Flow<List<StagingTransactionEntity>>

    @Query("SELECT * FROM finance_staging_transaction WHERE is_processed = 0 ORDER BY original_date ASC")
    suspend fun getPending(): List<StagingTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StagingTransactionEntity>)

    @Query("UPDATE finance_staging_transaction SET is_processed = 1, assigned_category_id = :categoryId WHERE id = :id")
    suspend fun markProcessed(id: Long, categoryId: Long)

    @Query("DELETE FROM finance_staging_transaction WHERE id IN (:ids) AND is_processed = 0")
    suspend fun deleteByIds(ids: List<Long>)
}

// ─────────────────────────────────────────────
// StagingCCTransactionDao
// ─────────────────────────────────────────────
@Dao
interface StagingCCTransactionDao {
    @Query("SELECT * FROM finance_staging_cc_transaction WHERE is_processed = 0 ORDER BY original_date ASC")
    fun observePending(): Flow<List<StagingCCTransactionEntity>>

    @Query("SELECT * FROM finance_staging_cc_transaction WHERE is_processed = 0 ORDER BY original_date ASC")
    suspend fun getPending(): List<StagingCCTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StagingCCTransactionEntity>)

    @Query("UPDATE finance_staging_cc_transaction SET is_processed = 1, assigned_category_id = :categoryId WHERE id = :id")
    suspend fun markProcessed(id: Long, categoryId: Long)

    @Query("DELETE FROM finance_staging_cc_transaction WHERE id IN (:ids) AND is_processed = 0")
    suspend fun deleteByIds(ids: List<Long>)
}

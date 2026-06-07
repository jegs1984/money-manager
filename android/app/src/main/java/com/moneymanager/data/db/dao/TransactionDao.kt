package com.moneymanager.data.db.dao

import androidx.room.*
import com.moneymanager.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("""
        SELECT * FROM finance_transaction
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observePaged(limit: Int = 50, offset: Int = 0): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM finance_transaction WHERE budget_item_id = :budgetItemId ORDER BY date DESC")
    fun observeByBudgetItem(budgetItemId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM finance_transaction WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(txList: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)
}

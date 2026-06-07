package com.moneymanager.data.db.dao

import androidx.room.*
import com.moneymanager.data.entity.StagingCCTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StagingCCTransactionDao {

    @Query("SELECT * FROM finance_staging_cc_transaction WHERE is_processed = 0 ORDER BY original_date")
    fun observePending(): Flow<List<StagingCCTransactionEntity>>

    @Query("SELECT * FROM finance_staging_cc_transaction WHERE is_processed = 0 ORDER BY original_date")
    suspend fun getPending(): List<StagingCCTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StagingCCTransactionEntity>)

    @Query("UPDATE finance_staging_cc_transaction SET assigned_category_id = :categoryId WHERE id = :id")
    suspend fun assignCategory(id: Long, categoryId: Long)

    @Query("UPDATE finance_staging_cc_transaction SET is_processed = 1 WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<Long>)

    @Query("DELETE FROM finance_staging_cc_transaction WHERE is_processed = 1")
    suspend fun clearProcessed()
}

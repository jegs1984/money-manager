package com.moneymanager.data.db.dao

import androidx.room.*
import com.moneymanager.data.entity.PeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {

    @Query("SELECT * FROM finance_period ORDER BY start_date DESC")
    fun observeAll(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM finance_period ORDER BY start_date DESC")
    suspend fun getAll(): List<PeriodEntity>

    @Query("SELECT * FROM finance_period WHERE id = :id")
    suspend fun getById(id: Long): PeriodEntity?

    @Query("SELECT * FROM finance_period WHERE is_active = 1 ORDER BY start_date DESC LIMIT 1")
    suspend fun getActive(): PeriodEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(period: PeriodEntity): Long

    @Update
    suspend fun update(period: PeriodEntity)

    @Delete
    suspend fun delete(period: PeriodEntity)

    /** Deactivate all periods before setting a new active one */
    @Query("UPDATE finance_period SET is_active = 0")
    suspend fun deactivateAll()
}

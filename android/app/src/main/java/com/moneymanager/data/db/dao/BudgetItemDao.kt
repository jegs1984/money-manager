package com.moneymanager.data.db.dao

import androidx.room.*
import com.moneymanager.data.entity.BudgetItemEntity
import kotlinx.coroutines.flow.Flow

// Flat projection used for dashboard calculations
data class BudgetItemWithStats(
    val id:              Long,
    val periodId:        Long,
    val categoryId:      Long,
    val categoryName:    String,
    val categoryGroup:   String,
    val type:            String,
    val projectedAmount: Double,
    val totalReal:       Double,
)

@Dao
interface BudgetItemDao {

    @Query("""
        SELECT
            bi.id,
            bi.period_id        AS periodId,
            bi.category_id      AS categoryId,
            c.name              AS categoryName,
            c.`group`           AS categoryGroup,
            bi.type,
            bi.projected_amount AS projectedAmount,
            COALESCE(SUM(t.real_amount), 0.0) AS totalReal
        FROM finance_budget_item bi
        JOIN finance_category c ON c.id = bi.category_id
        LEFT JOIN finance_transaction t ON t.budget_item_id = bi.id
        WHERE bi.period_id = :periodId
        GROUP BY bi.id
        ORDER BY bi.type, c.`group`, c.name
    """)
    fun observeWithStatsByPeriod(periodId: Long): Flow<List<BudgetItemWithStats>>

    @Query("""
        SELECT
            bi.id,
            bi.period_id        AS periodId,
            bi.category_id      AS categoryId,
            c.name              AS categoryName,
            c.`group`           AS categoryGroup,
            bi.type,
            bi.projected_amount AS projectedAmount,
            COALESCE(SUM(t.real_amount), 0.0) AS totalReal
        FROM finance_budget_item bi
        JOIN finance_category c ON c.id = bi.category_id
        LEFT JOIN finance_transaction t ON t.budget_item_id = bi.id
        WHERE bi.period_id = :periodId
        GROUP BY bi.id
        ORDER BY bi.type, c.`group`, c.name
    """)
    suspend fun getWithStatsByPeriod(periodId: Long): List<BudgetItemWithStats>

    @Query("SELECT * FROM finance_budget_item WHERE id = :id")
    suspend fun getById(id: Long): BudgetItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: BudgetItemEntity): Long

    @Update
    suspend fun update(item: BudgetItemEntity)

    @Delete
    suspend fun delete(item: BudgetItemEntity)
}

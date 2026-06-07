package com.moneymanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "finance_budget_item",
    foreignKeys = [
        ForeignKey(
            entity      = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns  = ["period_id"],
            onDelete    = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity      = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete    = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("period_id"),
        Index("category_id"),
        Index(value = ["period_id", "category_id"], unique = true),
    ],
)
data class BudgetItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "period_id")          val periodId:        Long,
    @ColumnInfo(name = "category_id")        val categoryId:      Long,
    /** "IN" or "OUT" */
    val type: String,
    @ColumnInfo(name = "projected_amount")   val projectedAmount: Double = 0.0,
)

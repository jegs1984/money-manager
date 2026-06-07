package com.moneymanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "finance_transaction",
    foreignKeys = [
        ForeignKey(
            entity        = BudgetItemEntity::class,
            parentColumns = ["id"],
            childColumns  = ["budget_item_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("budget_item_id")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "budget_item_id") val budgetItemId: Long?,
    /** ISO-8601 yyyy-MM-dd */
    val date:        String,
    @ColumnInfo(name = "real_amount") val realAmount: Double,
    val description: String,
    val notes:       String? = null,
)

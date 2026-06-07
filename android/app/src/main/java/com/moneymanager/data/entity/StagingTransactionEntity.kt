package com.moneymanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "finance_staging_transaction",
    foreignKeys = [
        ForeignKey(
            entity        = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["assigned_category_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("assigned_category_id")],
)
data class StagingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_file")     val sourceFile:    String? = null,
    @ColumnInfo(name = "account_number")  val accountNumber: String? = null,
    @ColumnInfo(name = "original_date")   val originalDate:  String,
    val description:  String,
    @ColumnInfo(name = "doc_number")      val docNumber:     String? = null,
    val amount:       Double,
    val balance:      Double? = null,
    /** "IN" or "OUT" */
    val type:         String,
    @ColumnInfo(name = "is_processed")    val isProcessed:   Boolean = false,
    @ColumnInfo(name = "assigned_category_id") val assignedCategoryId: Long? = null,
    @ColumnInfo(name = "created_at")      val createdAt:     Long = System.currentTimeMillis(),
)

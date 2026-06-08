package com.moneymanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

// ─────────────────────────────────────────────
// Period   →  finance_period
// ─────────────────────────────────────────────
@Entity(tableName = "finance_period")
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name")       val name: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date")   val endDate: LocalDate,
    @ColumnInfo(name = "is_active")  val isActive: Boolean = true,
)

// ─────────────────────────────────────────────
// Category  →  finance_category
// NOTE: "group" is a reserved SQLite word; column is quoted in SQL but the
//       Room column name is stored as group_name to avoid conflicts.
// ─────────────────────────────────────────────
@Entity(tableName = "finance_category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)    val id: Long = 0,
    @ColumnInfo(name = "name")          val name: String,
    @ColumnInfo(name = "group_name")    val group: String,   // maps to Django db_column='"group"'
)

// ─────────────────────────────────────────────
// BudgetItem  →  finance_budget_item
// ─────────────────────────────────────────────
@Entity(
    tableName = "finance_budget_item",
    foreignKeys = [
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns  = ["period_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["category_id"],
            onDelete      = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("period_id"),
        Index("category_id"),
        Index(value = ["period_id", "category_id"], unique = true),
    ],
)
data class BudgetItemEntity(
    @PrimaryKey(autoGenerate = true)         val id: Long = 0,
    @ColumnInfo(name = "period_id")          val periodId: Long,
    @ColumnInfo(name = "category_id")        val categoryId: Long,
    /** "IN" | "OUT" */
    @ColumnInfo(name = "type")               val type: String,
    @ColumnInfo(name = "projected_amount")   val projectedAmount: String = "0.00", // stored as String, converted via BigDecimal
)

// ─────────────────────────────────────────────
// Transaction  →  finance_transaction
// ─────────────────────────────────────────────
@Entity(
    tableName = "finance_transaction",
    foreignKeys = [
        ForeignKey(
            entity = BudgetItemEntity::class,
            parentColumns = ["id"],
            childColumns  = ["budget_item_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("budget_item_id")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)     val id: Long = 0,
    @ColumnInfo(name = "budget_item_id") val budgetItemId: Long?,
    @ColumnInfo(name = "date")           val date: LocalDate,
    @ColumnInfo(name = "real_amount")    val realAmount: String,   // BigDecimal as String
    @ColumnInfo(name = "description")    val description: String,
    @ColumnInfo(name = "notes")          val notes: String? = null,
)

// ─────────────────────────────────────────────
// StagingTransaction  →  finance_staging_transaction
// ─────────────────────────────────────────────
@Entity(
    tableName = "finance_staging_transaction",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["assigned_category_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("assigned_category_id")],
)
data class StagingTransactionEntity(
    @PrimaryKey(autoGenerate = true)              val id: Long = 0,
    @ColumnInfo(name = "source_file")             val sourceFile: String? = null,
    @ColumnInfo(name = "account_number")          val accountNumber: String? = null,
    @ColumnInfo(name = "original_date")           val originalDate: LocalDate,
    @ColumnInfo(name = "description")             val description: String,
    @ColumnInfo(name = "doc_number")              val docNumber: String? = null,
    @ColumnInfo(name = "amount")                  val amount: String,               // BigDecimal as String
    @ColumnInfo(name = "balance")                 val balance: String? = null,
    @ColumnInfo(name = "type")                    val type: String,                  // "IN" | "OUT"
    @ColumnInfo(name = "is_processed")            val isProcessed: Boolean = false,
    @ColumnInfo(name = "assigned_category_id")    val assignedCategoryId: Long? = null,
    @ColumnInfo(name = "created_at")              val createdAt: Long = System.currentTimeMillis(),
)

// ─────────────────────────────────────────────
// StagingCCTransaction  →  finance_staging_cc_transaction
// ─────────────────────────────────────────────
@Entity(
    tableName = "finance_staging_cc_transaction",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns  = ["assigned_category_id"],
            onDelete      = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("assigned_category_id")],
)
data class StagingCCTransactionEntity(
    @PrimaryKey(autoGenerate = true)              val id: Long = 0,
    @ColumnInfo(name = "source_file")             val sourceFile: String? = null,
    @ColumnInfo(name = "card_number")             val cardNumber: String? = null,
    @ColumnInfo(name = "card_holder")             val cardHolder: String? = null,
    @ColumnInfo(name = "statement_date")          val statementDate: LocalDate? = null,
    @ColumnInfo(name = "original_date")           val originalDate: LocalDate,
    @ColumnInfo(name = "description")             val description: String,
    @ColumnInfo(name = "location")                val location: String? = null,
    @ColumnInfo(name = "ref_code")                val refCode: String? = null,
    @ColumnInfo(name = "amount")                  val amount: String,
    @ColumnInfo(name = "installment_current")     val installmentCurrent: Int? = null,
    @ColumnInfo(name = "installment_total")       val installmentTotal: Int? = null,
    @ColumnInfo(name = "installment_value")       val installmentValue: String? = null,
    @ColumnInfo(name = "type")                    val type: String,
    @ColumnInfo(name = "is_processed")            val isProcessed: Boolean = false,
    @ColumnInfo(name = "assigned_category_id")    val assignedCategoryId: Long? = null,
    @ColumnInfo(name = "created_at")              val createdAt: Long = System.currentTimeMillis(),
)

// ─────────────────────────────────────────────
// Convenience extension
// ─────────────────────────────────────────────
fun String.toBigDecimal(): BigDecimal = BigDecimal(this)

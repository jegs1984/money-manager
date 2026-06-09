package com.moneymanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PeriodEntity::class,
        CategoryEntity::class,
        BudgetItemEntity::class,
        TransactionEntity::class,
        StagingTransactionEntity::class,
        StagingCCTransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun periodDao(): PeriodDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetItemDao(): BudgetItemDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stagingTransactionDao(): StagingTransactionDao
    abstract fun stagingCCTransactionDao(): StagingCCTransactionDao

    companion object {
        const val DATABASE_NAME = "money_manager.db"
    }
}

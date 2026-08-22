package com.moneymanager.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.data.db.*
import com.moneymanager.data.repository.FinanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()

    /** Preserve all v1 data while introducing import provenance. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS finance_import_batch (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    source_type TEXT NOT NULL, filename TEXT NOT NULL,
                    account_reference TEXT NOT NULL, content_hash TEXT NOT NULL,
                    imported_at INTEGER NOT NULL, parser_version TEXT NOT NULL,
                    status TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_import_batch_content_hash ON finance_import_batch(content_hash)")
            db.execSQL("ALTER TABLE finance_staging_transaction ADD COLUMN batch_id INTEGER")
            db.execSQL("ALTER TABLE finance_staging_cc_transaction ADD COLUMN batch_id INTEGER")
        }
    }

    /** Align budget-item direction identity with the web ledger without data loss. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_finance_budget_item_period_id_category_id")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_finance_budget_item_period_id_category_id_type ON finance_budget_item(period_id, category_id, type)")
        }
    }

    @Provides fun periodDao(db: AppDatabase): PeriodDao               = db.periodDao()
    @Provides fun categoryDao(db: AppDatabase): CategoryDao           = db.categoryDao()
    @Provides fun budgetItemDao(db: AppDatabase): BudgetItemDao       = db.budgetItemDao()
    @Provides fun transactionDao(db: AppDatabase): TransactionDao     = db.transactionDao()
    @Provides fun stagingDao(db: AppDatabase): StagingTransactionDao  = db.stagingTransactionDao()
    @Provides fun stagingCCDao(db: AppDatabase): StagingCCTransactionDao = db.stagingCCTransactionDao()
}

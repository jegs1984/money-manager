package com.moneymanager.di

import android.content.Context
import androidx.room.Room
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
            .fallbackToDestructiveMigration()   // bump version + write proper Migration for prod
            .build()

    @Provides fun periodDao(db: AppDatabase): PeriodDao               = db.periodDao()
    @Provides fun categoryDao(db: AppDatabase): CategoryDao           = db.categoryDao()
    @Provides fun budgetItemDao(db: AppDatabase): BudgetItemDao       = db.budgetItemDao()
    @Provides fun transactionDao(db: AppDatabase): TransactionDao     = db.transactionDao()
    @Provides fun stagingDao(db: AppDatabase): StagingTransactionDao  = db.stagingTransactionDao()
    @Provides fun stagingCCDao(db: AppDatabase): StagingCCTransactionDao = db.stagingCCTransactionDao()
}

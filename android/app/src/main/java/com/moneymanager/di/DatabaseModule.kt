package com.moneymanager.di

import android.content.Context
import com.moneymanager.data.db.AppDatabase
import com.moneymanager.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.build(ctx)

    @Provides fun providePeriodDao(db: AppDatabase): PeriodDao                   = db.periodDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao               = db.categoryDao()
    @Provides fun provideBudgetItemDao(db: AppDatabase): BudgetItemDao           = db.budgetItemDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao         = db.transactionDao()
    @Provides fun provideStagingDao(db: AppDatabase): StagingTransactionDao      = db.stagingTransactionDao()
    @Provides fun provideStagingCCDao(db: AppDatabase): StagingCCTransactionDao  = db.stagingCCTransactionDao()
}

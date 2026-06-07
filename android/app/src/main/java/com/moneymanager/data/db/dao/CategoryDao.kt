package com.moneymanager.data.db.dao

import androidx.room.*
import com.moneymanager.data.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM finance_category ORDER BY `group`, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM finance_category ORDER BY `group`, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM finance_category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM finance_category")
    suspend fun count(): Int
}

package com.moneymanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_period")
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,   // ISO-8601 yyyy-MM-dd
    @ColumnInfo(name = "end_date")   val endDate:   String,
    @ColumnInfo(name = "is_active")  val isActive:  Boolean = true,
)

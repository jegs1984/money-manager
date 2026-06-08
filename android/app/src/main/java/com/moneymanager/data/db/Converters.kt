package com.moneymanager.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Converters {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE   // "YYYY-MM-DD"

    @TypeConverter fun fromLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, fmt) }

    @TypeConverter fun toLocalDate(date: LocalDate?): String? =
        date?.format(fmt)
}

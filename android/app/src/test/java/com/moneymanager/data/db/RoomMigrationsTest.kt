package com.moneymanager.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMigrationsTest {
    @Test
    fun `migrations form a continuous non-destructive upgrade path`() {
        val migrations = RoomMigrations.all
        assertEquals(1, migrations.first().startVersion)
        assertEquals(AppDatabase::class.java.getAnnotation(androidx.room.Database::class.java).version, migrations.last().endVersion)
        assertTrue(migrations.asList().zipWithNext().all { (first, second) -> first.endVersion == second.startVersion })
    }
}

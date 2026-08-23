package com.moneymanager.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every on-device schema transition. Keep this list append-only: the database
 * builder deliberately has no destructive-migration fallback.
 */
object RoomMigrations {
    val all: Array<Migration> = arrayOf(
        object : Migration(1, 2) {
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
        },
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_finance_budget_item_period_id_category_id")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_finance_budget_item_period_id_category_id_type ON finance_budget_item(period_id, category_id, type)")
            }
        },
    )
}

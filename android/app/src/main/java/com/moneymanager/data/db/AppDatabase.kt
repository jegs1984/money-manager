package com.moneymanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneymanager.data.db.dao.*
import com.moneymanager.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun periodDao(): PeriodDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetItemDao(): BudgetItemDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stagingTransactionDao(): StagingTransactionDao
    abstract fun stagingCCTransactionDao(): StagingCCTransactionDao

    companion object {
        const val DB_NAME = "money_manager.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addCallback(SeedCallback())
                .build()
    }

    // ── Seed pre-defined categories on first create ────────────────────────
    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Run seed in IO pool; DB reference obtained via Room's open helper
            CoroutineScope(Dispatchers.IO).launch {
                // The DB is open at this point; insert via raw SQL to avoid
                // circular dependency on the DAO which requires the DB instance.
                SEED_CATEGORIES.forEach { (name, group) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO finance_category(name, `group`) VALUES (?, ?)",
                        arrayOf(name, group),
                    )
                }
            }
        }
    }
}

// ── Canonical category list (mirrors sql/categories.sql) ──────────────────
private val SEED_CATEGORIES: List<Pair<String, String>> = listOf(
    // IngresoFijo
    "Sueldo Líquido"               to "IngresoFijo",
    "Honorarios Recurrentes"       to "IngresoFijo",
    // IngresoVariableExtra
    "Bonos"                        to "IngresoVariableExtra",
    "Aguinaldos"                   to "IngresoVariableExtra",
    "Devolución Impuestos"         to "IngresoVariableExtra",
    "Ventas"                       to "IngresoVariableExtra",
    "Cachureos"                    to "IngresoVariableExtra",
    // ViviendaHogar
    "Arriendo"                     to "ViviendaHogar",
    "Dividendo"                    to "ViviendaHogar",
    "Gastos Comunes"               to "ViviendaHogar",
    "Reparaciones"                 to "ViviendaHogar",
    "Mantención"                   to "ViviendaHogar",
    "Muebles"                      to "ViviendaHogar",
    "Deco"                         to "ViviendaHogar",
    // Cuenta
    "Luz"                          to "Cuenta",
    "Agua"                         to "Cuenta",
    "Gas"                          to "Cuenta",
    "Internet Hogar"               to "Cuenta",
    "Plan Celular"                 to "Cuenta",
    // Alimentación
    "Supermercado"                 to "Alimentación",
    "Feria"                        to "Alimentación",
    "Minimarket"                   to "Alimentación",
    // GastosExtraordinarios
    "Delivery"                     to "GastosExtraordinarios",
    "Restaurant"                   to "GastosExtraordinarios",
    "Patente"                      to "GastosExtraordinarios",
    "Seguro Obligatorio (SOAP)"    to "GastosExtraordinarios",
    "Contribuciones"               to "GastosExtraordinarios",
    "Regalos de Cumpleaños"        to "GastosExtraordinarios",
    "Regalos de Navidad"           to "GastosExtraordinarios",
    "Celebraciones"                to "GastosExtraordinarios",
    "Asados"                       to "GastosExtraordinarios",
    // Transporte
    "Bencina"                      to "Transporte",
    "TAG"                          to "Transporte",
    "Peajes"                       to "Transporte",
    "Transporte Público"           to "Transporte",
    "Apps (Uber, Didi)"            to "Transporte",
    "Mantención Auto"              to "Transporte",
    "Seguro"                       to "Transporte",
    // Deuda
    "Crédito de Consumo"           to "Deuda",
    "Crédito Hipotecario"          to "Deuda",
    "Pago Nacional TC (Línea de Crédito)" to "Deuda",
    // Personal
    "Salud"                        to "Personal",
    "Farmacia"                     to "Personal",
    "Ropa"                         to "Personal",
    "Calzado"                      to "Personal",
    "Cuidado Personal"             to "Personal",
    "Peluquería"                   to "Personal",
    "Educación"                    to "Personal",
    "Doctor"                       to "Personal",
    // Pension
    "APV (Ahorro Previsional Voluntario)" to "Pension",
    "Cuenta 2 AFP"                 to "Pension",
    // AhorroeInversion
    "Fondos Mutuos"                to "AhorroeInversion",
    "Acciones"                     to "AhorroeInversion",
    "Depósitos a Plazo"            to "AhorroeInversion",
    "Ahorro Vacaciones"            to "AhorroeInversion",
    "Proyectos"                    to "AhorroeInversion",
    // Lujo
    "Cafeterías"                   to "Lujo",
    "Hobbies"                      to "Lujo",
    "Videojuegos"                  to "Lujo",
    "Streaming (Netflix, Spotify)" to "Lujo",
    "Viajes"                       to "Lujo",
    "Hoteles"                      to "Lujo",
    // Gastos
    "Gasto Hormiga"                to "Gastos",
    "No Reconocido"                to "Gastos",
)

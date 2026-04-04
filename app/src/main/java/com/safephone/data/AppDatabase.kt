package com.safephone.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FocusProfileEntity::class,
        BlockedAppEntity::class,
        DomainRuleEntity::class,
        AppBudgetEntity::class,
        BreakPolicyEntity::class,
        CalendarKeywordEntity::class,
        BlockStatsEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusProfileDao(): FocusProfileDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun domainRuleDao(): DomainRuleDao
    abstract fun appBudgetDao(): AppBudgetDao
    abstract fun breakPolicyDao(): BreakPolicyDao
    abstract fun calendarKeywordDao(): CalendarKeywordDao
    abstract fun blockStatsDao(): BlockStatsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM domain_rules WHERE isAllowlist != 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `tier_apps`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_budgets ADD COLUMN maxOpensPerDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `schedule_windows`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `block_stats` (
                    `dayEpochDay` INTEGER NOT NULL,
                    `kind` TEXT NOT NULL,
                    `targetKey` TEXT NOT NULL,
                    `count` INTEGER NOT NULL,
                    PRIMARY KEY(`dayEpochDay`, `kind`, `targetKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** In-memory DB for unit/instrumented tests (does not use the app singleton). */
        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safephone.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

package com.safephone.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<FocusProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun getAll(): List<FocusProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): FocusProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: FocusProfileEntity): Long

    suspend fun upsert(profile: FocusProfileEntity): Long = insert(profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
}

@Dao
interface DomainRuleDao {
    @Query("SELECT * FROM domain_rules")
    fun observeAll(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules")
    suspend fun getAll(): List<DomainRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: DomainRuleEntity): Long

    @Query("DELETE FROM domain_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM domain_rules")
    suspend fun deleteAll()
}

@Dao
interface AppBudgetDao {
    @Query("SELECT * FROM app_budgets")
    fun observeAll(): Flow<List<AppBudgetEntity>>

    @Query("SELECT * FROM app_budgets")
    suspend fun getAll(): List<AppBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: AppBudgetEntity)

    @Query("DELETE FROM app_budgets WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM app_budgets")
    suspend fun deleteAll()
}

@Dao
interface BreakPolicyDao {
    @Query("SELECT * FROM break_policy WHERE id = 1")
    fun observe(): Flow<BreakPolicyEntity?>

    @Query("SELECT * FROM break_policy WHERE id = 1")
    suspend fun get(): BreakPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: BreakPolicyEntity)
}

@Dao
interface CalendarKeywordDao {
    @Query("SELECT * FROM calendar_keywords")
    fun observeAll(): Flow<List<CalendarKeywordEntity>>

    @Query("SELECT * FROM calendar_keywords")
    suspend fun getAll(): List<CalendarKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CalendarKeywordEntity)

    @Query("DELETE FROM calendar_keywords WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM calendar_keywords")
    suspend fun deleteAll()
}

@Dao
interface BlockStatsDao {
    @Query(
        """
        INSERT INTO block_stats (dayEpochDay, kind, targetKey, count)
        VALUES (:dayEpochDay, :kind, :targetKey, 1)
        ON CONFLICT(dayEpochDay, kind, targetKey) DO UPDATE SET count = count + 1
        """,
    )
    suspend fun increment(dayEpochDay: Long, kind: String, targetKey: String)

    @Query(
        "SELECT count FROM block_stats WHERE dayEpochDay = :day AND kind = :kind AND targetKey = :targetKey",
    )
    suspend fun getCount(day: Long, kind: String, targetKey: String): Int?

    @Query("SELECT * FROM block_stats WHERE dayEpochDay = :day ORDER BY count DESC, kind ASC, targetKey ASC")
    fun observeForDay(day: Long): Flow<List<BlockStatsEntity>>

    @Query(
        """
        SELECT kind, targetKey, SUM(count) AS count
        FROM block_stats
        WHERE dayEpochDay >= :sinceEpochDayInclusive
        GROUP BY kind, targetKey
        ORDER BY count DESC, kind ASC, targetKey ASC
        """,
    )
    fun observeAggregatedSince(sinceEpochDayInclusive: Long): Flow<List<BlockStatsAggregateRow>>
}

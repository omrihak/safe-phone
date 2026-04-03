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
}

@Dao
interface ScheduleWindowDao {
    @Query("SELECT * FROM schedule_windows WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Long): List<ScheduleWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(window: ScheduleWindowEntity): Long

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedule_windows WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
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
}

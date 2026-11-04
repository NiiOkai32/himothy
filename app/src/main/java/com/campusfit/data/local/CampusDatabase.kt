package com.campusfit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.campusfit.domain.Profile

@Entity(tableName = "profiles")
data class ProfileEntity(@PrimaryKey val uid: String, val profile: Profile, val syncPending: Boolean = true)
class ProfileConverter {
    private val gson = com.google.gson.Gson()
    @TypeConverter fun encode(value: Profile): String = gson.toJson(value)
    @TypeConverter fun decode(value: String): Profile = gson.fromJson(value, Profile::class.java)
}
@Entity(tableName = "weigh_ins", primaryKeys = ["uid", "epochDay"])
data class WeighInEntity(val uid: String, val epochDay: Long, val weightKg: Double, val flagged: Boolean, val syncPending: Boolean = true,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val syncIssue: String = "")
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(@PrimaryKey val id: String, val uid: String, val startedAt: Long, val minutes: Int, val calories: Double, val syncPending: Boolean = true, @ColumnInfo(defaultValue = "0") val durationSeconds: Int = 0,
    @ColumnInfo(defaultValue = "'guided'") val source: String = "guided",
    @ColumnInfo(defaultValue = "0") val distanceMeters: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val reps: Int = 0,
    @ColumnInfo(defaultValue = "''") val syncIssue: String = "")
@Entity(tableName = "exercises")
data class ExerciseEntity(@PrimaryKey val id: String, val name: String, val category: String, val difficulty: String, val met: Double, val instructionsJson: String, val musclesJson: String, val demoAsset: String, @ColumnInfo(defaultValue = "''") val metadataJson: String = "")
@Dao interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE uid = :uid") fun observe(uid: String): Flow<ProfileEntity?>
    @Query("SELECT * FROM profiles WHERE uid = :uid") suspend fun get(uid: String): ProfileEntity?
    @Upsert suspend fun save(profile: ProfileEntity)
    @Query("DELETE FROM profiles WHERE uid = :uid") suspend fun delete(uid: String)
}
@Dao interface TrackingDao {
    // ABORT prevents a second daily entry from silently replacing the first.
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(entry: WeighInEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(session: WorkoutSessionEntity)
    @Query("SELECT * FROM weigh_ins WHERE uid = :uid ORDER BY epochDay") fun weights(uid: String): Flow<List<WeighInEntity>>
    @Query("SELECT * FROM workout_sessions WHERE uid = :uid ORDER BY startedAt DESC") fun sessions(uid: String): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM weigh_ins WHERE syncPending = 1 AND uid = :uid AND syncIssue NOT LIKE 'Rejected:%'") suspend fun pendingWeights(uid: String): List<WeighInEntity>
    @Query("SELECT * FROM workout_sessions WHERE syncPending = 1 AND uid = :uid AND syncIssue NOT LIKE 'Rejected:%'") suspend fun pendingSessions(uid: String): List<WorkoutSessionEntity>
    @Query("SELECT * FROM weigh_ins WHERE uid = :uid ORDER BY epochDay DESC LIMIT 1") suspend fun latestWeight(uid: String): WeighInEntity?
    @Query("SELECT * FROM weigh_ins WHERE uid = :uid AND epochDay = :day") suspend fun weight(uid: String, day: Long): WeighInEntity?
    @Query("SELECT * FROM workout_sessions WHERE uid = :uid") suspend fun allSessions(uid: String): List<WorkoutSessionEntity>
    @Query("SELECT * FROM workout_sessions WHERE id = :id") suspend fun session(id: String): WorkoutSessionEntity?
    @Upsert suspend fun merge(entry: WeighInEntity)
    @Upsert suspend fun merge(session: WorkoutSessionEntity)
    @Query("UPDATE weigh_ins SET syncIssue = '' WHERE uid = :uid") suspend fun clearWeightIssues(uid: String)
    @Query("UPDATE workout_sessions SET syncIssue = '' WHERE uid = :uid") suspend fun clearSessionIssues(uid: String)
    @Query("UPDATE workout_sessions SET syncPending = 0, syncIssue = '' WHERE uid = :uid AND id = :id") suspend fun sessionSynced(uid: String, id: String)
    @Query("UPDATE weigh_ins SET syncIssue = :issue WHERE uid = :uid AND epochDay = :day") suspend fun weightIssue(uid: String, day: Long, issue: String)
    @Query("UPDATE workout_sessions SET syncIssue = :issue WHERE uid = :uid AND id = :id") suspend fun sessionIssue(uid: String, id: String, issue: String)
    @Query("DELETE FROM weigh_ins WHERE uid = :uid") suspend fun deleteWeights(uid: String)
    @Query("DELETE FROM workout_sessions WHERE uid = :uid") suspend fun deleteSessions(uid: String)
}
@Dao interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name") fun observe(): Flow<List<ExerciseEntity>>
    @Upsert suspend fun seed(exercises: List<ExerciseEntity>)
}
@Entity(tableName = "workout_runs")
data class WorkoutRunEntity(@PrimaryKey val id: String, val uid: String, val payloadJson: String, val finished: Boolean, val updatedAt: Long)
@Dao interface WorkoutRunDao {
    @Query("SELECT * FROM workout_runs WHERE uid = :uid AND finished = 0 ORDER BY updatedAt DESC LIMIT 1") suspend fun active(uid: String): WorkoutRunEntity?
    @Upsert suspend fun save(run: WorkoutRunEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun complete(session: WorkoutSessionEntity): Long
    @Query("DELETE FROM workout_runs WHERE uid = :uid") suspend fun delete(uid: String)
}
@Entity(tableName = "outdoor_runs")
data class OutdoorRunEntity(@PrimaryKey val id: String, val uid: String, val payloadJson: String, val finished: Boolean)
@Entity(tableName = "tracking_preferences")
data class TrackingPreference(@PrimaryKey val uid: String, val healthEnabled: Boolean = false)
@Entity(tableName = "account_deletions")
data class AccountDeletion(@PrimaryKey val uid: String)
@Dao interface TrackingExtraDao {
    @Query("SELECT * FROM outdoor_runs WHERE uid = :uid ORDER BY rowid DESC") fun routes(uid: String): Flow<List<OutdoorRunEntity>>
    @Upsert suspend fun save(run: OutdoorRunEntity)
    @Query("DELETE FROM outdoor_runs WHERE uid = :uid") suspend fun deleteRoutes(uid: String)
    @Query("SELECT * FROM tracking_preferences WHERE uid = :uid") suspend fun preference(uid: String): TrackingPreference?
    @Upsert suspend fun preference(value: TrackingPreference)
    @Query("DELETE FROM tracking_preferences WHERE uid = :uid") suspend fun deletePreferences(uid: String)
    @Query("SELECT EXISTS(SELECT 1 FROM account_deletions WHERE uid = :uid)") suspend fun deleting(uid: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun deleting(value: AccountDeletion)
    @Query("DELETE FROM account_deletions WHERE uid = :uid") suspend fun clearDeletion(uid: String)
}
@Entity(tableName = "social_cache", primaryKeys = ["uid", "resource"])
data class SocialCache(val uid: String, val resource: String, val payloadJson: String, val updatedAt: Long, val sensitive: Boolean)
@Dao interface SocialCacheDao {
    @Query("SELECT * FROM social_cache WHERE uid = :uid") suspend fun all(uid:String): List<SocialCache>
    @Query("SELECT * FROM social_cache WHERE uid = :uid AND resource = :resource") suspend fun get(uid: String, resource: String): SocialCache?
    @Upsert suspend fun put(value: SocialCache)
    @Query("DELETE FROM social_cache WHERE uid = :uid AND resource = :resource") suspend fun evict(uid: String, resource: String)
    @Query("DELETE FROM social_cache WHERE uid = :uid") suspend fun delete(uid: String)
}
@Entity(tableName = "engagement_preferences")
data class EngagementPreference(@PrimaryKey val uid:String,val reminders:Boolean=false,val push:Boolean=false,val widget:Boolean=false,val zone:String="Africa/Accra",val startMinute:Int=480,val endMinute:Int=1200,val quietStart:Int=1320,val quietEnd:Int=480,val daysMask:Int=31,val minutes:Int=10,val calendarUntil:Long=0,val deviceKey:String="")
@Entity(tableName = "timetable_blocks",primaryKeys=["uid","id"])
data class TimetableBlock(val uid:String,val id:String,val start:Long,val end:Long)
@Entity(tableName = "notification_inbox",primaryKeys=["uid","id"])
data class InboxEntry(val uid:String,val id:String,val kind:String,val target:String,val createdAt:Long,val read:Boolean=false,val readPending:Boolean=false,val shown:Boolean=false)
@Entity(tableName = "mini_completions",primaryKeys=["uid","epochDay"])
data class MiniCompletion(val uid:String,val epochDay:Long,val taskId:String,val completedAt:Long)
@Entity(tableName = "seen_awards",primaryKeys=["uid","awardId"])
data class SeenAward(val uid:String,val awardId:String)
@Entity(tableName = "reminder_days",primaryKeys=["uid","epochDay"])
data class ReminderDay(val uid:String,val epochDay:Long,val rested:Boolean=false,val notified:Boolean=false)
@Dao interface EngagementDao {
 @Query("SELECT * FROM engagement_preferences WHERE uid=:uid") fun preferences(uid:String):Flow<EngagementPreference?>
 @Query("SELECT * FROM engagement_preferences WHERE uid=:uid") suspend fun preference(uid:String):EngagementPreference?
 @Upsert suspend fun preference(value:EngagementPreference)
 @Query("SELECT * FROM timetable_blocks WHERE uid=:uid ORDER BY start") fun blocks(uid:String):Flow<List<TimetableBlock>>
 @Query("SELECT * FROM timetable_blocks WHERE uid=:uid AND end>:now ORDER BY start") suspend fun futureBlocks(uid:String,now:Long):List<TimetableBlock>
 @Upsert suspend fun blocks(values:List<TimetableBlock>)
 @Query("DELETE FROM timetable_blocks WHERE uid=:uid") suspend fun clearBlocks(uid:String)
 @Query("SELECT * FROM notification_inbox WHERE uid=:uid ORDER BY createdAt DESC LIMIT 100") fun inbox(uid:String):Flow<List<InboxEntry>>
 @Query("SELECT * FROM notification_inbox WHERE uid=:uid AND id=:id") suspend fun entry(uid:String,id:String):InboxEntry?
 @Upsert suspend fun entry(value:InboxEntry)
 @Query("UPDATE notification_inbox SET read=1,readPending=1 WHERE uid=:uid AND id=:id") suspend fun read(uid:String,id:String)
 @Query("SELECT * FROM notification_inbox WHERE uid=:uid AND readPending=1") suspend fun pendingReads(uid:String):List<InboxEntry>
 @Query("UPDATE notification_inbox SET readPending=0 WHERE uid=:uid AND id=:id") suspend fun readSynced(uid:String,id:String)
 @Query("SELECT * FROM mini_completions WHERE uid=:uid ORDER BY epochDay DESC LIMIT 30") fun minis(uid:String):Flow<List<MiniCompletion>>
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun mini(value:MiniCompletion):Long
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun award(value:SeenAward):Long
 @Query("SELECT * FROM reminder_days WHERE uid=:uid AND epochDay>=:day") suspend fun days(uid:String,day:Long):List<ReminderDay>
 @Upsert suspend fun day(value:ReminderDay)
 @Query("DELETE FROM engagement_preferences WHERE uid=:uid") suspend fun deletePreferences(uid:String)
 @Query("DELETE FROM notification_inbox WHERE uid=:uid") suspend fun deleteInbox(uid:String)
 @Query("DELETE FROM mini_completions WHERE uid=:uid") suspend fun deleteMinis(uid:String)
 @Query("DELETE FROM seen_awards WHERE uid=:uid") suspend fun deleteAwards(uid:String)
 @Query("DELETE FROM reminder_days WHERE uid=:uid") suspend fun deleteDays(uid:String)
}
@Database(entities = [ProfileEntity::class, WeighInEntity::class, WorkoutSessionEntity::class, ExerciseEntity::class, WorkoutRunEntity::class, OutdoorRunEntity::class, TrackingPreference::class, AccountDeletion::class, SocialCache::class, EngagementPreference::class, TimetableBlock::class, InboxEntry::class, MiniCompletion::class, SeenAward::class, ReminderDay::class], version = 5, exportSchema = true)
@TypeConverters(ProfileConverter::class)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun tracking(): TrackingDao
    abstract fun exercises(): ExerciseDao
    abstract fun runs(): WorkoutRunDao
    abstract fun extra(): TrackingExtraDao
    abstract fun social(): SocialCacheDao
    abstract fun engagement(): EngagementDao
}

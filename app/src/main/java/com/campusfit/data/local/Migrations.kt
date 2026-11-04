package com.campusfit.data.local
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE workout_sessions SET durationSeconds = minutes * 60")
        db.execSQL("ALTER TABLE exercises ADD COLUMN metadataJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_runs (id TEXT NOT NULL, uid TEXT NOT NULL, payloadJson TEXT NOT NULL, finished INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weigh_ins ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE weigh_ins SET createdAt = epochDay * 86400000")
        db.execSQL("ALTER TABLE weigh_ins ADD COLUMN syncIssue TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'guided'")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN distanceMeters REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN syncIssue TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS outdoor_runs (id TEXT NOT NULL, uid TEXT NOT NULL, payloadJson TEXT NOT NULL, finished INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS tracking_preferences (uid TEXT NOT NULL, healthEnabled INTEGER NOT NULL, PRIMARY KEY(uid))")
        db.execSQL("CREATE TABLE IF NOT EXISTS account_deletions (uid TEXT NOT NULL, PRIMARY KEY(uid))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS social_cache (uid TEXT NOT NULL, resource TEXT NOT NULL, payloadJson TEXT NOT NULL, updatedAt INTEGER NOT NULL, sensitive INTEGER NOT NULL, PRIMARY KEY(uid, resource))")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS engagement_preferences (uid TEXT NOT NULL, reminders INTEGER NOT NULL, push INTEGER NOT NULL, widget INTEGER NOT NULL, zone TEXT NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, quietStart INTEGER NOT NULL, quietEnd INTEGER NOT NULL, daysMask INTEGER NOT NULL, minutes INTEGER NOT NULL, calendarUntil INTEGER NOT NULL, deviceKey TEXT NOT NULL, PRIMARY KEY(uid))")
  db.execSQL("CREATE TABLE IF NOT EXISTS timetable_blocks (uid TEXT NOT NULL, id TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER NOT NULL, PRIMARY KEY(uid,id))")
  db.execSQL("CREATE TABLE IF NOT EXISTS notification_inbox (uid TEXT NOT NULL, id TEXT NOT NULL, kind TEXT NOT NULL, target TEXT NOT NULL, createdAt INTEGER NOT NULL, read INTEGER NOT NULL, readPending INTEGER NOT NULL, shown INTEGER NOT NULL, PRIMARY KEY(uid,id))")
  db.execSQL("CREATE TABLE IF NOT EXISTS mini_completions (uid TEXT NOT NULL, epochDay INTEGER NOT NULL, taskId TEXT NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY(uid,epochDay))")
  db.execSQL("CREATE TABLE IF NOT EXISTS seen_awards (uid TEXT NOT NULL, awardId TEXT NOT NULL, PRIMARY KEY(uid,awardId))")
  db.execSQL("CREATE TABLE IF NOT EXISTS reminder_days (uid TEXT NOT NULL, epochDay INTEGER NOT NULL, rested INTEGER NOT NULL, notified INTEGER NOT NULL, PRIMARY KEY(uid,epochDay))")
 }
}

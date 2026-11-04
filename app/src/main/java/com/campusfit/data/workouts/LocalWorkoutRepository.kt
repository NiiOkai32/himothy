package com.campusfit.data.workouts

import android.content.Context
import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.AuthRepository
import com.campusfit.domain.workouts.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class LocalWorkoutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CampusDatabase, private val auth: AuthRepository, private val scheduler: com.campusfit.data.tracking.SyncScheduler,
) : WorkoutRepository {
    private val gson = Gson()
    override suspend fun catalogue(): List<Exercise> = withContext(Dispatchers.IO) {
        val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val items: List<Exercise> = gson.fromJson(text, object : TypeToken<List<Exercise>>() {}.type)
        require(items.size >= 60 && items.map { it.id }.distinct().size == items.size)
        items.forEach { e -> e.validate(); require(items.any { it.id == e.alternativeId }) }
        db.exercises().seed(items.map { e -> ExerciseEntity(e.id, e.name, e.category, e.difficulty, e.met, gson.toJson(e.instructions), gson.toJson(e.muscles), "vector:${e.id}", gson.toJson(e)) })
        items.sortedBy { it.name }
    }
    override suspend fun active(uid: String): WorkoutRun? = withContext(Dispatchers.IO) {
        check(auth.account.value?.uid == uid) { "Please sign in again." }
        db.runs().active(uid)?.let { entity ->
            gson.fromJson(entity.payloadJson, WorkoutRun::class.java).also { check(it.uid == uid) }.let(WorkoutEngine::restore)
        }
    }
    override suspend fun save(run: WorkoutRun) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Account deletion/sign-out must not leave a queued write able to recreate private rows.
            check(auth.account.value?.uid == run.uid && !db.extra().deleting(run.uid) && db.profiles().get(run.uid) != null) { "Please sign in again." }
            db.runs().save(WorkoutRunEntity(run.id, run.uid, gson.toJson(run.copy(paused = true)), run.finished, System.currentTimeMillis()))
            if (run.finished && run.activeMillis >= 1000) {
                val seconds = (run.activeMillis / 1000).toInt()
                db.runs().complete(WorkoutSessionEntity(run.id, run.uid, run.startedAt, seconds / 60, run.calories, true, seconds))
            }
        }
        if (run.finished && auth.account.value?.let { it.verified && !it.demo && it.uid == run.uid } == true) scheduler.enqueue(run.uid)
    }
}

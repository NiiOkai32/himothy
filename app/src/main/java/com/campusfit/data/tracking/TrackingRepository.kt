package com.campusfit.data.tracking

import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.AuthRepository
import com.campusfit.domain.tracking.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TrackingRepository @Inject constructor(
    private val db: CampusDatabase, private val auth: AuthRepository, private val scheduler: SyncScheduler,
) {
    private val gson = Gson()
    fun weights(uid: String): Flow<List<WeighInEntity>> = db.tracking().weights(uid)
    fun sessions(uid: String): Flow<List<WorkoutSessionEntity>> = db.tracking().sessions(uid)
    fun routes(uid: String): Flow<List<OutdoorRunEntity>> = db.extra().routes(uid)
    private suspend fun owner(uid: String) {
        check(auth.account.value?.uid == uid && !db.extra().deleting(uid) && db.profiles().get(uid) != null) { "Sign in to an active account before saving." }
    }
    fun schedule(uid: String) {
        if (auth.account.value?.let { it.uid == uid && it.verified && !it.demo } == true && auth.cloudConfigured) scheduler.enqueue(uid)
    }
    suspend fun refreshCloud(uid: String) {
        db.withTransaction { owner(uid); db.tracking().clearWeightIssues(uid); db.tracking().clearSessionIssues(uid) }
        if (auth.account.value?.let { it.uid == uid && it.verified && !it.demo } == true && auth.cloudConfigured) scheduler.enqueue(uid, pull = true)
    }
    fun startSync(uid: String) {
        if (auth.account.value?.let { it.uid == uid && it.verified && !it.demo } == true && auth.cloudConfigured) scheduler.observeAccount(uid)
    }
    suspend fun weigh(uid: String, kg: Double, time: Long = System.currentTimeMillis()) {
        WeightPolicy.validate(kg)
        db.withTransaction {
            owner(uid)
            val day = WeightPolicy.utcDay(time)
            require(db.tracking().weight(uid, day) == null) { "You have already logged a weigh-in for today (UTC). Try again tomorrow." }
            val previous = db.tracking().latestWeight(uid)
            val flagged = previous != null && previous.epochDay < day && WeightPolicy.unusual(previous.weightKg, kg, day - previous.epochDay)
            db.tracking().add(WeighInEntity(uid, day, kg, flagged, true, time))
        }
        schedule(uid)
    }
    suspend fun saveOutdoor(run: OutdoorRun) = withContext(Dispatchers.IO) {
        db.withTransaction {
            owner(run.uid)
            db.extra().save(OutdoorRunEntity(run.id, run.uid, gson.toJson(run.copy(paused = true)), run.finished))
            if (run.finished && run.elapsedMillis >= 1000) {
                val seconds = (run.elapsedMillis / 1000).toInt()
                val energy = com.campusfit.domain.workouts.Energy.calories(if (run.mode == "walk") 3.0 else 7.0, run.weightKg, run.elapsedMillis)
                db.runs().complete(WorkoutSessionEntity(run.id, run.uid, run.startedAt, seconds / 60, energy, true, seconds, "gps-${run.mode}", run.distanceMeters))
            }
        }
        if (run.finished) schedule(run.uid)
    }
    suspend fun saveCamera(uid: String, id: String, startedAt: Long, seconds: Int, reps: Int, met: Double, kg: Double) {
        require(seconds in 1..1200 && reps >= 0)
        db.withTransaction {
            owner(uid)
            val energy = com.campusfit.domain.workouts.Energy.calories(met, kg, seconds * 1000L)
            db.runs().complete(WorkoutSessionEntity(id, uid, startedAt, seconds / 60, energy, true, seconds, "camera", 0.0, reps))
        }
        schedule(uid)
    }
    suspend fun healthEnabled(uid: String): Boolean = db.extra().preference(uid)?.healthEnabled == true
    suspend fun healthEnabled(uid: String, enabled: Boolean) {
        db.withTransaction { owner(uid); db.extra().preference(TrackingPreference(uid, enabled)) }
    }
}

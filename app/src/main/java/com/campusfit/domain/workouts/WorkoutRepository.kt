package com.campusfit.domain.workouts
interface WorkoutRepository {
    suspend fun catalogue(): List<Exercise>
    suspend fun active(uid: String): WorkoutRun?
    suspend fun save(run: WorkoutRun)
}

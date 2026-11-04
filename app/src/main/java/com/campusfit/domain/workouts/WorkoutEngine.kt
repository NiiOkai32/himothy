package com.campusfit.domain.workouts

/** Pure foreground timer. Callers supply monotonic elapsed time, never wall-clock deltas.
 * Rest, skipped time and paused/background time never earn movement calories or minutes. */
object WorkoutEngine {
    fun advance(run: WorkoutRun, deltaMillis: Long, exercises: List<Exercise>): WorkoutRun {
        require(deltaMillis >= 0)
        if (run.paused || run.finished || deltaMillis == 0L) return run
        var result = run
        var budget = deltaMillis
        val byId = exercises.associateBy { it.id }
        while (budget > 0 && !result.finished) {
            val stage = result.currentStage ?: return result.copy(finished = true, paused = true)
            if (result.preparationMillis > 0) {
                val preparing = minOf(budget, result.preparationMillis)
                result = result.copy(preparationMillis = result.preparationMillis - preparing)
                budget -= preparing
                if (budget == 0L) break
            }
            val applied = minOf(budget, stage.seconds * 1000L - result.stageMillis)
            val exercise = stage.exerciseId?.let { checkNotNull(byId[it]) }
            result = result.copy(
                stageMillis = result.stageMillis + applied,
                elapsedMillis = result.elapsedMillis + applied,
                activeMillis = result.activeMillis + if (exercise != null) applied else 0,
                calories = result.calories + if (exercise != null) Energy.calories(exercise.met, result.weightKg, applied) else 0.0,
            )
            budget -= applied
            if (result.stageMillis == stage.seconds * 1000L) result = next(result)
        }
        return result
    }
    private fun next(run: WorkoutRun): WorkoutRun {
        val index = run.stageIndex + 1
        return run.copy(stageIndex = index, stageMillis = 0, finished = index >= run.plan.stages.size,
            preparationMillis = if (run.plan.stages.getOrNull(index)?.exerciseId != null) 3000L else 0L,
            paused = if (index >= run.plan.stages.size) true else run.paused)
    }
    fun skip(run: WorkoutRun): WorkoutRun = if (run.finished) run else next(run)
    fun end(run: WorkoutRun): WorkoutRun = run.copy(finished = true, paused = true, endedEarly = true, preparationMillis = 0)
    fun restore(run: WorkoutRun): WorkoutRun = run.copy(paused = true,
        // Older JSON checkpoints have no preparation field (Gson reads zero).
        // Also restart a partially completed count-in after a process restart.
        preparationMillis = if (!run.finished && run.currentStage?.exerciseId != null && (run.stageMillis == 0L || run.preparationMillis > 0)) 3000L else 0L)
}

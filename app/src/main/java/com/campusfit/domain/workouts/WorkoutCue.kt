package com.campusfit.domain.workouts

/** Stable, one-second cue windows. The UI plays each window once, never on a 250ms tick. */
enum class WorkoutCue { THREE, TWO, ONE, GO, DONE }
fun WorkoutRun.audioCue(): WorkoutCue? {
    if (paused && !finished) return null
    if (finished) return if (endedEarly) null else WorkoutCue.DONE
    fun number(value: Int) = when(value) { 3 -> WorkoutCue.THREE; 2 -> WorkoutCue.TWO; 1 -> WorkoutCue.ONE; else -> null }
    if (preparationSeconds > 0) return number(preparationSeconds)
    if (currentStage?.exerciseId != null) {
        if (remainingSeconds in 1..3) return number(remainingSeconds)
        if (stageMillis < 1000L) return WorkoutCue.GO
    } else if (stageMillis < 1000L) return WorkoutCue.DONE
    return null
}

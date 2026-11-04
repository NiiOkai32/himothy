package com.campusfit.domain.workouts

/** Local catalogue MET values are broad estimates, not medical measurements. */
data class Exercise(
    val id: String, val name: String, val category: String, val difficulty: String,
    val met: Double, val instructions: List<String>, val muscles: List<String>,
    val lowNoise: Boolean, val gentle: Boolean, val campusOnly: Boolean,
    val alternativeId: String, val demo: List<Pose>, val cue: String,
    val demo3d: List<SpatialPose>,
) {
    fun validate() {
        require(id.matches(Regex("[a-z0-9-]+")) && name.isNotBlank())
        require(category in Categories.all && difficulty in listOf("Beginner", "Intermediate", "Advanced"))
        require(met.isFinite() && met in 1.0..15.0 && instructions.size >= 3 && muscles.isNotEmpty())
        require(demo.size >= 2 && demo.all { it.points.size == 26 && it.points.all { n -> n.isFinite() && n in 0f..1f } })
        require(demo3d.size >= 2 && demo3d.all { it.points.size == 39 && it.points.all { n -> n.isFinite() && n in -2.5f..2.5f } })
    }
}
/** 13 normalized joints: head, neck, shoulders, elbows, hands, pelvis, knees and feet.
 * Joint order and connectivity are shared by the offline vector demo renderer. */
data class Pose(val points: List<Float>)
/** Same 13 joints, in metres: X left/right, Y up, Z forward. */
data class SpatialPose(val points: List<Float>)
object Categories {
    val all = listOf("HIIT", "Strength", "Cardio", "Yoga & stretch", "Core", "Dance", "Walking & running", "Dorm quiet")
}
enum class StageKind { WARMUP, WORK, REST, COOLDOWN }
data class Stage(val kind: StageKind, val exerciseId: String?, val seconds: Int)
data class WorkoutPlan(val id: String, val title: String, val stages: List<Stage>) {
    val durationSeconds: Int get() = stages.sumOf { it.seconds }
    fun validate(catalogue: List<Exercise>) {
        require(id.isNotBlank() && stages.isNotEmpty() && durationSeconds in 120..1200)
        val ids = catalogue.map { it.id }.toSet()
        require(stages.all { it.seconds in 1..120 && (if (it.kind == StageKind.REST) it.exerciseId == null else it.exerciseId in ids) })
    }
}
data class WorkoutRun(
    val id: String, val uid: String, val plan: WorkoutPlan, val weightKg: Double,
    val stageIndex: Int = 0, val stageMillis: Long = 0, val activeMillis: Long = 0,
    val elapsedMillis: Long = 0, val calories: Double = 0.0,
    val paused: Boolean = true, val finished: Boolean = false, val endedEarly: Boolean = false,
    val startedAt: Long,
    // Preparation is deliberately separate from stage/elapsed/activity accounting.
    val preparationMillis: Long = if (plan.stages.getOrNull(stageIndex)?.exerciseId != null) 3000L else 0L,
) {
    val currentStage: Stage? get() = plan.stages.getOrNull(stageIndex)
    val remainingSeconds: Int get() = currentStage?.let { ((it.seconds * 1000L - stageMillis + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
    val preparationSeconds: Int get() = ((preparationMillis + 999) / 1000).toInt()
}
object Energy {
    fun calories(met: Double, weightKg: Double, milliseconds: Long): Double {
        require(met.isFinite() && met in 1.0..15.0 && weightKg.isFinite() && weightKg in 35.0..350.0 && milliseconds >= 0)
        return met * 3.5 * weightKg / 200.0 * milliseconds / 60_000.0
    }
}

fun interface WorkoutClock { fun now(): Long }

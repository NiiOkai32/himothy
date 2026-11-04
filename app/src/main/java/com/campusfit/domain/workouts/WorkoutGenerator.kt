package com.campusfit.domain.workouts

import kotlin.random.Random
import java.util.UUID

object WorkoutGenerator {
    fun generate(
        exercises: List<Exercise>, minutes: Int, level: String, quiet: Boolean,
        gentle: Boolean, category: String? = null, random: Random = Random.Default,
        singleExerciseId: String? = null,
    ): WorkoutPlan {
        require(minutes in 2..20) { "Choose 2 to 20 minutes." }
        val levels = listOf("Beginner", "Intermediate", "Advanced")
        require(level in levels)
        val pool = exercises.filter {
            !it.campusOnly && levels.indexOf(it.difficulty) <= levels.indexOf(level) &&
                (!quiet || it.lowNoise) && (!gentle || it.gentle) &&
                (category == null || it.category == category) && (singleExerciseId == null || it.id == singleExerciseId)
        }
        require(pool.isNotEmpty()) { "No exercises match. Try another category or choose an easier alternative." }
        val warmup = exercises.firstOrNull { it.id == "easy-march" } ?: error("Warm-up exercise is missing.")
        val cooldown = exercises.firstOrNull { it.id == "standing-breathing" } ?: error("Cool-down exercise is missing.")
        val ordered = pool.shuffled(random)
        val stages = mutableListOf(Stage(StageKind.WARMUP, warmup.id, 30))
        repeat(minutes - 1) { index ->
            stages += Stage(StageKind.WORK, ordered[index % ordered.size].id, 40)
            stages += Stage(StageKind.REST, null, 20)
        }
        stages += Stage(StageKind.COOLDOWN, cooldown.id, 30)
        return WorkoutPlan(UUID.randomUUID().toString(), if (singleExerciseId != null) pool.first().name else "${minutes}-minute ${if (gentle) "gentle" else if (quiet) "quiet" else "campus"} reset", stages).also { it.validate(exercises) }
    }
    fun estimate(plan: WorkoutPlan, exercises: List<Exercise>, weightKg: Double): Double = plan.stages.sumOf { stage ->
        stage.exerciseId?.let { id -> Energy.calories(exercises.first { it.id == id }.met, weightKg, stage.seconds * 1000L) } ?: 0.0
    }
}

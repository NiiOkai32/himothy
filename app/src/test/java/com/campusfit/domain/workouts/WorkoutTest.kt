package com.campusfit.domain.workouts

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

fun loadTestCatalogue(): List<Exercise> = Gson().fromJson(File(checkNotNull(System.getProperty("campusfit.seed"))).readText(), object : TypeToken<List<Exercise>>() {}.type)
class CatalogueTest {
    private val catalogue = loadTestCatalogue()
    @Test fun everyBundledExerciseHasCompleteOfflineContent() {
        assertTrue(catalogue.size >= 60)
        assertEquals(catalogue.size, catalogue.map { it.id }.toSet().size)
        catalogue.forEach { e -> e.validate(); assertTrue(e.cue.isNotBlank()); assertTrue(e.instructions.all { it.isNotBlank() }); assertTrue(e.demo.distinct().size > 1) }
    }
    @Test fun demosShowDistinctExerciseMechanicsInsteadOfSharedStandingPoses() {
        fun frames(id: String) = catalogue.first { it.id == id }.demo.map { it.points }
        fun y(p: List<Float>, joint: Int) = p[joint*2+1]
        val squat = frames("bodyweight-squat")
        assertTrue(y(squat[1],8) > y(squat[0],8) + .1f) // Hips lower.
        val bridge = frames("glute-bridge")
        assertTrue(y(bridge[1],8) < y(bridge[0],8) - .1f) // Hips rise off mat.
        val push = frames("push-up")
        assertTrue(y(push[1],2) > y(push[0],2) + .1f) // Chest lowers, hands stay planted.
        assertEquals(push[0][12],push[1][12],0f)
        assertEquals(push[0][13],push[1][13],0f)
        val dog = frames("downward-dog").first()
        assertTrue(y(dog,8) < y(dog,2)) // Hips above shoulders, not a plank.
        assertNotEquals(frames("easy-march"),frames("high-knees"))
        assertNotEquals(frames("high-plank"),frames("push-up"))
    }
    @Test fun everyCategoryAndRequiredCardioMovementExists() {
        assertEquals(Categories.all.toSet(), catalogue.map { it.category }.toSet())
        setOf("jumping-jacks", "high-knees", "stair-climbing", "invisible-rope-skipping").forEach { id -> assertTrue(catalogue.any { it.id == id }) }
    }
    @Test fun alternativesAreRealAndNeverHarder() {
        val levels = listOf("Beginner", "Intermediate", "Advanced")
        catalogue.forEach { e ->
            val alternative = catalogue.first { it.id == e.alternativeId }
            assertNotEquals(e.id, alternative.id)
            assertTrue(levels.indexOf(alternative.difficulty) <= levels.indexOf(e.difficulty))
            if (!e.campusOnly) assertFalse(alternative.campusOnly)
        }
    }
}
class GeneratorTest {
    private val catalogue = loadTestCatalogue()
    @Test fun everySupportedDurationIsExactIncludingWarmupAndRecovery() {
        for (minutes in 2..20) for (level in listOf("Beginner", "Intermediate", "Advanced")) {
            val plan = WorkoutGenerator.generate(catalogue, minutes, level, false, false, random = Random(7))
            assertEquals(minutes * 60, plan.durationSeconds)
            assertEquals(StageKind.WARMUP, plan.stages.first().kind)
            assertEquals(StageKind.COOLDOWN, plan.stages.last().kind)
            assertTrue(plan.stages.any { it.kind == StageKind.REST })
            plan.validate(catalogue)
        }
    }
    @Test fun quietGentleBeginnerFiltersAreRespected() {
        val plan = WorkoutGenerator.generate(catalogue, 20, "Beginner", true, true, random = Random(3))
        plan.stages.mapNotNull { it.exerciseId }.forEach { id ->
            val e = catalogue.first { it.id == id }
            assertTrue(e.lowNoise && e.gentle && !e.campusOnly)
            assertEquals("Beginner", e.difficulty)
        }
    }
    @Test fun categoryAppliesToWorkIntervalsWithoutRemovingWarmup() {
        val plan = WorkoutGenerator.generate(catalogue, 5, "Beginner", true, false, "Strength", Random(1))
        plan.stages.filter { it.kind == StageKind.WORK }.forEach { stage -> assertEquals("Strength", catalogue.first { it.id == stage.exerciseId }.category) }
    }
    @Test fun aSingleExercisePracticeUsesOnlyThatExerciseForWork() {
        val plan = WorkoutGenerator.generate(catalogue, 2, "Beginner", true, false, singleExerciseId = "wall-push-up")
        assertTrue(plan.stages.filter { it.kind == StageKind.WORK }.all { it.exerciseId == "wall-push-up" })
    }
    @Test fun unsafeDurationsAndEmptyCombinationsFailClearly() {
        listOf(0,1,21).forEach { minutes -> assertThrows(IllegalArgumentException::class.java) { WorkoutGenerator.generate(catalogue, minutes, "Beginner", false, false) } }
        assertThrows(IllegalArgumentException::class.java) { WorkoutGenerator.generate(catalogue, 5, "Beginner", true, true, "HIIT") }
        assertThrows(IllegalArgumentException::class.java) { WorkoutGenerator.generate(catalogue, 5, "Beginner", false, false, singleExerciseId = "stair-climbing") }
    }
}
class WorkoutEngineTest {
    private val catalogue = loadTestCatalogue()
    private val plan = WorkoutGenerator.generate(catalogue, 2, "Beginner", true, false, singleExerciseId = "wall-push-up")
    private fun run() = WorkoutRun("run", "user", plan, 70.0, paused = false, startedAt = 1L)
    @Test fun pausedRunsEarnNothing() {
        val paused = run().copy(paused = true)
        assertEquals(paused, WorkoutEngine.advance(paused, 90_000, catalogue))
    }
    @Test fun restCountsElapsedButNotActivityOrCalories() {
        val atRest = WorkoutEngine.advance(run(), 70_000L + plan.stages.takeWhile { it.kind != StageKind.REST }.size * 3000L, catalogue)
        assertEquals(StageKind.REST, atRest.currentStage!!.kind)
        val result = WorkoutEngine.advance(atRest, 10_000, catalogue)
        assertEquals(atRest.activeMillis, result.activeMillis)
        assertEquals(atRest.calories, result.calories, 0.0000001)
        assertEquals(atRest.elapsedMillis + 10_000, result.elapsedMillis)
    }
    @Test fun aDelayedTickStopsAtThePlannedFinish() {
        val result = WorkoutEngine.advance(run(), 900_000, catalogue)
        assertTrue(result.finished && result.paused)
        assertEquals(120_000L, result.elapsedMillis)
        assertEquals(100_000L, result.activeMillis)
        assertEquals(WorkoutGenerator.estimate(plan, catalogue, 70.0), result.calories, 0.000001)
        assertEquals(result, WorkoutEngine.advance(result, 50_000, catalogue))
    }
    @Test fun skippedTimeNeverBecomesMovementOrCalories() {
        val partial = WorkoutEngine.advance(run(), 5_000, catalogue)
        val skipped = WorkoutEngine.skip(partial)
        assertEquals(1, skipped.stageIndex)
        assertEquals(2_000L, skipped.activeMillis)
        assertEquals(2_000L, skipped.elapsedMillis)
        assertEquals(partial.calories, skipped.calories, 0.0)
    }
    @Test fun skippingEveryIntervalRecordsNoActivity() {
        var result = run()
        repeat(plan.stages.size) { result = WorkoutEngine.skip(result) }
        assertTrue(result.finished)
        assertEquals(0L, result.activeMillis)
        assertEquals(0.0, result.calories, 0.0)
    }
    @Test fun irregularTicksDoNotDriftOrRoundUpCredit() {
        var result = run()
        repeat(360) { result = WorkoutEngine.advance(result, 333, catalogue) }
        result = WorkoutEngine.advance(result, 120, catalogue)
        val oneTick = WorkoutEngine.advance(run(), 120_000, catalogue)
        assertEquals(oneTick.elapsedMillis, result.elapsedMillis)
        assertEquals(oneTick.activeMillis, result.activeMillis)
        assertEquals(oneTick.calories, result.calories, 0.000001)
    }
    @Test fun aCheckpointRestoresPausedWithoutWallClockCredit() {
        val progressed = WorkoutEngine.advance(run(), 15_000, catalogue)
        val restored = WorkoutEngine.restore(Gson().fromJson(Gson().toJson(progressed), WorkoutRun::class.java))
        assertTrue(restored.paused)
        assertEquals(restored, WorkoutEngine.advance(restored, 3600_000, catalogue))
        assertEquals(12_000L, restored.activeMillis)
    }
    @Test fun earlyFinishPreservesOnlyActualMovement() {
        val progressed = WorkoutEngine.advance(run(), 12_345, catalogue)
        val ended = WorkoutEngine.end(progressed)
        assertTrue(ended.finished && ended.paused && ended.endedEarly)
        assertEquals(9_345L, ended.activeMillis)
        assertEquals(progressed.calories, ended.calories, 0.0)
    }
    @Test fun countInHasNoMovementElapsedOrEnergyCredit() {
        val preparing = WorkoutEngine.advance(run(), 2_999, catalogue)
        assertEquals(1L, preparing.preparationMillis)
        assertEquals(0L, preparing.activeMillis)
        assertEquals(0L, preparing.elapsedMillis)
        assertEquals(0L, preparing.stageMillis)
        assertEquals(0.0, preparing.calories, 0.0)
        val moving = WorkoutEngine.advance(preparing, 1001, catalogue)
        assertEquals(0L, moving.preparationMillis)
        assertEquals(1000L, moving.activeMillis)
        assertEquals(1000L, moving.stageMillis)
    }
    @Test fun eachMovementGetsACountInButRestDoesNot() {
        var current = run()
        plan.stages.forEach { stage ->
            assertEquals(if(stage.exerciseId == null) 0L else 3000L, current.preparationMillis)
            val next = WorkoutEngine.advance(current, current.preparationMillis + stage.seconds * 1000L, catalogue)
            assertEquals(current.elapsedMillis + stage.seconds * 1000L, next.elapsedMillis)
            current = next
        }
        assertTrue(current.finished)
        assertEquals(plan.durationSeconds*1000L, current.elapsedMillis)
    }
    @Test fun pauseSkipAndRestoreDuringPreparationCannotEarnCredit() {
        val preparing = WorkoutEngine.advance(run(), 1500, catalogue)
        val paused = preparing.copy(paused=true)
        assertEquals(paused, WorkoutEngine.advance(paused, 90_000, catalogue))
        val restored = WorkoutEngine.restore(Gson().fromJson(Gson().toJson(preparing), WorkoutRun::class.java))
        assertTrue(restored.paused)
        assertEquals(3000L, restored.preparationMillis)
        val skipped = WorkoutEngine.skip(preparing)
        assertEquals(0L, skipped.activeMillis)
        assertEquals(0.0, skipped.calories, 0.0)
        assertEquals(3000L, skipped.preparationMillis)
    }
    @Test fun oldJsonCheckpointsRemainReadable() {
        val json = Gson().toJsonTree(run()).asJsonObject.apply { remove("preparationMillis") }
        val restored = WorkoutEngine.restore(Gson().fromJson(json, WorkoutRun::class.java))
        assertTrue(restored.paused)
        assertEquals(3000L, restored.preparationMillis)
        assertEquals(0L, restored.activeMillis)
    }
    @Test fun spokenCountdownsCoverBothEndsAndStaySilentWhilePaused() {
        val start = run()
        assertEquals(WorkoutCue.THREE, start.audioCue())
        assertEquals(WorkoutCue.TWO, WorkoutEngine.advance(start,1000,catalogue).audioCue())
        assertEquals(WorkoutCue.ONE, WorkoutEngine.advance(start,2000,catalogue).audioCue())
        assertEquals(WorkoutCue.GO, WorkoutEngine.advance(start,3000,catalogue).audioCue())
        assertNull(start.copy(paused=true).audioCue())
        for (remaining in 1..3) {
            val ending = start.copy(preparationMillis=0,stageMillis=(start.currentStage!!.seconds-remaining)*1000L)
            assertEquals(listOf(WorkoutCue.ONE,WorkoutCue.TWO,WorkoutCue.THREE)[remaining-1],ending.audioCue())
        }
        val restIndex = plan.stages.indexOfFirst { it.kind == StageKind.REST }
        val rest = start.copy(stageIndex=restIndex,preparationMillis=0,stageMillis=(plan.stages[restIndex].seconds-2)*1000L)
        assertNull(rest.audioCue())
        assertEquals(WorkoutCue.DONE, WorkoutEngine.advance(start,900_000,catalogue).audioCue())
        assertNull(WorkoutEngine.end(start).audioCue())
    }
    @Test fun negativeDeltasAreRejected() { assertThrows(IllegalArgumentException::class.java) { WorkoutEngine.advance(run(), -1, catalogue) } }
}
class EnergyTest {
    @Test fun publishedMetEquationUsesMinutesAndKilograms() { assertEquals(36.75, Energy.calories(3.0, 70.0, 600_000), 0.000001) }
    @Test fun aDoubleWeightDoublesTheEstimate() { assertEquals(Energy.calories(4.0, 70.0, 60_000) * 2, Energy.calories(4.0, 140.0, 60_000), 0.000001) }
    @Test fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Energy.calories(4.0, Double.NaN, 1000) }
        assertThrows(IllegalArgumentException::class.java) { Energy.calories(Double.POSITIVE_INFINITY, 70.0, 1000) }
        assertThrows(IllegalArgumentException::class.java) { Energy.calories(4.0, 70.0, -1) }
    }
}

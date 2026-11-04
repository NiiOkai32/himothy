package com.campusfit.feature.workouts

import com.campusfit.domain.Profile
import com.campusfit.domain.workouts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profile = Profile("user", "Ama", 21, 170.0, 70.0, 68.0, "Beginner", 5, "UG", "", true, true, true)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun startingAndRestoringAlwaysWaitForTheUser() = runTest(dispatcher) {
        val repo = FakeWorkouts()
        val vm = WorkoutsViewModel(repo, WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent()
        vm.generate(2, "Beginner", true, false); runCurrent()
        vm.startPreview(); runCurrent()
        assertTrue(vm.state.value.run!!.paused)
        assertEquals(0L, vm.state.value.run!!.activeMillis)
        val restored = WorkoutsViewModel(repo, WorkoutClock { testScheduler.currentTime })
        restored.load(profile); runCurrent()
        assertTrue(restored.state.value.run!!.paused)
    }
    @Test fun leavingForegroundAndResumingNeverCreditsTheGap() = runTest(dispatcher) {
        val vm = WorkoutsViewModel(FakeWorkouts(), WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent()
        vm.toggle(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        vm.pause(); runCurrent()
        val before = vm.state.value.run!!.activeMillis
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2_000L, before)
        assertEquals(before, vm.state.value.run!!.activeMillis)
        vm.toggle(); runCurrent(); advanceTimeBy(5_000); runCurrent(); vm.pause(); runCurrent()
        assertEquals(7_000L, vm.state.value.run!!.activeMillis)
    }
    @Test fun storageFailurePausesAndRetryKeepsTheActualCredit() = runTest(dispatcher) {
        val repo = FakeWorkouts()
        val vm = WorkoutsViewModel(repo, WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent(); vm.toggle(); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        repo.failSave = true
        advanceTimeBy(5_000); runCurrent()
        assertTrue(vm.state.value.run!!.paused)
        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.error)
        assertEquals(2_000L, vm.state.value.run!!.activeMillis)
        repo.failSave = false
        vm.retrySave(); runCurrent()
        assertTrue(vm.state.value.saved)
        assertNull(vm.state.value.error)
        assertEquals(2_000L, vm.state.value.run!!.activeMillis)
    }
    @Test fun anUnfinishedWorkoutCannotBeReplaced() = runTest(dispatcher) {
        val vm = WorkoutsViewModel(FakeWorkouts(), WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent()
        val id = vm.state.value.run!!.id
        vm.generate(10, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent()
        assertEquals(id, vm.state.value.run!!.id)
        assertNotNull(vm.state.value.error)
    }
    @Test fun reusingAPlanCreatesANewSessionIdentity() = runTest(dispatcher) {
        val vm = WorkoutsViewModel(FakeWorkouts(), WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent()
        val firstId = vm.state.value.run!!.id
        vm.end(); runCurrent(); vm.startPreview(); runCurrent()
        assertNotEquals(firstId, vm.state.value.run!!.id)
        assertFalse(vm.state.value.run!!.finished)
    }
    @Test fun foregroundPauseUsesEventTimeEvenWhenStorageIsSlow() = runTest(dispatcher) {
        val repo = FakeWorkouts()
        val vm = WorkoutsViewModel(repo, WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2, "Beginner", true, false); runCurrent(); vm.startPreview(); runCurrent(); vm.toggle(); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        repo.saveDelay = 5_000
        advanceTimeBy(1_500); runCurrent()
        vm.pause(); runCurrent()
        assertTrue(vm.state.value.run!!.paused)
        advanceTimeBy(20_000); runCurrent()
        assertTrue(vm.state.value.run!!.paused)
        assertTrue(vm.state.value.saved)
        assertEquals(2_500L, vm.state.value.run!!.activeMillis)
    }
    @Test fun pausingDuringTheCountInRestartsAtThreeWithoutRecordingTheGap() = runTest(dispatcher) {
        val vm = WorkoutsViewModel(FakeWorkouts(), WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent(); vm.generate(2,"Beginner",true,false); runCurrent(); vm.startPreview(); runCurrent()
        vm.toggle(); runCurrent(); advanceTimeBy(1500); runCurrent(); vm.pause(); runCurrent()
        assertEquals(1500L,vm.state.value.run!!.preparationMillis)
        advanceTimeBy(30_000); runCurrent()
        vm.toggle(); runCurrent()
        assertEquals(3000L,vm.state.value.run!!.preparationMillis)
        advanceTimeBy(4000); runCurrent(); vm.pause(); runCurrent()
        assertEquals(1000L,vm.state.value.run!!.activeMillis)
    }
    @Test fun retryAfterCatalogueFailureLoadsRealContent() = runTest(dispatcher) {
        val repo = FakeWorkouts().apply { failRead = true }
        val vm = WorkoutsViewModel(repo, WorkoutClock { testScheduler.currentTime })
        vm.load(profile); runCurrent()
        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.error)
        repo.failRead = false; vm.load(profile); runCurrent()
        assertEquals(72, vm.state.value.exercises.size)
    }
}
private class FakeWorkouts : WorkoutRepository {
    var checkpoint: WorkoutRun? = null
    var failSave = false
    var saveDelay = 0L
    var failRead = false
    override suspend fun catalogue(): List<Exercise> { check(!failRead); return loadTestCatalogue() }
    override suspend fun active(uid: String): WorkoutRun? = checkpoint?.takeIf { !it.finished }?.copy(paused = true)
    override suspend fun save(run: WorkoutRun) { if (saveDelay > 0) kotlinx.coroutines.delay(saveDelay); check(!failSave); checkpoint = run.copy(paused = true) }
}

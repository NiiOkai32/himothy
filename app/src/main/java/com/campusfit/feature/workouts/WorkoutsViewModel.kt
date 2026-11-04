package com.campusfit.feature.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusfit.domain.Profile
import com.campusfit.domain.workouts.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class WorkoutsState(
    val loading: Boolean = true, val exercises: List<Exercise> = emptyList(),
    val preview: WorkoutPlan? = null, val run: WorkoutRun? = null,
    val error: String? = null, val saving: Boolean = false, val saved: Boolean = true,
)
@HiltViewModel class WorkoutsViewModel @Inject constructor(private val repository: WorkoutRepository, private val clock: WorkoutClock) : ViewModel() {
    private val mutable = MutableStateFlow(WorkoutsState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var ticker: Job? = null
    private var lastTick = 0L
    private var checkpoint = 0L
    private var loadedUid: String? = null
    private var owner: Profile? = null

    fun load(profile: Profile) {
        if (loadedUid == profile.uid) { owner = profile; return }
        perform {
            mutable.update { it.copy(loading = true, error = null) }
            owner = profile
            val catalogue = repository.catalogue()
            val restored = repository.active(profile.uid)
            restored?.let { run ->
                run.plan.validate(catalogue)
                require(run.weightKg.isFinite() && run.weightKg in 35.0..350.0)
                require(run.activeMillis >= 0 && run.activeMillis <= run.elapsedMillis && run.elapsedMillis <= run.plan.durationSeconds * 1000L)
                require(run.calories.isFinite() && run.calories >= 0)
                require(run.stageIndex in run.plan.stages.indices)
                require(run.stageMillis in 0 until run.currentStage!!.seconds * 1000L)
                require(run.preparationMillis in 0..3000)
            }
            loadedUid = profile.uid
            mutable.value = WorkoutsState(loading = false, exercises = catalogue, run = restored)
        }
    }
    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    ticker?.cancel()
                    mutable.update { it.copy(loading = false, saving = false, run = it.run?.copy(paused = true), error = if (e is IllegalArgumentException) e.message else "Could not load or save your workout. Your last saved checkpoint is kept; please retry.") }
                }
            }
        }
    }
    fun generate(minutes: Int, level: String, quiet: Boolean, gentle: Boolean, category: String? = null, exerciseId: String? = null) = perform {
        val plan = WorkoutGenerator.generate(mutable.value.exercises, minutes, level, quiet, gentle, category, singleExerciseId = exerciseId)
        mutable.update { it.copy(preview = plan, error = null) }
    }
    fun clearPreview() { mutable.update { it.copy(preview = null, error = null) } }
    fun startPreview() = perform {
        require(mutable.value.run?.finished != false) { "Finish or end your saved workout before starting another." }
        val profile = checkNotNull(owner)
        val plan = checkNotNull(mutable.value.preview)
        val run = WorkoutRun(java.util.UUID.randomUUID().toString(), profile.uid, plan, profile.weightKg, startedAt = System.currentTimeMillis())
        mutable.update { it.copy(run = run, error = null, saved = false) }
        persist()
        // Starting on a paused instruction screen gives time to inspect form and enable cues.
    }
    private suspend fun persist() {
        val run = mutable.value.run ?: return
        mutable.update { it.copy(saving = true, saved = false) }
        repository.save(run)
        mutable.update { it.copy(saving = false, saved = true, error = null) }
        checkpoint = clock.now()
    }
    private suspend fun tickNow(now: Long = clock.now(), saveCheckpoint: Boolean = true) {
        val run = mutable.value.run ?: return
        val updated = WorkoutEngine.advance(run, (now - lastTick).coerceAtLeast(0), mutable.value.exercises)
        lastTick = now
        mutable.update { it.copy(run = updated) }
        if (updated != run) mutable.update { it.copy(saved = false) }
        if (saveCheckpoint && (updated.stageIndex != run.stageIndex || now - checkpoint >= 1000L || updated.finished)) persist()
    }
    private fun startClock() {
        ticker?.cancel()
        lastTick = clock.now()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(250)
                mutex.withLock {
                    if (mutable.value.run?.let { it.paused || it.finished } != false) return@launch
                    try { tickNow() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        mutable.update { it.copy(run = it.run?.copy(paused = true), error = "Workout paused because saving failed. Retry before continuing.", saving = false, saved = false) }
                        return@launch
                    }
                }
            }
        }
    }
    fun toggle() = perform {
        val run = mutable.value.run ?: return@perform
        if (run.finished) return@perform
        if (run.paused) {
            persist()
            mutable.update { it.copy(run = run.copy(paused = false,
                preparationMillis = if (run.preparationMillis > 0) 3000L else 0L)) }
            startClock()
        } else {
            tickNow(saveCheckpoint = false)
            ticker?.cancel()
            mutable.update { it.copy(run = it.run?.copy(paused = true)) }
            persist()
        }
    }
    fun pause() {
        // Capture STOP immediately, before any database operation waiting on the mutex.
        val stoppedAt = clock.now()
        ticker?.cancel()
        perform {
            if (mutable.value.run?.paused == false) tickNow(stoppedAt, saveCheckpoint = false)
            mutable.update { it.copy(run = it.run?.copy(paused = true)) }
            if (mutable.value.run != null) persist()
        }
    }
    fun skip() = perform {
        val run = mutable.value.run ?: return@perform
        if (run.finished) return@perform
        if (!run.paused) tickNow(saveCheckpoint = false)
        mutable.update { it.copy(run = it.run?.let(WorkoutEngine::skip)) }
        persist()
        lastTick = clock.now()
    }
    fun end() = perform {
        if (mutable.value.run?.paused == false) tickNow(saveCheckpoint = false)
        ticker?.cancel()
        mutable.update { it.copy(run = it.run?.let(WorkoutEngine::end)) }
        persist()
    }
    fun retrySave() = perform { persist() }
}

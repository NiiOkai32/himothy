package com.campusfit.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusfit.data.local.*
import com.campusfit.data.tracking.*
import com.campusfit.domain.*
import com.campusfit.domain.tracking.*
import com.campusfit.domain.workouts.WorkoutClock
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class TrackingState(
    val loading: Boolean = true, val weights: List<WeighInEntity> = emptyList(), val sessions: List<WorkoutSessionEntity> = emptyList(),
    val routes: List<OutdoorRun> = emptyList(), val outdoor: OutdoorRun? = null,
    val health: HealthSummary? = null, val healthEnabled: Boolean = false, val healthStatus: String? = null,
    val saving: Boolean = false, val error: String? = null, val message: String? = null, val cameraSaved: String? = null,
)
@HiltViewModel class TrackingViewModel @Inject constructor(
    private val repository: TrackingRepository, val health: HealthConnectGateway, private val auth: AuthRepository, private val clock: WorkoutClock,
) : ViewModel() {
    private val mutable = MutableStateFlow(TrackingState())
    val state = mutable.asStateFlow()
    private var owner: Profile? = null
    private val mutex = Mutex()
    private var collectors: Job? = null
    private var ticker: Job? = null
    private var lastTick = 0L
    private var lastSaved = 0L
    private var segmentStarted = 0L
    private val gson = Gson()
    val cloudAvailable get() = auth.cloudConfigured && auth.account.value?.demo == false
    fun load(profile: Profile) {
        if (owner?.uid == profile.uid) { owner = profile; return }
        owner = profile
        collectors?.cancel()
        collectors = viewModelScope.launch {
            try {
                mutable.update { it.copy(loading = true) }
                coroutineScope {
                    launch { repository.weights(profile.uid).collect { values -> mutable.update { it.copy(weights = values, loading = false) } } }
                    launch { repository.sessions(profile.uid).collect { values -> mutable.update { it.copy(sessions = values) } } }
                    launch { repository.routes(profile.uid).collect { values ->
                        val runs = values.map { gson.fromJson(it.payloadJson, OutdoorRun::class.java) }
                        mutable.update { it.copy(routes = runs, outdoor = it.outdoor ?: runs.firstOrNull { run -> !run.finished }?.copy(paused = true)) }
                    } }
                    launch {
                        val enabled = repository.healthEnabled(profile.uid)
                        mutable.update { it.copy(healthEnabled = enabled) }
                        if (enabled) refreshHealth()
                    }
                    repository.startSync(profile.uid)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { owner = null; mutable.update { it.copy(loading = false, error = "Could not load tracking data. Reopen this screen to retry.") } }
        }
    }
    private fun perform(action: suspend () -> Unit) { viewModelScope.launch { mutex.withLock {
        try { mutable.update { it.copy(error = null, message = null) }; action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { ticker?.cancel(); mutable.update { it.copy(saving = false, outdoor = it.outdoor?.copy(paused = true), error = if (e is IllegalArgumentException || e is IllegalStateException) e.message else "Could not save tracking data. The previous device copy is kept; please retry.") } }
    } } }
    fun weigh(kg: String) = perform {
        val value = kg.toDoubleOrNull() ?: throw IllegalArgumentException("Enter your weight in kilograms using a decimal point.")
        repository.weigh(checkNotNull(owner).uid, value)
        mutable.update { it.copy(message = "Today's private weigh-in was saved. Daily entries use UTC.") }
    }
    fun sync() = perform { owner?.uid?.let { repository.refreshCloud(it) }; mutable.update { it.copy(message = if (cloudAvailable) "Sync queued. It will run with an internet connection." else "Local demo stays on this device; sign in to use cloud sync.") } }
    fun connectHealth() = perform {
        val uid = checkNotNull(owner).uid
        check(health.status() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) { "Install or update Health Connect first." }
        check(health.granted()) { "Health permissions were not granted. You can keep using manual tracking." }
        repository.healthEnabled(uid, true)
        mutable.update { it.copy(healthEnabled = true) }
        refreshHealth()
    }
    fun disconnectHealth() = perform {
        health.disconnect(); repository.healthEnabled(checkNotNull(owner).uid, false)
        mutable.update { it.copy(healthEnabled = false, health = null, healthStatus = "Health Connect disconnected. No provider records were deleted.") }
    }
    fun refresh() = perform { if (mutable.value.healthEnabled) refreshHealth() }
    private suspend fun refreshHealth() {
        try {
            if (health.status() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE || !health.granted()) {
                mutable.update { it.copy(health = null, healthStatus = "Health Connect access is unavailable or revoked. Reconnect to refresh.") }; return
            }
            val summary = health.today()
            mutable.update { it.copy(health = summary, healthStatus = null) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.update { it.copy(health = null, healthStatus = "Could not read Health Connect. Check permissions and retry.") } }
    }
    private fun currentWeight() = mutable.value.weights.lastOrNull()?.weightKg ?: checkNotNull(owner).weightKg
    fun startOutdoor(mode: String) = perform {
        require(mode in listOf("walk", "run"))
        require(mutable.value.outdoor?.finished != false) { "Resume or finish your saved outdoor session first." }
        val run = OutdoorRun(java.util.UUID.randomUUID().toString(), checkNotNull(owner).uid, mode, currentWeight(), System.currentTimeMillis())
        repository.saveOutdoor(run)
        mutable.update { it.copy(outdoor = run) }
    }
    private suspend fun tick(now: Long = clock.now()) {
        val run = mutable.value.outdoor ?: return
        mutable.update { it.copy(outdoor = RoutePolicy.tick(run, (now-lastTick).coerceAtLeast(0))) }
        lastTick = now
    }
    private suspend fun persist() {
        val run = mutable.value.outdoor ?: return
        mutable.update { it.copy(saving = true) }
        repository.saveOutdoor(run)
        mutable.update { it.copy(saving = false) }; lastSaved = clock.now()
    }
    fun toggleOutdoor() = perform {
        val run = mutable.value.outdoor ?: return@perform
        if (run.finished) return@perform
        if (!run.paused) { tick(); ticker?.cancel(); mutable.update { it.copy(outdoor = it.outdoor?.copy(paused = true)) }; persist() }
        else {
            mutable.update { it.copy(outdoor = run.copy(paused = false, segment = run.segment + 1, lastFixElapsed = null)) }
            lastTick = clock.now(); segmentStarted = lastTick; ticker?.cancel()
            ticker = viewModelScope.launch {
                while (isActive) {
                    delay(500)
                    mutex.withLock {
                        if (mutable.value.outdoor?.let { it.paused || it.finished } != false) return@launch
                        try { tick(); if (clock.now()-lastSaved >= 5000 || mutable.value.outdoor?.finished == true) persist() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { mutable.update { it.copy(outdoor = it.outdoor?.copy(paused = true), saving = false, error = "GPS session paused because saving failed. Retry saving before leaving.") }; return@launch }
                    }
                }
            }
        }
    }
    fun pauseOutdoor() {
        val now = clock.now(); ticker?.cancel()
        perform { if (mutable.value.outdoor?.paused == false) tick(now); mutable.update { it.copy(outdoor = it.outdoor?.copy(paused = true)) }; if (mutable.value.outdoor != null) persist() }
    }
    fun point(latitude: Double, longitude: Double, accuracy: Float, elapsed: Long, mock: Boolean) {
        viewModelScope.launch { mutex.withLock {
            val run = mutable.value.outdoor ?: return@withLock
            if (run.paused || run.finished || elapsed < segmentStarted) return@withLock
            mutable.update { it.copy(outdoor = RoutePolicy.add(run, GeoPoint(latitude, longitude, accuracy, elapsed, run.segment), mock)) }
        } }
    }
    fun finishOutdoor() = perform {
        if (mutable.value.outdoor?.paused == false) tick()
        ticker?.cancel(); mutable.update { it.copy(outdoor = it.outdoor?.copy(paused = true, finished = true)) }; persist()
    }
    fun retryOutdoor() = perform { persist() }
    fun saveCamera(id: String, startedAt: Long, seconds: Int, reps: Int, met: Double) = perform {
        mutable.update { it.copy(saving = true) }
        repository.saveCamera(checkNotNull(owner).uid, id, startedAt, seconds, reps, met, currentWeight())
        mutable.update { it.copy(saving = false, cameraSaved = id) }
    }
}

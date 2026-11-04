package com.campusfit.domain.tracking

import kotlin.math.*
import java.time.Instant
import java.time.ZoneOffset

object WeightPolicy {
    fun utcDay(timeMillis: Long): Long = Instant.ofEpochMilli(timeMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    fun validate(weightKg: Double) { require(weightKg.isFinite() && weightKg in 35.0..350.0) { "Enter a weight between 35 and 350 kg." } }
    fun lossPercent(baseline: Double, current: Double): Double {
        validate(baseline); validate(current)
        return (baseline - current) / baseline * 100
    }
    fun unusual(previousKg: Double, currentKg: Double, days: Long): Boolean {
        validate(previousKg); validate(currentKg); require(days > 0)
        // More than 2% compound loss per day. Gains never earn a loss badge.
        return (1 - (currentKg / previousKg).pow(1.0 / days)) * 100 > 2.0 + 1e-9
    }
    fun supported(flagged: Boolean, movementSeconds: Int): Boolean = !flagged && movementSeconds >= 300
}
data class GeoPoint(val latitude: Double, val longitude: Double, val accuracy: Float, val elapsedMillis: Long, val segment: Int)
data class OutdoorRun(
    val id: String, val uid: String, val mode: String, val weightKg: Double, val startedAt: Long,
    val elapsedMillis: Long = 0, val distanceMeters: Double = 0.0, val points: List<GeoPoint> = emptyList(),
    val paused: Boolean = true, val finished: Boolean = false, val segment: Int = 0, val lastFixElapsed: Long? = null,
)
object RoutePolicy {
    const val MAX_MILLIS = 20 * 60 * 1000L
    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371000.0
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat/2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon/2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun add(run: OutdoorRun, point: GeoPoint, mock: Boolean): OutdoorRun {
        if (run.paused || run.finished || mock || run.points.size >= 1200) return run
        if (!point.latitude.isFinite() || !point.longitude.isFinite() || point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0 ||
            !point.accuracy.isFinite() || point.accuracy !in 0f..35f || point.elapsedMillis < 0 || point.segment != run.segment) return run
        val previous = run.points.lastOrNull()
        if (previous != null && previous.segment == point.segment && point.elapsedMillis <= (run.lastFixElapsed ?: previous.elapsedMillis)) return run
        val sameSegment = previous != null && previous.segment == point.segment
        val connected = sameSegment && point.elapsedMillis - (run.lastFixElapsed ?: previous!!.elapsedMillis) <= 10_000
        if (sameSegment && !connected) {
            return run.copy(points = run.points + point.copy(segment = run.segment + 1), segment = run.segment + 1, lastFixElapsed = point.elapsedMillis)
        }
        val distance = if (connected) meters(previous!!, point) else 0.0
        if (connected && distance / ((point.elapsedMillis - previous!!.elapsedMillis) / 1000.0) > 12.0) return run
        // Ignore position jitter below the smaller accuracy radius; retain a stable anchor.
        if (connected && distance < max(3.0, min(previous!!.accuracy, point.accuracy).toDouble())) return run.copy(lastFixElapsed = point.elapsedMillis)
        return run.copy(points = run.points + point, distanceMeters = run.distanceMeters + distance, lastFixElapsed = point.elapsedMillis)
    }
    fun tick(run: OutdoorRun, milliseconds: Long): OutdoorRun {
        require(milliseconds >= 0)
        if (run.paused || run.finished) return run
        val elapsed = (run.elapsedMillis + milliseconds).coerceAtMost(MAX_MILLIS)
        return run.copy(elapsedMillis = elapsed, paused = elapsed == MAX_MILLIS, finished = elapsed == MAX_MILLIS)
    }
}
/** One full neutral → extended/deep → neutral cycle, with hysteresis and a cooldown.
 * Invalid or low-confidence frames reset the cycle instead of guessing missing motion. */
class RepCounter(private val low: Double, private val high: Double) {
    init { require(low < high) }
    var count: Int = 0; private set
    private var armed = false
    private var extended = false
    private var lastRep = Long.MIN_VALUE
    private var lastFrame = -1L
    fun reset() { armed = false; extended = false; lastFrame = -1L }
    fun frame(value: Double?, time: Long): Int {
        if (value == null || !value.isFinite()) { reset(); return count }
        if (time <= lastFrame) return count
        if (lastFrame >= 0 && time - lastFrame > 1500) { armed = false; extended = false }
        lastFrame = time
        if (value >= high) {
            if (armed && extended && (lastRep == Long.MIN_VALUE || time - lastRep >= 700)) { count++; lastRep = time }
            armed = true; extended = false
        } else if (value <= low && armed) extended = true
        return count
    }
}
object PoseAngles {
    fun angle(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double? {
        val ux = ax-bx; val uy = ay-by; val vx = cx-bx; val vy = cy-by
        val length = hypot(ux,uy) * hypot(vx,vy)
        if (length < 1e-6 || !length.isFinite()) return null
        return Math.toDegrees(acos(((ux*vx+uy*vy)/length).coerceIn(-1.0,1.0)))
    }
}

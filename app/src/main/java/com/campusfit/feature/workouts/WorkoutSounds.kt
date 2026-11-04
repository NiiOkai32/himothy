package com.campusfit.feature.workouts

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.campusfit.R
import com.campusfit.domain.workouts.WorkoutCue

/** Bundled spoken digits work with no internet or installed TTS engine.
 * Uses media volume, with no alarm stream or interruption-policy override. */
internal class WorkoutSounds(context: Context) {
    var ready by mutableStateOf(false)
        private set
    private var released = false
    private var stream = 0
    private val loaded = mutableSetOf<Int>()
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    ).build()
    private val samples: Map<WorkoutCue,Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (!released && status == 0) { loaded.add(id); ready = loaded.size == 5 }
        }
        samples = mapOf(
            WorkoutCue.THREE to pool.load(context,R.raw.count_three,1),
            WorkoutCue.TWO to pool.load(context,R.raw.count_two,1),
            WorkoutCue.ONE to pool.load(context,R.raw.count_one,1),
            WorkoutCue.GO to pool.load(context,R.raw.count_go,1),
            WorkoutCue.DONE to pool.load(context,R.raw.count_done,1),
        )
    }
    fun play(cue: WorkoutCue) {
        if (!ready || released) return
        stop()
        stream = pool.play(checkNotNull(samples[cue]),1f,1f,1,0,1f)
    }
    fun stop() { if (!released && stream != 0) { pool.stop(stream); stream = 0 } }
    fun release() { if (!released) { stop(); released = true; pool.setOnLoadCompleteListener(null); pool.release() } }
}

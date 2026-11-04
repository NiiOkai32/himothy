package com.campusfit.data.tracking

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun enqueue(uid: String, pull: Boolean = false) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val once = OneTimeWorkRequestBuilder<TrackingSyncWorker>().setInputData(workDataOf("uid" to uid, "pull" to pull)).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("tracking:$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, once)
    }
    fun observeAccount(uid: String) {
        val periodic = PeriodicWorkRequestBuilder<TrackingSyncWorker>(15, TimeUnit.MINUTES).setInputData(workDataOf("uid" to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("tracking-periodic:$uid", ExistingPeriodicWorkPolicy.KEEP, periodic)
        enqueue(uid, pull = true)
    }
    fun cancel(uid: String) {
        WorkManager.getInstance(context).cancelUniqueWork("tracking:$uid")
        WorkManager.getInstance(context).cancelUniqueWork("tracking-periodic:$uid")
    }
}
@HiltWorker class TrackingSyncWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted parameters: WorkerParameters, private val engine: TrackingSyncEngine,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (engine.sync(inputData.getString("uid") ?: return Result.failure(), inputData.getBoolean("pull", false))) {
        SyncResult.DONE -> Result.success()
        SyncResult.RETRY -> Result.retry()
        SyncResult.BLOCKED -> Result.failure()
    }
}
enum class SyncResult { DONE, RETRY, BLOCKED }

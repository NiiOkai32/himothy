package com.campusfit.data.tracking

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.*
import javax.inject.Inject

data class HealthSummary(val steps: Long, val exerciseMinutes: Long)
class HealthConnectGateway @Inject constructor(@ApplicationContext private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(ExerciseSessionRecord::class))
    fun status(): Int = if (android.os.Build.VERSION.SDK_INT < 28) HealthConnectClient.SDK_UNAVAILABLE else HealthConnectClient.getSdkStatus(context)
    private fun client() = HealthConnectClient.getOrCreate(context)
    suspend fun granted() = client().permissionController.getGrantedPermissions().containsAll(permissions)
    suspend fun disconnect() { if (status() == HealthConnectClient.SDK_AVAILABLE) client().permissionController.revokeAllPermissions() }
    suspend fun today(): HealthSummary {
        check(granted()) { "Health Connect access is not granted." }
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val result = client().aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), TimeRangeFilter.between(start, Instant.now())))
        return HealthSummary(result[StepsRecord.COUNT_TOTAL] ?: 0L, result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L)
    }
}

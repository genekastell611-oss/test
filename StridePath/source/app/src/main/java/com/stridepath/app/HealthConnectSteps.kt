package com.stridepath.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectSteps(private val context: Context) {
    val readStepsPermission: String = HealthPermission.getReadPermission(StepsRecord::class)

    fun status(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = status() == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return runCatching { client().permissionController.getGrantedPermissions().contains(readStepsPermission) }.getOrDefault(false)
    }

    suspend fun readSteps(date: LocalDate = LocalDate.now()): Int? {
        if (!isAvailable() || !hasPermission()) return null
        return readStepsGranted(date)
    }

    private suspend fun readStepsGranted(date: LocalDate): Int? {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            val result = client().aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            (result[StepsRecord.COUNT_TOTAL] ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }.getOrNull()
    }

    suspend fun readRecentSteps(days: Int = 31): Map<LocalDate, Int> {
        if (!isAvailable() || !hasPermission()) return emptyMap()
        val safeDays = days.coerceIn(1, 93)
        val today = LocalDate.now()
        return (0 until safeDays).mapNotNull { offset ->
            val date = today.minusDays(offset.toLong())
            readStepsGranted(date)?.let { date to it }
        }.toMap()
    }
}

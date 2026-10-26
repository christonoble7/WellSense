package com.wellsense.app

import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads today's wearable data that the watch app has shared into Android Health Connect. */
@CapacitorPlugin(name = "HealthConnectBridge")
class HealthConnectPlugin : Plugin() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val perms = setOf(
        StepsRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, SleepSessionRecord::class,
        OxygenSaturationRecord::class, HeartRateVariabilityRmssdRecord::class, ExerciseSessionRecord::class
    ).map { HealthPermission.getReadPermission(it) }.toSet()

    private fun client() = HealthConnectClient.getOrCreate(context)
    private inline fun safe(f: () -> Unit) { try { f() } catch (e: Exception) { /* type not shared or no permission: skip */ } }

    @PluginMethod
    fun status(call: PluginCall) {
        val s = HealthConnectClient.getSdkStatus(context)
        val o = JSObject()
        o.put("available", s == HealthConnectClient.SDK_AVAILABLE)
        o.put("message", when (s) {
            HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect is not supported on this device."
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Install or update Health Connect from Google Play."
            else -> "OK"
        })
        call.resolve(o)
    }

    private fun granted(call: PluginCall, g: Boolean) { val o = JSObject(); o.put("granted", g); call.resolve(o) }

    @PluginMethod
    fun requestPermissions(call: PluginCall) {
        scope.launch {
            try {
                val have = client().permissionController.getGrantedPermissions()
                if (have.any { it in perms }) { granted(call, true); return@launch }
                withContext(Dispatchers.Main) {
                    val i = PermissionController.createRequestPermissionResultContract().createIntent(context, perms)
                    startActivityForResult(call, i, "permResult")
                }
            } catch (e: Exception) { call.reject(e.message) }
        }
    }

    @ActivityCallback
    private fun permResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        scope.launch {
            try { granted(call, client().permissionController.getGrantedPermissions().any { it in perms }) }
            catch (e: Exception) { call.reject(e.message) }
        }
    }

    @PluginMethod
    fun readToday(call: PluginCall) {
        scope.launch {
            try {
                val c = client(); val zone = ZoneId.systemDefault(); val now = Instant.now()
                val mid = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val day = TimeRangeFilter.between(mid, now)
                val night = TimeRangeFilter.between(mid.minus(Duration.ofHours(6)), now) // last night's sleep
                val o = JSObject()
                safe { c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), day))[StepsRecord.COUNT_TOTAL]?.let { o.put("steps", it) } }
                safe { c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), day))[HeartRateRecord.BPM_AVG]?.let { o.put("hr", it) } }
                safe { c.aggregate(AggregateRequest(setOf(RestingHeartRateRecord.BPM_AVG), day))[RestingHeartRateRecord.BPM_AVG]?.let { o.put("rhr", it) } }
                safe { c.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), night))[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { o.put("sleep", it.toMinutes() / 60.0) } }
                safe { c.aggregate(AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), day))[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.let { o.put("act", it.toMinutes()) } }
                safe { val r = c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, day)).records; if (r.isNotEmpty()) o.put("spo2", r.map { it.percentage.value }.average()) }
                safe { val r = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, day)).records; if (r.isNotEmpty()) o.put("hrv", r.map { it.heartRateVariabilityMillis }.average()) }
                call.resolve(o)
            } catch (e: Exception) { call.reject(e.message) }
        }
    }
}

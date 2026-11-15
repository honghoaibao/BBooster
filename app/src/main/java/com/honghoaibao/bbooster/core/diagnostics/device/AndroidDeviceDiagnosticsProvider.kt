package com.honghoaibao.bbooster.core.diagnostics.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogModule
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects a point-in-time snapshot using public Android APIs only.
 *
 * No Shizuku, root, package enumeration or dangerous permissions are needed.
 * Missing system telemetry is represented as null at the relevant field.
 */
class AndroidDeviceDiagnosticsProvider(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val uptimeMillis: () -> Long = SystemClock::elapsedRealtime
) : DeviceDiagnosticsProvider {

    private val applicationContext = context.applicationContext

    override suspend fun collect(): DeviceDiagnostics = withContext(Dispatchers.IO) {
        val memory = readMemory()
        val storage = readStorage()
        val battery = readBattery()

        val snapshot = DeviceDiagnostics(
            collectedAtMillis = clockMillis().coerceAtLeast(0L),
            android = AndroidDiagnostics(
                release = Build.VERSION.RELEASE.orEmpty(),
                sdkLevel = Build.VERSION.SDK_INT
            ),
            device = DeviceHardwareDiagnostics(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                cpuAbis = Build.SUPPORTED_ABIS
                    .orEmpty()
                    .filter(String::isNotBlank)
                    .distinct()
                    .toList()
            ),
            memory = memory,
            storage = storage,
            battery = battery,
            uptimeMillis = uptimeMillis().coerceAtLeast(0L)
        )

        AppLog.debug(
            LogModule.DEVICE,
            "device_diagnostics_collected",
            "sdk=" + snapshot.android.sdkLevel +
                "; memoryAvailable=" + (memory != null) +
                "; storageAvailable=" + (storage != null) +
                "; batteryAvailable=" + (battery != null)
        )
        snapshot
    }

    private fun readMemory(): MemoryDiagnostics? = readSafely("memory_read_failed") {
        val manager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
            as? ActivityManager
            ?: return@readSafely null

        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)

        if (info.totalMem <= 0L) return@readSafely null

        MemoryDiagnostics(
            totalBytes = info.totalMem,
            availableBytes = DeviceDiagnosticsMath.availableBytes(
                info.totalMem,
                info.availMem
            ),
            thresholdBytes = info.threshold.coerceAtLeast(0L),
            isLowMemory = info.lowMemory
        )
    }

    private fun readStorage(): StorageDiagnostics? = readSafely("storage_read_failed") {
        val dataDirectory: File = Environment.getDataDirectory()
        val stat = StatFs(dataDirectory.absolutePath)
        val totalBytes = DeviceDiagnosticsMath.multiplyBytes(
            stat.blockCountLong,
            stat.blockSizeLong
        )
        if (totalBytes <= 0L) return@readSafely null

        val availableBytes = DeviceDiagnosticsMath.availableBytes(
            totalBytes,
            DeviceDiagnosticsMath.multiplyBytes(
                stat.availableBlocksLong,
                stat.blockSizeLong
            )
        )

        StorageDiagnostics(
            totalBytes = totalBytes,
            availableBytes = availableBytes
        )
    }

    @Suppress("DEPRECATION")
    private fun readBattery(): BatteryDiagnostics? = readSafely("battery_read_failed") {
        val intent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return@readSafely null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val rawStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val rawPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        BatteryDiagnostics(
            levelPercent = DeviceDiagnosticsMath.batteryPercent(level, scale),
            status = rawStatus.toBatteryStatus(),
            plugged = rawPlugged.toBatteryPlugged(),
            temperatureCelsius = DeviceDiagnosticsMath.temperatureCelsius(rawTemperature),
            voltageMillivolts = DeviceDiagnosticsMath.voltageMillivolts(rawVoltage)
        )
    }

    private inline fun <T> readSafely(
        event: String,
        block: () -> T?
    ): T? = try {
        block()
    } catch (error: SecurityException) {
        AppLog.warn(
            LogModule.DEVICE,
            event,
            "source=android_api; exception=SecurityException"
        )
        null
    } catch (error: RuntimeException) {
        AppLog.warn(
            LogModule.DEVICE,
            event,
            "source=android_api; exception=" + error.javaClass.simpleName
        )
        null
    }

    private fun Int.toBatteryStatus(): BatteryStatus = when (this) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        else -> BatteryStatus.UNKNOWN
    }

    private fun Int.toBatteryPlugged(): BatteryPlugged = when (this) {
        0 -> BatteryPlugged.NONE
        BatteryManager.BATTERY_PLUGGED_AC -> BatteryPlugged.AC
        BatteryManager.BATTERY_PLUGGED_USB -> BatteryPlugged.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryPlugged.WIRELESS
        -1 -> BatteryPlugged.UNKNOWN
        else -> BatteryPlugged.OTHER
    }
}

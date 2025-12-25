package com.sysmetrics.infrastructure.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.sysmetrics.domain.model.BatteryHealth
import com.sysmetrics.domain.model.BatteryMetrics
import com.sysmetrics.domain.model.BatteryStatus
import com.sysmetrics.domain.model.StorageMetrics
import com.sysmetrics.domain.model.ThermalMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Provides Android-specific system metrics.
 *
 * This class interfaces with Android system services and APIs
 * to collect battery, storage, and thermal metrics. All operations
 * are performed on appropriate dispatchers to avoid blocking.
 *
 * @property context Application context for accessing system services
 */
public class AndroidMetricsProvider(private val context: Context) {

    private val batteryManager: BatteryManager? by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    /**
     * Retrieves current battery metrics from the system.
     *
     * Uses both BatteryManager API and sticky broadcast intent
     * for comprehensive battery information.
     *
     * @return [Result] containing [BatteryMetrics] or an error
     */
    public suspend fun getBatteryMetrics(): Result<BatteryMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val levelPercent = if (scale > 0) (level * 100) / scale else 0

            val temperatureRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val temperature = temperatureRaw / 10f

            val statusRaw = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

            val healthRaw = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN
            ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

            val pluggedRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

            val chargingSpeed = batteryManager?.let { manager ->
                try {
                    val currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    if (currentNow != Int.MIN_VALUE) currentNow else null
                } catch (e: Exception) {
                    null
                }
            }

            BatteryMetrics(
                level = levelPercent.coerceIn(0, 100),
                temperature = temperature,
                status = mapBatteryStatus(statusRaw),
                health = mapBatteryHealth(healthRaw),
                plugged = pluggedRaw != 0,
                chargingSpeed = chargingSpeed
            )
        }
    }

    /**
     * Retrieves current storage metrics for internal storage.
     *
     * Uses StatFs to get storage capacity and availability.
     *
     * @return [Result] containing [StorageMetrics] or an error
     */
    public suspend fun getStorageMetrics(): Result<StorageMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            val path = Environment.getDataDirectory()
            val statFs = StatFs(path.absolutePath)

            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            val totalMB = totalBytes / (1024 * 1024)
            val freeMB = availableBytes / (1024 * 1024)
            val usedMB = usedBytes / (1024 * 1024)

            val usagePercent = if (totalMB > 0) {
                ((usedMB.toFloat() / totalMB.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            StorageMetrics(
                totalStorageMB = totalMB,
                freeStorageMB = freeMB,
                usedStorageMB = usedMB,
                usagePercent = usagePercent
            )
        }
    }

    /**
     * Retrieves current thermal metrics.
     *
     * Attempts to read thermal zone files from sysfs.
     * Returns default values if thermal information is unavailable.
     *
     * @return [Result] containing [ThermalMetrics] or an error
     */
    public suspend fun getThermalMetrics(): Result<ThermalMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            val temperatures = mutableMapOf<String, Float>()
            var cpuTemp = 0f
            var batteryTemp = 0f

            // Try to read thermal zones
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                thermalDir.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
                    try {
                        val tempFile = File(zone, "temp")
                        val typeFile = File(zone, "type")
                        
                        if (tempFile.exists()) {
                            val tempMilliCelsius = tempFile.readText().trim().toLongOrNull() ?: 0L
                            val tempCelsius = tempMilliCelsius / 1000f
                            
                            val zoneName = if (typeFile.exists()) {
                                typeFile.readText().trim()
                            } else {
                                zone.name
                            }
                            
                            temperatures[zoneName] = tempCelsius
                            
                            // Identify CPU temperature
                            if (zoneName.contains("cpu", ignoreCase = true) && cpuTemp == 0f) {
                                cpuTemp = tempCelsius
                            }
                            
                            // Identify battery temperature
                            if (zoneName.contains("battery", ignoreCase = true) && batteryTemp == 0f) {
                                batteryTemp = tempCelsius
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore and continue with other thermal zones
                    }
                }
            }

            // Fallback: try to get CPU temp from other sources
            if (cpuTemp == 0f) {
                cpuTemp = readCpuTemperatureFallback()
            }

            // Detect thermal throttling
            val thermalThrottling = cpuTemp > 80f || temperatures.values.any { it > 85f }

            ThermalMetrics(
                cpuTemperature = cpuTemp,
                batteryTemperature = batteryTemp,
                otherTemperatures = temperatures.toMap(),
                thermalThrottling = thermalThrottling
            )
        }
    }

    private fun readCpuTemperatureFallback(): Float {
        val possiblePaths = listOf(
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/class/hwmon/hwmon1/temp1_input"
        )

        for (path in possiblePaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val tempMilliCelsius = file.readText().trim().toLongOrNull() ?: 0L
                    val tempCelsius = tempMilliCelsius / 1000f
                    if (tempCelsius > 0f && tempCelsius < 150f) {
                        return tempCelsius
                    }
                }
            } catch (e: Exception) {
                // Continue to next path
            }
        }

        return 0f
    }

    private fun mapBatteryStatus(status: Int): BatteryStatus = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
        else -> BatteryStatus.UNKNOWN
    }

    private fun mapBatteryHealth(health: Int): BatteryHealth = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        else -> BatteryHealth.UNKNOWN
    }
}

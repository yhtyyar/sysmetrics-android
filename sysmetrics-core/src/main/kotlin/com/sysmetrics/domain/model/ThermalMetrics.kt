package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents thermal-related metrics for the device.
 *
 * Contains information about various temperature sensors and
 * thermal throttling state. All temperatures are in degrees Celsius.
 *
 * @property cpuTemperature CPU temperature in degrees Celsius
 * @property batteryTemperature Battery temperature in degrees Celsius
 * @property otherTemperatures Map of other temperature sensor readings (sensor name to temperature)
 * @property thermalThrottling Whether the device is currently thermal throttling
 */
@Serializable
public data class ThermalMetrics(
    val cpuTemperature: Float,
    val batteryTemperature: Float,
    val otherTemperatures: Map<String, Float> = emptyMap(),
    val thermalThrottling: Boolean = false
) {
    public companion object {
        /**
         * Creates an empty ThermalMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): ThermalMetrics = ThermalMetrics(
            cpuTemperature = 0f,
            batteryTemperature = 0f,
            otherTemperatures = emptyMap(),
            thermalThrottling = false
        )
    }
}

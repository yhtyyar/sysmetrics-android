package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents battery-related metrics for the device.
 *
 * Contains information about battery level, temperature, status,
 * health, and charging state.
 *
 * @property level Battery charge level as a percentage (0-100)
 * @property temperature Battery temperature in degrees Celsius
 * @property status Current charging status of the battery
 * @property health Current health condition of the battery
 * @property plugged Whether the device is connected to a power source
 * @property chargingSpeed Charging speed indicator, null if unavailable or not charging
 */
@Serializable
public data class BatteryMetrics(
    val level: Int,
    val temperature: Float,
    val status: BatteryStatus,
    val health: BatteryHealth,
    val plugged: Boolean,
    val chargingSpeed: Int? = null
) {
    init {
        require(level in 0..100) { "level must be between 0 and 100" }
    }

    public companion object {
        /**
         * Creates an empty BatteryMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): BatteryMetrics = BatteryMetrics(
            level = 0,
            temperature = 0f,
            status = BatteryStatus.UNKNOWN,
            health = BatteryHealth.UNKNOWN,
            plugged = false
        )
    }
}

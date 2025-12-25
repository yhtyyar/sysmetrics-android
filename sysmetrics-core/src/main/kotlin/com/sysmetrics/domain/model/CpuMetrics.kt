package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents CPU-related metrics for the system.
 *
 * Contains information about CPU usage, core counts, and frequencies.
 * All frequency values are in kilohertz (kHz).
 *
 * @property usagePercent Current CPU usage as a percentage (0-100)
 * @property physicalCores Number of physical CPU cores
 * @property logicalCores Number of logical CPU cores (including hyperthreading)
 * @property maxFrequencyKHz Maximum CPU frequency in kHz, null if unavailable
 * @property currentFrequencyKHz Current CPU frequency in kHz, null if unavailable
 * @property coreFrequencies List of current frequencies for each core in kHz, null if unavailable
 */
@Serializable
public data class CpuMetrics(
    val usagePercent: Float,
    val physicalCores: Int,
    val logicalCores: Int,
    val maxFrequencyKHz: Long? = null,
    val currentFrequencyKHz: Long? = null,
    val coreFrequencies: List<Long>? = null
) {
    init {
        require(usagePercent in 0f..100f) { "usagePercent must be between 0 and 100" }
        require(physicalCores > 0) { "physicalCores must be positive" }
        require(logicalCores > 0) { "logicalCores must be positive" }
    }

    public companion object {
        /**
         * Creates an empty CpuMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): CpuMetrics = CpuMetrics(
            usagePercent = 0f,
            physicalCores = 1,
            logicalCores = 1
        )
    }
}

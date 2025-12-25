package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents memory-related metrics for the system.
 *
 * Contains information about RAM usage including total, used, free,
 * and available memory. All memory values are in megabytes (MB).
 *
 * @property totalMemoryMB Total physical memory in MB
 * @property usedMemoryMB Currently used memory in MB
 * @property freeMemoryMB Free memory in MB (not including buffers/cache)
 * @property availableMemoryMB Available memory for new allocations in MB
 * @property usagePercent Memory usage as a percentage (0-100)
 * @property buffersMB Memory used for buffers in MB, null if unavailable
 * @property cachedMB Memory used for cache in MB, null if unavailable
 * @property swapTotalMB Total swap space in MB, null if unavailable
 * @property swapFreeMB Free swap space in MB, null if unavailable
 */
@Serializable
public data class MemoryMetrics(
    val totalMemoryMB: Long,
    val usedMemoryMB: Long,
    val freeMemoryMB: Long,
    val availableMemoryMB: Long,
    val usagePercent: Float,
    val buffersMB: Long? = null,
    val cachedMB: Long? = null,
    val swapTotalMB: Long? = null,
    val swapFreeMB: Long? = null
) {
    init {
        require(totalMemoryMB >= 0) { "totalMemoryMB must be non-negative" }
        require(usedMemoryMB >= 0) { "usedMemoryMB must be non-negative" }
        require(freeMemoryMB >= 0) { "freeMemoryMB must be non-negative" }
        require(availableMemoryMB >= 0) { "availableMemoryMB must be non-negative" }
        require(usagePercent in 0f..100f) { "usagePercent must be between 0 and 100" }
    }

    public companion object {
        /**
         * Creates an empty MemoryMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): MemoryMetrics = MemoryMetrics(
            totalMemoryMB = 0,
            usedMemoryMB = 0,
            freeMemoryMB = 0,
            availableMemoryMB = 0,
            usagePercent = 0f
        )
    }
}

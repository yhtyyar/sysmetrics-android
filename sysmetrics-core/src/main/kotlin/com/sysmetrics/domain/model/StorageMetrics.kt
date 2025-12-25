package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents storage-related metrics for the device.
 *
 * Contains information about internal storage usage.
 * All storage values are in megabytes (MB).
 *
 * @property totalStorageMB Total internal storage capacity in MB
 * @property freeStorageMB Free internal storage space in MB
 * @property usedStorageMB Used internal storage space in MB
 * @property usagePercent Storage usage as a percentage (0-100)
 */
@Serializable
public data class StorageMetrics(
    val totalStorageMB: Long,
    val freeStorageMB: Long,
    val usedStorageMB: Long,
    val usagePercent: Float
) {
    init {
        require(totalStorageMB >= 0) { "totalStorageMB must be non-negative" }
        require(freeStorageMB >= 0) { "freeStorageMB must be non-negative" }
        require(usedStorageMB >= 0) { "usedStorageMB must be non-negative" }
        require(usagePercent in 0f..100f) { "usagePercent must be between 0 and 100" }
    }

    public companion object {
        /**
         * Creates an empty StorageMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): StorageMetrics = StorageMetrics(
            totalStorageMB = 0,
            freeStorageMB = 0,
            usedStorageMB = 0,
            usagePercent = 0f
        )
    }
}

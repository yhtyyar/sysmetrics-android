package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents resource consumption metrics for a specific application.
 *
 * Unlike system-wide metrics, these are scoped to the host application
 * where the library is integrated, providing granular insight into
 * app-specific resource usage.
 *
 * ## Usage
 *
 * ```kotlin
 * val appMetrics = appMetricsCollector.collect()
 * println("App CPU: ${appMetrics.cpuUsagePercent}%")
 * println("App Memory: ${appMetrics.memoryUsageMb} MB")
 * ```
 *
 * @property timestamp Timestamp when metrics were captured (epoch millis)
 * @property packageName Application package name
 * @property cpuUsagePercent CPU usage by this app (0-100)
 * @property memoryUsageMb Memory used by this app in megabytes
 * @property heapUsageMb Java heap memory used in megabytes
 * @property heapMaxMb Maximum Java heap size in megabytes
 * @property nativeHeapMb Native heap memory used in megabytes
 * @property threadCount Number of active threads in the app
 * @property networkRxBytes Bytes received by this app
 * @property networkTxBytes Bytes transmitted by this app
 * @property openFileDescriptors Number of open file descriptors
 */
@Serializable
public data class AppMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val cpuUsagePercent: Float,
    val memoryUsageMb: Float,
    val heapUsageMb: Float,
    val heapMaxMb: Float,
    val nativeHeapMb: Float,
    val threadCount: Int,
    val networkRxBytes: Long = 0L,
    val networkTxBytes: Long = 0L,
    val openFileDescriptors: Int = 0
) {
    init {
        require(cpuUsagePercent >= 0f) { "cpuUsagePercent must be non-negative" }
        require(memoryUsageMb >= 0f) { "memoryUsageMb must be non-negative" }
        require(threadCount >= 0) { "threadCount must be non-negative" }
    }

    /**
     * Heap usage as percentage of max heap.
     */
    val heapUsagePercent: Float get() = if (heapMaxMb > 0) {
        (heapUsageMb / heapMaxMb) * 100f
    } else 0f

    /**
     * Returns true if heap usage is above 80% (warning threshold).
     */
    val isHeapWarning: Boolean get() = heapUsagePercent >= 80f

    /**
     * Returns true if heap usage is above 95% (critical threshold).
     */
    val isHeapCritical: Boolean get() = heapUsagePercent >= 95f

    /**
     * Total memory (heap + native) in megabytes.
     */
    val totalMemoryMb: Float get() = heapUsageMb + nativeHeapMb

    /**
     * Returns the memory status based on heap usage.
     */
    val memoryStatus: MemoryStatus get() = when {
        heapUsagePercent >= 95f -> MemoryStatus.CRITICAL
        heapUsagePercent >= 80f -> MemoryStatus.WARNING
        heapUsagePercent >= 60f -> MemoryStatus.MODERATE
        else -> MemoryStatus.HEALTHY
    }

    /**
     * Memory status levels for app-specific monitoring.
     */
    public enum class MemoryStatus {
        /** Heap < 60% - Normal operation */
        HEALTHY,
        /** Heap 60-79% - Elevated usage */
        MODERATE,
        /** Heap 80-94% - High usage, may cause GC */
        WARNING,
        /** Heap ≥ 95% - OOM risk */
        CRITICAL
    }

    public companion object {
        /**
         * Creates empty app metrics.
         */
        public fun empty(packageName: String = ""): AppMetrics = AppMetrics(
            packageName = packageName,
            cpuUsagePercent = 0f,
            memoryUsageMb = 0f,
            heapUsageMb = 0f,
            heapMaxMb = 0f,
            nativeHeapMb = 0f,
            threadCount = 0
        )
    }
}

/**
 * Combined overlay metrics containing both FPS and app-specific data.
 *
 * This is the primary data class used by FpsOverlayView for rendering.
 *
 * @property fpsMetrics Current FPS metrics
 * @property appMetrics Current app-specific metrics
 * @property systemCpuPercent System-wide CPU usage for context
 * @property systemMemoryPercent System-wide memory usage for context
 */
@Serializable
public data class OverlayMetrics(
    val fpsMetrics: FpsMetrics,
    val appMetrics: AppMetrics,
    val systemCpuPercent: Float = 0f,
    val systemMemoryPercent: Float = 0f
) {
    /**
     * Returns true if any metric is in critical state.
     */
    val hasCriticalIssue: Boolean get() =
        fpsMetrics.isCritical || appMetrics.isHeapCritical

    /**
     * Returns true if any metric is in warning state.
     */
    val hasWarning: Boolean get() =
        fpsMetrics.isWarning || appMetrics.isHeapWarning

    /**
     * Overall health score (0-100) based on all metrics.
     */
    val healthScore: Int get() {
        var score = 100

        // FPS contribution (40%)
        score -= when {
            fpsMetrics.isCritical -> 40
            fpsMetrics.isWarning -> 20
            else -> 0
        }

        // App memory contribution (30%)
        score -= when (appMetrics.memoryStatus) {
            AppMetrics.MemoryStatus.CRITICAL -> 30
            AppMetrics.MemoryStatus.WARNING -> 15
            AppMetrics.MemoryStatus.MODERATE -> 5
            AppMetrics.MemoryStatus.HEALTHY -> 0
        }

        // App CPU contribution (30%)
        score -= when {
            appMetrics.cpuUsagePercent >= 80f -> 30
            appMetrics.cpuUsagePercent >= 50f -> 15
            appMetrics.cpuUsagePercent >= 30f -> 5
            else -> 0
        }

        return score.coerceIn(0, 100)
    }

    public companion object {
        public fun empty(): OverlayMetrics = OverlayMetrics(
            fpsMetrics = FpsMetrics.empty(),
            appMetrics = AppMetrics.empty()
        )
    }
}

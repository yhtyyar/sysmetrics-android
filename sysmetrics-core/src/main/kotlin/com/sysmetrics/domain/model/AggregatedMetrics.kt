package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents aggregated system metrics over a specific time window.
 *
 * This is a Value Object (immutable, no ID) containing statistical summaries
 * of system metrics collected during a defined time interval.
 *
 * Example usage:
 * ```kotlin
 * val aggregated = repository.getAggregatedMetrics(TimeWindow.FIVE_MINUTES)
 * aggregated.onSuccess { metrics ->
 *     println("Average CPU: ${metrics.cpuPercentAverage}%")
 *     println("Samples collected: ${metrics.sampleCount}")
 * }
 * ```
 *
 * @property timeWindow The time window used for aggregation
 * @property windowStartTime Start timestamp of the aggregation window (epoch millis)
 * @property windowEndTime End timestamp of the aggregation window (epoch millis)
 * @property sampleCount Number of metric samples included in this aggregation
 * @property cpuPercentAverage Average CPU usage percentage (0-100)
 * @property memoryPercentAverage Average memory usage percentage (0-100)
 * @property batteryPercentAverage Average battery level percentage (0-100)
 * @property temperatureCelsius Last recorded CPU temperature in Celsius
 * @property healthScoreAverage Average health score (0-100)
 * @property cpuPercentMin Minimum CPU usage in the window
 * @property cpuPercentMax Maximum CPU usage in the window
 * @property memoryPercentMin Minimum memory usage in the window
 * @property memoryPercentMax Maximum memory usage in the window
 * @property networkRxBytesTotal Total bytes received during the window
 * @property networkTxBytesTotal Total bytes transmitted during the window
 */
@Serializable
public data class AggregatedMetrics(
    val timeWindow: TimeWindow,
    val windowStartTime: Long,
    val windowEndTime: Long,
    val sampleCount: Int,
    val cpuPercentAverage: Float,
    val memoryPercentAverage: Float,
    val batteryPercentAverage: Float,
    val temperatureCelsius: Float,
    val healthScoreAverage: Int,
    val cpuPercentMin: Float = cpuPercentAverage,
    val cpuPercentMax: Float = cpuPercentAverage,
    val memoryPercentMin: Float = memoryPercentAverage,
    val memoryPercentMax: Float = memoryPercentAverage,
    val networkRxBytesTotal: Long = 0L,
    val networkTxBytesTotal: Long = 0L
) {
    init {
        require(sampleCount >= 0) { "sampleCount must be non-negative" }
        require(windowStartTime <= windowEndTime) { "windowStartTime must be <= windowEndTime" }
        require(cpuPercentAverage in 0f..100f) { "cpuPercentAverage must be in range 0-100" }
        require(memoryPercentAverage in 0f..100f) { "memoryPercentAverage must be in range 0-100" }
        require(batteryPercentAverage in 0f..100f) { "batteryPercentAverage must be in range 0-100" }
        require(healthScoreAverage in 0..100) { "healthScoreAverage must be in range 0-100" }
    }

    /**
     * Returns true if this aggregation contains actual data (sampleCount > 0).
     */
    public val hasData: Boolean get() = sampleCount > 0

    /**
     * Returns the duration of this window in milliseconds.
     */
    public val windowDurationMillis: Long get() = windowEndTime - windowStartTime

    /**
     * Returns the average samples per second in this window.
     */
    public val samplesPerSecond: Float get() = if (windowDurationMillis > 0) {
        sampleCount * 1000f / windowDurationMillis
    } else 0f

    /**
     * Returns the CPU usage variance (max - min).
     */
    public val cpuVariance: Float get() = cpuPercentMax - cpuPercentMin

    /**
     * Returns the memory usage variance (max - min).
     */
    public val memoryVariance: Float get() = memoryPercentMax - memoryPercentMin

    public companion object {
        /**
         * Creates an empty AggregatedMetrics instance for cases when no data is available.
         *
         * @param timeWindow The time window for this empty aggregation
         * @param windowStartTime Start of the window (epoch millis)
         * @param windowEndTime End of the window (epoch millis)
         * @return Empty AggregatedMetrics with zero values
         */
        public fun empty(
            timeWindow: TimeWindow,
            windowStartTime: Long,
            windowEndTime: Long
        ): AggregatedMetrics = AggregatedMetrics(
            timeWindow = timeWindow,
            windowStartTime = windowStartTime,
            windowEndTime = windowEndTime,
            sampleCount = 0,
            cpuPercentAverage = 0f,
            memoryPercentAverage = 0f,
            batteryPercentAverage = 0f,
            temperatureCelsius = 0f,
            healthScoreAverage = 100,
            cpuPercentMin = 0f,
            cpuPercentMax = 0f,
            memoryPercentMin = 0f,
            memoryPercentMax = 0f,
            networkRxBytesTotal = 0L,
            networkTxBytesTotal = 0L
        )
    }
}

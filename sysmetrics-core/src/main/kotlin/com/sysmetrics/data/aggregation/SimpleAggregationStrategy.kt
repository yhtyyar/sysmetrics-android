package com.sysmetrics.data.aggregation

import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.domain.model.TimeWindow

/**
 * Default implementation of [MetricsAggregationStrategy] using simple arithmetic averaging.
 *
 * This strategy:
 * 1. Aligns time windows to clock boundaries (e.g., 5-minute windows start at :00, :05, :10, etc.)
 * 2. Filters metrics within the window boundaries
 * 3. Computes simple arithmetic averages for numeric metrics
 * 4. Captures min/max values for CPU and memory
 * 5. Uses the last sample for temperature (point-in-time value)
 * 6. Returns empty aggregation when no data is available
 *
 * Thread-safe: This class is stateless and can be safely shared across threads.
 *
 * Performance: O(n) where n is the number of metrics in the input list.
 * Optimized for typical use cases with <1000 metrics in cache.
 */
internal class SimpleAggregationStrategy : MetricsAggregationStrategy {

    override fun aggregate(
        metrics: List<SystemMetrics>,
        timeWindow: TimeWindow,
        nowMillis: Long
    ): AggregatedMetrics {
        // Calculate the previous complete window (not the current incomplete one)
        val windowStart = calculatePreviousWindowStart(nowMillis, timeWindow)
        val windowEnd = windowStart + timeWindow.durationMillis()

        return computeAggregation(metrics, timeWindow, windowStart, windowEnd)
    }

    /**
     * Aggregates metrics for a specific window defined by start and end times.
     *
     * Used internally for generating historical aggregations.
     *
     * @param metrics List of raw metrics
     * @param timeWindow The time window type
     * @param windowStart Start of the window in milliseconds
     * @param windowEnd End of the window in milliseconds
     * @return Aggregated metrics for the specified window
     */
    fun aggregateForWindow(
        metrics: List<SystemMetrics>,
        timeWindow: TimeWindow,
        windowStart: Long,
        windowEnd: Long
    ): AggregatedMetrics = computeAggregation(metrics, timeWindow, windowStart, windowEnd)

    /**
     * Core aggregation logic extracted to eliminate code duplication.
     *
     * Filters metrics by time window and computes statistical aggregations.
     *
     * @param metrics List of raw metrics to aggregate
     * @param timeWindow The time window type for the aggregation
     * @param windowStart Start timestamp of the window (inclusive)
     * @param windowEnd End timestamp of the window (exclusive)
     * @return Aggregated metrics containing averages, min/max, and network totals
     */
    private fun computeAggregation(
        metrics: List<SystemMetrics>,
        timeWindow: TimeWindow,
        windowStart: Long,
        windowEnd: Long
    ): AggregatedMetrics {
        // Filter metrics within the time window
        val filtered = filterByTimeWindow(metrics, windowStart, windowEnd)

        // Return empty aggregation if no data
        if (filtered.isEmpty()) {
            return AggregatedMetrics.empty(
                timeWindow = timeWindow,
                windowStartTime = windowStart,
                windowEndTime = windowEnd
            )
        }

        // Extract values for statistical calculations
        val cpuValues = filtered.map { it.cpuMetrics.usagePercent }
        val memoryValues = filtered.map { it.memoryMetrics.usagePercent }
        val batteryValues = filtered.map { it.batteryMetrics.level.toFloat() }
        val healthScores = filtered.map { it.getHealthScore() }

        // Sort by timestamp for network delta and last temperature
        val sortedByTime = filtered.sortedBy { it.timestamp }

        // Calculate network totals
        val (networkRxTotal, networkTxTotal) = calculateNetworkTotals(sortedByTime)

        return AggregatedMetrics(
            timeWindow = timeWindow,
            windowStartTime = windowStart,
            windowEndTime = windowEnd,
            sampleCount = filtered.size,
            cpuPercentAverage = cpuValues.safeAverage().coerceIn(0f, 100f),
            memoryPercentAverage = memoryValues.safeAverage().coerceIn(0f, 100f),
            batteryPercentAverage = batteryValues.safeAverage().coerceIn(0f, 100f),
            temperatureCelsius = sortedByTime.lastOrNull()?.thermalMetrics?.cpuTemperature ?: 0f,
            healthScoreAverage = healthScores.safeAverageInt().coerceIn(0, 100),
            cpuPercentMin = cpuValues.safeMin().coerceIn(0f, 100f),
            cpuPercentMax = cpuValues.safeMax().coerceIn(0f, 100f),
            memoryPercentMin = memoryValues.safeMin().coerceIn(0f, 100f),
            memoryPercentMax = memoryValues.safeMax().coerceIn(0f, 100f),
            networkRxBytesTotal = networkRxTotal,
            networkTxBytesTotal = networkTxTotal
        )
    }

    /**
     * Filters metrics to include only those within the specified time window.
     *
     * @param metrics List of metrics to filter
     * @param windowStart Start timestamp (inclusive)
     * @param windowEnd End timestamp (exclusive)
     * @return Filtered list of metrics within the time window
     */
    private fun filterByTimeWindow(
        metrics: List<SystemMetrics>,
        windowStart: Long,
        windowEnd: Long
    ): List<SystemMetrics> = metrics.filter { it.timestamp in windowStart until windowEnd }

    /**
     * Calculates network byte totals as the difference between first and last samples.
     *
     * Handles counter resets by returning 0 when the delta would be negative.
     *
     * @param sortedMetrics Metrics sorted by timestamp
     * @return Pair of (rxBytesTotal, txBytesTotal)
     */
    private fun calculateNetworkTotals(sortedMetrics: List<SystemMetrics>): Pair<Long, Long> {
        val firstNetwork = sortedMetrics.firstOrNull()?.networkMetrics
        val lastNetwork = sortedMetrics.lastOrNull()?.networkMetrics

        val rxTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.rxBytes - firstNetwork.rxBytes).coerceAtLeast(0L)
        } else 0L

        val txTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.txBytes - firstNetwork.txBytes).coerceAtLeast(0L)
        } else 0L

        return rxTotal to txTotal
    }

    /**
     * Extension function for safe average calculation that handles empty lists.
     */
    private fun List<Float>.safeAverage(): Float =
        if (isEmpty()) 0f else average().toFloat()

    /**
     * Extension function for safe average calculation returning Int.
     */
    private fun List<Float>.safeAverageInt(): Int =
        if (isEmpty()) 0 else average().toInt()

    /**
     * Extension function for safe minimum calculation.
     */
    private fun List<Float>.safeMin(): Float = minOrNull() ?: 0f

    /**
     * Extension function for safe maximum calculation.
     */
    private fun List<Float>.safeMax(): Float = maxOrNull() ?: 0f
}

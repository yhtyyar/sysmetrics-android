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

        // Filter metrics within the time window
        val filtered = metrics.filter { metric ->
            metric.timestamp in windowStart until windowEnd
        }

        // Return empty aggregation if no data
        if (filtered.isEmpty()) {
            return AggregatedMetrics.empty(
                timeWindow = timeWindow,
                windowStartTime = windowStart,
                windowEndTime = windowEnd
            )
        }

        // Calculate statistics
        val cpuValues = filtered.map { it.cpuMetrics.usagePercent }
        val memoryValues = filtered.map { it.memoryMetrics.usagePercent }
        val batteryValues = filtered.map { it.batteryMetrics.level.toFloat() }
        val healthScores = filtered.map { it.getHealthScore() }

        // Network totals: difference between first and last sample
        val sortedByTime = filtered.sortedBy { it.timestamp }
        val firstNetwork = sortedByTime.firstOrNull()?.networkMetrics
        val lastNetwork = sortedByTime.lastOrNull()?.networkMetrics
        
        val networkRxTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.rxBytes - firstNetwork.rxBytes).coerceAtLeast(0L)
        } else 0L
        
        val networkTxTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.txBytes - firstNetwork.txBytes).coerceAtLeast(0L)
        } else 0L

        return AggregatedMetrics(
            timeWindow = timeWindow,
            windowStartTime = windowStart,
            windowEndTime = windowEnd,
            sampleCount = filtered.size,
            cpuPercentAverage = cpuValues.average().toFloat().coerceIn(0f, 100f),
            memoryPercentAverage = memoryValues.average().toFloat().coerceIn(0f, 100f),
            batteryPercentAverage = batteryValues.average().toFloat().coerceIn(0f, 100f),
            temperatureCelsius = sortedByTime.lastOrNull()?.thermalMetrics?.cpuTemperature ?: 0f,
            healthScoreAverage = healthScores.average().toInt().coerceIn(0, 100),
            cpuPercentMin = cpuValues.minOrNull()?.coerceIn(0f, 100f) ?: 0f,
            cpuPercentMax = cpuValues.maxOrNull()?.coerceIn(0f, 100f) ?: 0f,
            memoryPercentMin = memoryValues.minOrNull()?.coerceIn(0f, 100f) ?: 0f,
            memoryPercentMax = memoryValues.maxOrNull()?.coerceIn(0f, 100f) ?: 0f,
            networkRxBytesTotal = networkRxTotal,
            networkTxBytesTotal = networkTxTotal
        )
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
    ): AggregatedMetrics {
        // Filter metrics within the time window
        val filtered = metrics.filter { metric ->
            metric.timestamp in windowStart until windowEnd
        }

        // Return empty aggregation if no data
        if (filtered.isEmpty()) {
            return AggregatedMetrics.empty(
                timeWindow = timeWindow,
                windowStartTime = windowStart,
                windowEndTime = windowEnd
            )
        }

        // Calculate statistics
        val cpuValues = filtered.map { it.cpuMetrics.usagePercent }
        val memoryValues = filtered.map { it.memoryMetrics.usagePercent }
        val batteryValues = filtered.map { it.batteryMetrics.level.toFloat() }
        val healthScores = filtered.map { it.getHealthScore() }

        // Network totals
        val sortedByTime = filtered.sortedBy { it.timestamp }
        val firstNetwork = sortedByTime.firstOrNull()?.networkMetrics
        val lastNetwork = sortedByTime.lastOrNull()?.networkMetrics
        
        val networkRxTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.rxBytes - firstNetwork.rxBytes).coerceAtLeast(0L)
        } else 0L
        
        val networkTxTotal = if (firstNetwork != null && lastNetwork != null) {
            (lastNetwork.txBytes - firstNetwork.txBytes).coerceAtLeast(0L)
        } else 0L

        return AggregatedMetrics(
            timeWindow = timeWindow,
            windowStartTime = windowStart,
            windowEndTime = windowEnd,
            sampleCount = filtered.size,
            cpuPercentAverage = cpuValues.average().toFloat().coerceIn(0f, 100f),
            memoryPercentAverage = memoryValues.average().toFloat().coerceIn(0f, 100f),
            batteryPercentAverage = batteryValues.average().toFloat().coerceIn(0f, 100f),
            temperatureCelsius = sortedByTime.lastOrNull()?.thermalMetrics?.cpuTemperature ?: 0f,
            healthScoreAverage = healthScores.average().toInt().coerceIn(0, 100),
            cpuPercentMin = cpuValues.minOrNull()?.coerceIn(0f, 100f) ?: 0f,
            cpuPercentMax = cpuValues.maxOrNull()?.coerceIn(0f, 100f) ?: 0f,
            memoryPercentMin = memoryValues.minOrNull()?.coerceIn(0f, 100f) ?: 0f,
            memoryPercentMax = memoryValues.maxOrNull()?.coerceIn(0f, 100f) ?: 0f,
            networkRxBytesTotal = networkRxTotal,
            networkTxBytesTotal = networkTxTotal
        )
    }
}

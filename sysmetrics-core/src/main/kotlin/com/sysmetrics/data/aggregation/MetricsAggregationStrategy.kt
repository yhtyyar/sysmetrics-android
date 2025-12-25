package com.sysmetrics.data.aggregation

import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.domain.model.TimeWindow

/**
 * Strategy interface for metrics aggregation algorithms.
 *
 * Allows implementing different aggregation strategies (simple average, weighted,
 * exponential moving average, etc.) without modifying the repository.
 *
 * This follows the Strategy Pattern for flexibility and testability.
 *
 * @see SimpleAggregationStrategy for the default implementation
 */
internal interface MetricsAggregationStrategy {

    /**
     * Aggregates a list of metrics into a single [AggregatedMetrics] for the specified time window.
     *
     * The implementation should:
     * 1. Calculate the time window boundaries based on [nowMillis] and [timeWindow]
     * 2. Filter metrics that fall within the window
     * 3. Compute aggregated statistics (averages, min/max, totals)
     * 4. Return empty aggregation if no metrics fall within the window
     *
     * @param metrics List of raw [SystemMetrics] to aggregate
     * @param timeWindow The time window to aggregate over
     * @param nowMillis Current time in milliseconds (for deterministic testing)
     * @return Aggregated metrics for the time window
     */
    fun aggregate(
        metrics: List<SystemMetrics>,
        timeWindow: TimeWindow,
        nowMillis: Long = System.currentTimeMillis()
    ): AggregatedMetrics

    /**
     * Calculates the start timestamp of a time window aligned to clock boundaries.
     *
     * For example, with FIVE_MINUTES at 14:32:45:
     * - Returns 14:30:00 (aligned to 5-minute boundary)
     *
     * @param nowMillis Current time in milliseconds
     * @param timeWindow The time window for alignment
     * @return Start timestamp of the aligned window in milliseconds
     */
    fun calculateWindowStart(nowMillis: Long, timeWindow: TimeWindow): Long {
        val windowDurationMillis = timeWindow.durationMillis()
        return (nowMillis / windowDurationMillis) * windowDurationMillis
    }

    /**
     * Calculates the start of the previous complete time window.
     *
     * For example, with FIVE_MINUTES at 14:32:45:
     * - Returns 14:25:00 (the last complete 5-minute window)
     *
     * @param nowMillis Current time in milliseconds
     * @param timeWindow The time window for alignment
     * @return Start timestamp of the previous complete window in milliseconds
     */
    fun calculatePreviousWindowStart(nowMillis: Long, timeWindow: TimeWindow): Long {
        val currentWindowStart = calculateWindowStart(nowMillis, timeWindow)
        return currentWindowStart - timeWindow.durationMillis()
    }
}

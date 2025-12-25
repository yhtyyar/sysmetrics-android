package com.sysmetrics.domain.repository

import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.HealthScore
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.domain.model.TimeWindow
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for accessing system metrics.
 *
 * Provides both one-shot and streaming access to system metrics data.
 * All operations are designed to be non-blocking and thread-safe.
 *
 * Implementation notes:
 * - All suspend functions return [Result] to handle errors gracefully
 * - Flow-based methods emit continuously until cancelled
 * - History is bounded to prevent memory issues
 */
public interface IMetricsRepository {

    /**
     * Initializes the repository and prepares metric collection.
     *
     * Must be called before any other repository methods.
     * Safe to call multiple times; subsequent calls are no-ops.
     *
     * @return [Result.success] if initialization succeeded, [Result.failure] otherwise
     */
    public suspend fun initialize(): Result<Unit>

    /**
     * Retrieves the current system metrics snapshot.
     *
     * Returns cached metrics if still valid (within TTL), otherwise
     * collects fresh metrics from the system.
     *
     * @return [Result] containing [SystemMetrics] or an error
     */
    public suspend fun getCurrentMetrics(): Result<SystemMetrics>

    /**
     * Observes system metrics as a continuous stream.
     *
     * Emits new metrics at the specified interval. Uses [distinctUntilChanged]
     * internally to avoid redundant emissions when values haven't changed.
     *
     * @param intervalMs Interval between emissions in milliseconds (default: 1000ms)
     * @return [Flow] of [SystemMetrics] that emits periodically
     */
    public fun observeMetrics(intervalMs: Long = 1000L): Flow<SystemMetrics>

    /**
     * Observes the system health score as a continuous stream.
     *
     * Calculates health score based on current metrics and emits
     * updates when the score or detected issues change.
     *
     * @return [Flow] of [HealthScore] that emits when health status changes
     */
    public fun observeHealthScore(): Flow<HealthScore>

    /**
     * Retrieves historical metrics from the internal buffer.
     *
     * Returns up to [count] most recent metrics snapshots.
     * History is bounded to 300 items maximum.
     *
     * @param count Maximum number of historical entries to retrieve (default: 60)
     * @return [Result] containing list of [SystemMetrics] or an error
     */
    public suspend fun getMetricsHistory(count: Int = 60): Result<List<SystemMetrics>>

    /**
     * Clears the metrics history buffer.
     *
     * Removes all stored historical metrics. Does not affect current metrics
     * or cached values.
     *
     * @return [Result.success] if cleared successfully, [Result.failure] otherwise
     */
    public suspend fun clearHistory(): Result<Unit>

    /**
     * Releases all resources held by the repository.
     *
     * Cancels any active metric collection, clears caches and history,
     * and releases system resources. The repository should not be used
     * after calling this method.
     *
     * @return [Result.success] if destroyed successfully, [Result.failure] otherwise
     */
    public suspend fun destroy(): Result<Unit>

    // ==================== Aggregation API ====================

    /**
     * Retrieves aggregated metrics for the last complete time window.
     *
     * For example, if called at 14:32 with [TimeWindow.FIVE_MINUTES],
     * returns metrics aggregated from 14:25 to 14:30 (the last complete 5-minute window).
     *
     * The aggregation includes:
     * - Average values for CPU, memory, battery usage
     * - Min/max values for CPU and memory
     * - Last recorded temperature
     * - Average health score
     * - Total network bytes transferred
     *
     * @param timeWindow The time window for aggregation
     * @return [Result.success] containing [AggregatedMetrics] or [Result.failure] with exception
     *
     * Example:
     * ```kotlin
     * repository.getAggregatedMetrics(TimeWindow.FIVE_MINUTES)
     *     .onSuccess { metrics ->
     *         println("Avg CPU: ${metrics.cpuPercentAverage}%")
     *         println("Samples: ${metrics.sampleCount}")
     *     }
     * ```
     */
    public suspend fun getAggregatedMetrics(timeWindow: TimeWindow): Result<AggregatedMetrics>

    /**
     * Retrieves historical aggregated metrics for multiple time windows.
     *
     * Returns a list of [AggregatedMetrics] for the last [count] complete time windows.
     * Useful for building charts and trend analysis.
     *
     * For example, with [TimeWindow.FIVE_MINUTES] and count=12, returns
     * aggregated metrics for the last hour (12 x 5 minutes).
     *
     * @param timeWindow The time window size for each aggregation
     * @param count Number of time windows to retrieve (default: 12)
     * @return [Result.success] containing list of [AggregatedMetrics] (oldest first) or [Result.failure]
     *
     * Example:
     * ```kotlin
     * // Get hourly chart data (12 five-minute intervals)
     * repository.getAggregatedHistory(TimeWindow.FIVE_MINUTES, count = 12)
     *     .onSuccess { history ->
     *         val cpuTrend = history.map { it.cpuPercentAverage }
     *         plotChart(cpuTrend)
     *     }
     * ```
     */
    public suspend fun getAggregatedHistory(
        timeWindow: TimeWindow,
        count: Int = 12
    ): Result<List<AggregatedMetrics>>
}

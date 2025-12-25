package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents time windows for metrics aggregation.
 *
 * Each time window defines a duration over which metrics can be aggregated
 * to provide statistical summaries like averages, min/max values.
 *
 * Example usage:
 * ```kotlin
 * val window = TimeWindow.FIVE_MINUTES
 * val duration = window.durationMillis() // 300000L (5 minutes in ms)
 * ```
 */
@Serializable
public enum class TimeWindow {
    /**
     * One minute aggregation window (60 seconds).
     * Useful for real-time monitoring dashboards.
     */
    ONE_MINUTE,

    /**
     * Five minutes aggregation window (300 seconds).
     * Good balance between granularity and data volume.
     */
    FIVE_MINUTES,

    /**
     * Thirty minutes aggregation window (1800 seconds).
     * Suitable for trend analysis over medium periods.
     */
    THIRTY_MINUTES,

    /**
     * One hour aggregation window (3600 seconds).
     * Ideal for long-term trend analysis and reports.
     */
    ONE_HOUR;

    /**
     * Returns the duration of this time window in milliseconds.
     *
     * @return Duration in milliseconds
     */
    public fun durationMillis(): Long = when (this) {
        ONE_MINUTE -> 60_000L
        FIVE_MINUTES -> 300_000L
        THIRTY_MINUTES -> 1_800_000L
        ONE_HOUR -> 3_600_000L
    }

    /**
     * Returns the duration of this time window in seconds.
     *
     * @return Duration in seconds
     */
    public fun durationSeconds(): Long = durationMillis() / 1000L

    /**
     * Returns a human-readable label for this time window.
     *
     * @return Localized label string
     */
    public fun label(): String = when (this) {
        ONE_MINUTE -> "1 min"
        FIVE_MINUTES -> "5 min"
        THIRTY_MINUTES -> "30 min"
        ONE_HOUR -> "1 hour"
    }
}

package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a snapshot of FPS (Frames Per Second) metrics.
 *
 * This is a Value Object containing real-time frame timing data
 * collected from Android's Choreographer API.
 *
 * ## Usage
 *
 * ```kotlin
 * val fpsMetrics = FpsMetrics(
 *     currentFps = 60,
 *     averageFps = 58.5f,
 *     minFps = 45,
 *     maxFps = 62,
 *     frameCount = 120
 * )
 *
 * if (fpsMetrics.isSmooth) {
 *     println("Performance is good!")
 * }
 * ```
 *
 * @property timestamp Timestamp when metrics were captured (epoch millis)
 * @property currentFps Current instantaneous FPS value
 * @property averageFps Average FPS over the sliding window
 * @property minFps Minimum FPS recorded in the window
 * @property maxFps Maximum FPS recorded in the window
 * @property frameCount Number of frames in the current window
 * @property frameTimeMs Average frame time in milliseconds
 * @property jankCount Number of janky frames (>16.67ms)
 */
@Serializable
public data class FpsMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val currentFps: Int,
    val averageFps: Float,
    val minFps: Int,
    val maxFps: Int,
    val frameCount: Int,
    val frameTimeMs: Float = if (currentFps > 0) 1000f / currentFps else 0f,
    val jankCount: Int = 0
) {
    init {
        require(currentFps >= 0) { "currentFps must be non-negative" }
        require(averageFps >= 0f) { "averageFps must be non-negative" }
        require(minFps >= 0) { "minFps must be non-negative" }
        require(maxFps >= 0) { "maxFps must be non-negative" }
        require(frameCount >= 0) { "frameCount must be non-negative" }
    }

    /**
     * Returns true if FPS is considered smooth (≥55 fps).
     */
    val isSmooth: Boolean get() = currentFps >= 55

    /**
     * Returns true if FPS indicates warning level (30-54 fps).
     */
    val isWarning: Boolean get() = currentFps in 30..54

    /**
     * Returns true if FPS is critically low (<30 fps).
     */
    val isCritical: Boolean get() = currentFps < 30

    /**
     * Returns the performance status based on current FPS.
     */
    val status: FpsStatus get() = when {
        currentFps >= 55 -> FpsStatus.SMOOTH
        currentFps >= 30 -> FpsStatus.WARNING
        else -> FpsStatus.CRITICAL
    }

    /**
     * Returns the jank percentage (0-100).
     */
    val jankPercentage: Float get() = if (frameCount > 0) {
        (jankCount.toFloat() / frameCount) * 100f
    } else 0f

    public companion object {
        /**
         * Target FPS for smooth 60Hz displays.
         */
        public const val TARGET_FPS_60: Int = 60

        /**
         * Target FPS for smooth 90Hz displays.
         */
        public const val TARGET_FPS_90: Int = 90

        /**
         * Target FPS for smooth 120Hz displays.
         */
        public const val TARGET_FPS_120: Int = 120

        /**
         * Frame time threshold for jank detection (16.67ms for 60fps).
         */
        public const val JANK_THRESHOLD_MS: Float = 16.67f

        /**
         * Creates empty FPS metrics.
         */
        public fun empty(): FpsMetrics = FpsMetrics(
            currentFps = 0,
            averageFps = 0f,
            minFps = 0,
            maxFps = 0,
            frameCount = 0
        )
    }
}

/**
 * FPS performance status levels.
 */
@Serializable
public enum class FpsStatus {
    /** FPS ≥ 55 - Excellent performance */
    SMOOTH,
    /** FPS 30-54 - Acceptable but degraded */
    WARNING,
    /** FPS < 30 - Unacceptable, needs attention */
    CRITICAL
}

/**
 * Represents a peak event in FPS monitoring.
 *
 * Peak events are significant changes in frame rate that
 * may require attention or logging.
 *
 * @see FpsMetricsCollector for event emission
 */
@Serializable
public sealed class FpsPeakEvent {
    /** Timestamp when the event occurred */
    public abstract val timestamp: Long

    /** Current FPS value when event occurred */
    public abstract val fps: Int

    /**
     * Significant FPS drop detected (≥10 fps decrease).
     *
     * @property fps Current FPS after drop
     * @property delta Amount of FPS decrease
     * @property previousFps FPS before the drop
     */
    @Serializable
    public data class FrameDrop(
        override val timestamp: Long = System.currentTimeMillis(),
        override val fps: Int,
        val delta: Int,
        val previousFps: Int
    ) : FpsPeakEvent() {
        val severity: Severity get() = when {
            delta >= 30 -> Severity.HIGH
            delta >= 20 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    /**
     * High performance achieved (≥58 fps).
     *
     * @property fps Current high FPS value
     */
    @Serializable
    public data class HighPerformance(
        override val timestamp: Long = System.currentTimeMillis(),
        override val fps: Int
    ) : FpsPeakEvent()

    /**
     * Critical jank detected (<20 fps).
     *
     * @property fps Current critically low FPS
     * @property duration How long the jank lasted in milliseconds
     */
    @Serializable
    public data class CriticalJank(
        override val timestamp: Long = System.currentTimeMillis(),
        override val fps: Int,
        val duration: Long = 0
    ) : FpsPeakEvent()

    /**
     * Severity levels for peak events.
     */
    public enum class Severity {
        LOW, MEDIUM, HIGH
    }
}

/**
 * Statistics summary for FPS data over a period.
 *
 * @property averageFps Mean FPS over the period
 * @property peakFps Maximum FPS recorded
 * @property minFps Minimum FPS recorded
 * @property p95Fps 95th percentile FPS
 * @property p99Fps 99th percentile FPS
 * @property totalFrames Total number of frames analyzed
 * @property jankFrames Number of janky frames
 * @property dropEvents Number of significant FPS drops
 * @property periodMs Duration of the analysis period
 */
@Serializable
public data class FpsStatistics(
    val averageFps: Float,
    val peakFps: Int,
    val minFps: Int,
    val p95Fps: Int,
    val p99Fps: Int,
    val totalFrames: Int,
    val jankFrames: Int,
    val dropEvents: Int,
    val periodMs: Long
) {
    /**
     * Jank rate as percentage (0-100).
     */
    val jankRate: Float get() = if (totalFrames > 0) {
        (jankFrames.toFloat() / totalFrames) * 100f
    } else 0f

    /**
     * Stability score (0-100) based on FPS variance.
     */
    val stabilityScore: Int get() {
        if (peakFps == 0) return 100
        val variance = peakFps - minFps
        val normalizedVariance = (variance.toFloat() / peakFps) * 100
        return (100 - normalizedVariance).toInt().coerceIn(0, 100)
    }

    public companion object {
        public fun empty(): FpsStatistics = FpsStatistics(
            averageFps = 0f,
            peakFps = 0,
            minFps = 0,
            p95Fps = 0,
            p99Fps = 0,
            totalFrames = 0,
            jankFrames = 0,
            dropEvents = 0,
            periodMs = 0
        )
    }
}

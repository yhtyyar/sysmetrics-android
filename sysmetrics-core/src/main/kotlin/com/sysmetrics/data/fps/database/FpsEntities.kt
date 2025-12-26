package com.sysmetrics.data.fps.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing FPS metric records.
 *
 * Each record represents a single FPS measurement at a specific timestamp.
 * Records are indexed by timestamp for efficient time-range queries.
 *
 * @property id Auto-generated primary key
 * @property timestamp Time when the measurement was taken (epoch millis)
 * @property currentFps Instantaneous FPS value
 * @property averageFps Average FPS over the sliding window
 * @property minFps Minimum FPS in the window
 * @property maxFps Maximum FPS in the window
 * @property jankCount Number of janky frames detected
 */
@Entity(
    tableName = "fps_records",
    indices = [Index(value = ["timestamp"], name = "idx_fps_timestamp")]
)
public data class FpsRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val currentFps: Int,
    val averageFps: Float,
    val minFps: Int,
    val maxFps: Int,
    val jankCount: Int = 0
)

/**
 * Room entity for storing peak events (drops, high performance, jank).
 *
 * Peak events are significant FPS changes that may require attention.
 *
 * @property id Auto-generated primary key
 * @property timestamp Time when the event occurred (epoch millis)
 * @property type Event type: "DROP", "HIGH", or "JANK"
 * @property fps FPS value when the event occurred
 * @property delta Change in FPS (for drop events)
 * @property severity Event severity: "LOW", "MEDIUM", or "HIGH"
 */
@Entity(
    tableName = "fps_peak_events",
    indices = [Index(value = ["timestamp"], name = "idx_peak_timestamp")]
)
public data class FpsPeakEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val fps: Int,
    val delta: Int = 0,
    val severity: String
)

/**
 * Room entity for storing FPS monitoring sessions.
 *
 * A session represents a continuous period of FPS monitoring,
 * typically corresponding to an app's foreground lifecycle.
 *
 * @property id Auto-generated primary key
 * @property name Optional session name for identification
 * @property startTime Session start time (epoch millis)
 * @property endTime Session end time (epoch millis), 0 if ongoing
 * @property durationMs Total session duration in milliseconds
 * @property averageFps Average FPS over the session
 * @property peakFps Maximum FPS recorded
 * @property minFps Minimum FPS recorded
 * @property totalFrames Total frames analyzed
 * @property jankFrames Number of janky frames
 * @property dropCount Number of significant FPS drops
 */
@Entity(
    tableName = "fps_sessions",
    indices = [Index(value = ["startTime"], name = "idx_session_start")]
)
public data class FpsSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val startTime: Long,
    val endTime: Long = 0,
    val durationMs: Long = 0,
    val averageFps: Float = 0f,
    val peakFps: Int = 0,
    val minFps: Int = 0,
    val totalFrames: Int = 0,
    val jankFrames: Int = 0,
    val dropCount: Int = 0
)

/**
 * Room entity for storing app-specific resource metrics.
 *
 * Stores periodic snapshots of app CPU, memory, and other resource usage.
 *
 * @property id Auto-generated primary key
 * @property timestamp Time when metrics were captured (epoch millis)
 * @property cpuUsagePercent App CPU usage percentage
 * @property memoryUsageMb Total memory used by app in MB
 * @property heapUsageMb Java heap memory in MB
 * @property nativeHeapMb Native heap memory in MB
 * @property threadCount Number of active threads
 */
@Entity(
    tableName = "app_metrics_records",
    indices = [Index(value = ["timestamp"], name = "idx_app_metrics_timestamp")]
)
public data class AppMetricsRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val cpuUsagePercent: Float,
    val memoryUsageMb: Float,
    val heapUsageMb: Float,
    val nativeHeapMb: Float,
    val threadCount: Int
)

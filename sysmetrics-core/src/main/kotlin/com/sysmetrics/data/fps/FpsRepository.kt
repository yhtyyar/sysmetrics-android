package com.sysmetrics.data.fps

import android.content.Context
import com.sysmetrics.data.fps.database.AppMetricsRecordEntity
import com.sysmetrics.data.fps.database.FpsDatabase
import com.sysmetrics.data.fps.database.FpsPeakEventEntity
import com.sysmetrics.data.fps.database.FpsRecordEntity
import com.sysmetrics.data.fps.database.FpsSessionEntity
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.AppMetrics
import com.sysmetrics.domain.model.FpsMetrics
import com.sysmetrics.domain.model.FpsPeakEvent
import com.sysmetrics.domain.model.FpsStatistics
import com.sysmetrics.infrastructure.logger.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for FPS and app metrics persistence.
 *
 * Provides a clean API for storing and querying metrics data,
 * abstracting the Room database implementation details.
 *
 * ## Features
 *
 * - Async operations (all suspend functions)
 * - Automatic cleanup of old records (configurable retention)
 * - Statistical calculations (avg, min, max, percentiles)
 * - Flow-based observation for reactive updates
 *
 * ## Usage
 *
 * ```kotlin
 * val repository = FpsRepository.getInstance(context)
 *
 * // Record FPS
 * repository.recordFps(fpsMetrics)
 *
 * // Get statistics for last 24 hours
 * val stats = repository.getStatisticsForPeriod(days = 1)
 *
 * // Cleanup old data
 * repository.cleanupOldRecords(daysToKeep = 7)
 * ```
 *
 * ## Thread Safety
 *
 * All operations run on Dispatchers.IO for non-blocking execution.
 * The repository is thread-safe and can be used from any thread.
 *
 * @property context Application context
 * @property logger Optional logger for diagnostics
 */
public class FpsRepository private constructor(
    context: Context,
    private val logger: MetricsLogger
) {
    private val database = FpsDatabase.getInstance(context)
    private val fpsRecordDao = database.fpsRecordDao()
    private val peakEventDao = database.fpsPeakEventDao()
    private val sessionDao = database.fpsSessionDao()
    private val appMetricsDao = database.appMetricsRecordDao()

    // ==================== FPS Records ====================

    /**
     * Records FPS metrics to the database.
     *
     * @param metrics FPS metrics to record
     */
    public suspend fun recordFps(metrics: FpsMetrics) {
        withContext(Dispatchers.IO) {
            try {
                fpsRecordDao.insert(
                    FpsRecordEntity(
                        timestamp = metrics.timestamp,
                        currentFps = metrics.currentFps,
                        averageFps = metrics.averageFps,
                        minFps = metrics.minFps,
                        maxFps = metrics.maxFps,
                        jankCount = metrics.jankCount
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to record FPS", e)
            }
        }
    }

    /**
     * Records multiple FPS metrics in a single transaction.
     *
     * @param metricsList List of FPS metrics to record
     */
    public suspend fun recordFpsBatch(metricsList: List<FpsMetrics>) {
        withContext(Dispatchers.IO) {
            try {
                val entities = metricsList.map { metrics ->
                    FpsRecordEntity(
                        timestamp = metrics.timestamp,
                        currentFps = metrics.currentFps,
                        averageFps = metrics.averageFps,
                        minFps = metrics.minFps,
                        maxFps = metrics.maxFps,
                        jankCount = metrics.jankCount
                    )
                }
                fpsRecordDao.insertAll(entities)
            } catch (e: Exception) {
                logger.error(TAG, "Failed to record FPS batch", e)
            }
        }
    }

    /**
     * Gets FPS records for a time period.
     *
     * @param startTime Start of period (epoch millis)
     * @param endTime End of period (epoch millis)
     * @return List of FPS metrics
     */
    public suspend fun getFpsRecords(startTime: Long, endTime: Long): List<FpsMetrics> {
        return withContext(Dispatchers.IO) {
            try {
                fpsRecordDao.getRecordsBetween(startTime, endTime).map { entity ->
                    FpsMetrics(
                        timestamp = entity.timestamp,
                        currentFps = entity.currentFps,
                        averageFps = entity.averageFps,
                        minFps = entity.minFps,
                        maxFps = entity.maxFps,
                        frameCount = 1,
                        jankCount = entity.jankCount
                    )
                }
            } catch (e: Exception) {
                logger.error(TAG, "Failed to get FPS records", e)
                emptyList()
            }
        }
    }

    /**
     * Gets the most recent FPS records.
     *
     * @param limit Maximum number of records to return
     * @return List of FPS metrics
     */
    public suspend fun getRecentFpsRecords(limit: Int = 100): List<FpsMetrics> {
        return withContext(Dispatchers.IO) {
            try {
                fpsRecordDao.getRecentRecords(limit).map { entity ->
                    FpsMetrics(
                        timestamp = entity.timestamp,
                        currentFps = entity.currentFps,
                        averageFps = entity.averageFps,
                        minFps = entity.minFps,
                        maxFps = entity.maxFps,
                        frameCount = 1,
                        jankCount = entity.jankCount
                    )
                }
            } catch (e: Exception) {
                logger.error(TAG, "Failed to get recent FPS records", e)
                emptyList()
            }
        }
    }

    // ==================== Peak Events ====================

    /**
     * Records a peak event.
     *
     * @param event Peak event to record
     */
    public suspend fun recordPeakEvent(event: FpsPeakEvent) {
        withContext(Dispatchers.IO) {
            try {
                val (type, severity, delta) = when (event) {
                    is FpsPeakEvent.FrameDrop -> Triple("DROP", event.severity.name, event.delta)
                    is FpsPeakEvent.HighPerformance -> Triple("HIGH", "LOW", 0)
                    is FpsPeakEvent.CriticalJank -> Triple("JANK", "HIGH", 0)
                }

                peakEventDao.insert(
                    FpsPeakEventEntity(
                        timestamp = event.timestamp,
                        type = type,
                        fps = event.fps,
                        delta = delta,
                        severity = severity
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to record peak event", e)
            }
        }
    }

    /**
     * Gets peak events for a time period.
     *
     * @param startTime Start of period (epoch millis)
     * @param endTime End of period (epoch millis)
     * @return List of peak event entities
     */
    public suspend fun getPeakEvents(
        startTime: Long,
        endTime: Long
    ): List<FpsPeakEventEntity> {
        return withContext(Dispatchers.IO) {
            try {
                peakEventDao.getEventsBetween(startTime, endTime)
            } catch (e: Exception) {
                logger.error(TAG, "Failed to get peak events", e)
                emptyList()
            }
        }
    }

    // ==================== Sessions ====================

    /**
     * Starts a new monitoring session.
     *
     * @param name Optional session name
     * @return Session ID
     */
    public suspend fun startSession(name: String = ""): Long {
        return withContext(Dispatchers.IO) {
            try {
                sessionDao.insert(
                    FpsSessionEntity(
                        name = name,
                        startTime = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to start session", e)
                -1L
            }
        }
    }

    /**
     * Ends a monitoring session with final statistics.
     *
     * @param sessionId Session ID to end
     * @param stats Final session statistics
     */
    public suspend fun endSession(sessionId: Long, stats: FpsStatistics) {
        withContext(Dispatchers.IO) {
            try {
                val session = sessionDao.getSession(sessionId) ?: return@withContext
                val endTime = System.currentTimeMillis()

                sessionDao.update(
                    session.copy(
                        endTime = endTime,
                        durationMs = endTime - session.startTime,
                        averageFps = stats.averageFps,
                        peakFps = stats.peakFps,
                        minFps = stats.minFps,
                        totalFrames = stats.totalFrames,
                        jankFrames = stats.jankFrames,
                        dropCount = stats.dropEvents
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to end session", e)
            }
        }
    }

    /**
     * Gets recent sessions.
     *
     * @param limit Maximum number of sessions
     * @return List of session entities
     */
    public suspend fun getRecentSessions(limit: Int = 10): List<FpsSessionEntity> {
        return withContext(Dispatchers.IO) {
            try {
                sessionDao.getRecentSessions(limit)
            } catch (e: Exception) {
                logger.error(TAG, "Failed to get recent sessions", e)
                emptyList()
            }
        }
    }

    // ==================== App Metrics ====================

    /**
     * Records app metrics to the database.
     *
     * @param metrics App metrics to record
     */
    public suspend fun recordAppMetrics(metrics: AppMetrics) {
        withContext(Dispatchers.IO) {
            try {
                appMetricsDao.insert(
                    AppMetricsRecordEntity(
                        timestamp = metrics.timestamp,
                        cpuUsagePercent = metrics.cpuUsagePercent,
                        memoryUsageMb = metrics.memoryUsageMb,
                        heapUsageMb = metrics.heapUsageMb,
                        nativeHeapMb = metrics.nativeHeapMb,
                        threadCount = metrics.threadCount
                    )
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to record app metrics", e)
            }
        }
    }

    // ==================== Statistics ====================

    /**
     * Gets FPS statistics for a time period.
     *
     * @param days Number of days to analyze (from now)
     * @return FPS statistics
     */
    public suspend fun getStatisticsForPeriod(days: Int = 1): FpsStatistics {
        return withContext(Dispatchers.IO) {
            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (days * 24L * 60 * 60 * 1000)

                val avgFps = fpsRecordDao.getAverageFps(startTime, endTime) ?: 0f
                val peakFps = fpsRecordDao.getPeakFps(startTime, endTime) ?: 0
                val minFps = fpsRecordDao.getMinFps(startTime, endTime) ?: 0
                val totalFrames = fpsRecordDao.getRecordCount(startTime, endTime)
                val jankFrames = fpsRecordDao.getTotalJankCount(startTime, endTime) ?: 0
                val dropEvents = peakEventDao.getEventCountByType("DROP", startTime, endTime)

                // Calculate percentiles
                val records = fpsRecordDao.getRecordsBetween(startTime, endTime)
                val sortedFps = records.map { it.currentFps }.sorted()

                val p95 = if (sortedFps.isNotEmpty()) {
                    sortedFps.getOrElse((sortedFps.size * 0.95).toInt()) { sortedFps.last() }
                } else 0

                val p99 = if (sortedFps.isNotEmpty()) {
                    sortedFps.getOrElse((sortedFps.size * 0.99).toInt()) { sortedFps.last() }
                } else 0

                FpsStatistics(
                    averageFps = avgFps,
                    peakFps = peakFps,
                    minFps = minFps,
                    p95Fps = p95,
                    p99Fps = p99,
                    totalFrames = totalFrames,
                    jankFrames = jankFrames,
                    dropEvents = dropEvents,
                    periodMs = endTime - startTime
                )
            } catch (e: Exception) {
                logger.error(TAG, "Failed to calculate statistics", e)
                FpsStatistics.empty()
            }
        }
    }

    // ==================== Cleanup ====================

    /**
     * Deletes records older than the specified number of days.
     *
     * @param daysToKeep Number of days to retain (default: 7)
     * @return Total number of deleted records
     */
    public suspend fun cleanupOldRecords(daysToKeep: Int = 7): Int {
        return withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000)

                val fpsDeleted = fpsRecordDao.deleteOlderThan(cutoffTime)
                val eventsDeleted = peakEventDao.deleteOlderThan(cutoffTime)
                val sessionsDeleted = sessionDao.deleteOlderThan(cutoffTime)
                val appMetricsDeleted = appMetricsDao.deleteOlderThan(cutoffTime)

                val total = fpsDeleted + eventsDeleted + sessionsDeleted + appMetricsDeleted
                logger.info(TAG, "Cleanup completed: $total records deleted")
                total
            } catch (e: Exception) {
                logger.error(TAG, "Failed to cleanup old records", e)
                0
            }
        }
    }

    /**
     * Deletes all data from all tables.
     */
    public suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            try {
                fpsRecordDao.deleteAll()
                peakEventDao.deleteAll()
                sessionDao.deleteAll()
                appMetricsDao.deleteAll()
                logger.info(TAG, "All data cleared")
            } catch (e: Exception) {
                logger.error(TAG, "Failed to clear all data", e)
            }
        }
    }

    // ==================== Observables ====================

    /**
     * Observes recent FPS records as a Flow.
     *
     * @param limit Maximum number of records
     * @return Flow of FPS metrics lists
     */
    public fun observeRecentFpsRecords(limit: Int = 100): Flow<List<FpsMetrics>> {
        return fpsRecordDao.observeRecentRecords(limit).map { entities ->
            entities.map { entity ->
                FpsMetrics(
                    timestamp = entity.timestamp,
                    currentFps = entity.currentFps,
                    averageFps = entity.averageFps,
                    minFps = entity.minFps,
                    maxFps = entity.maxFps,
                    frameCount = 1,
                    jankCount = entity.jankCount
                )
            }
        }
    }

    public companion object {
        private const val TAG = "FpsRepository"

        @Volatile
        private var INSTANCE: FpsRepository? = null

        /**
         * Returns the singleton repository instance.
         *
         * @param context Application context
         * @param logger Optional logger
         * @return Repository instance
         */
        public fun getInstance(
            context: Context,
            logger: MetricsLogger = NoOpLogger
        ): FpsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FpsRepository(context.applicationContext, logger).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Clears the singleton instance (for testing).
         */
        internal fun clearInstance() {
            INSTANCE = null
        }
    }
}

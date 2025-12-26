package com.sysmetrics.data.fps.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FPS record operations.
 *
 * All operations are suspend functions for async execution.
 * Queries use proper indexing for performance (<50ms).
 */
@Dao
public interface FpsRecordDao {

    /**
     * Inserts a single FPS record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(record: FpsRecordEntity)

    /**
     * Inserts multiple FPS records in a transaction.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(records: List<FpsRecordEntity>)

    /**
     * Gets all records within a time range.
     */
    @Query("SELECT * FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    public suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<FpsRecordEntity>

    /**
     * Gets the most recent N records.
     */
    @Query("SELECT * FROM fps_records ORDER BY timestamp DESC LIMIT :limit")
    public suspend fun getRecentRecords(limit: Int): List<FpsRecordEntity>

    /**
     * Gets average FPS for a time period.
     */
    @Query("SELECT AVG(currentFps) FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getAverageFps(startTime: Long, endTime: Long): Float?

    /**
     * Gets peak (max) FPS for a time period.
     */
    @Query("SELECT MAX(currentFps) FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getPeakFps(startTime: Long, endTime: Long): Int?

    /**
     * Gets minimum FPS for a time period.
     */
    @Query("SELECT MIN(currentFps) FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getMinFps(startTime: Long, endTime: Long): Int?

    /**
     * Gets total frame count for a time period.
     */
    @Query("SELECT COUNT(*) FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getRecordCount(startTime: Long, endTime: Long): Int

    /**
     * Gets total jank count for a time period.
     */
    @Query("SELECT SUM(jankCount) FROM fps_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getTotalJankCount(startTime: Long, endTime: Long): Int?

    /**
     * Deletes records older than the specified timestamp.
     */
    @Query("DELETE FROM fps_records WHERE timestamp < :cutoffTime")
    public suspend fun deleteOlderThan(cutoffTime: Long): Int

    /**
     * Deletes all records.
     */
    @Query("DELETE FROM fps_records")
    public suspend fun deleteAll()

    /**
     * Observes recent records as a Flow.
     */
    @Query("SELECT * FROM fps_records ORDER BY timestamp DESC LIMIT :limit")
    public fun observeRecentRecords(limit: Int): Flow<List<FpsRecordEntity>>
}

/**
 * Data Access Object for peak event operations.
 */
@Dao
public interface FpsPeakEventDao {

    /**
     * Inserts a single peak event.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(event: FpsPeakEventEntity)

    /**
     * Gets all events within a time range.
     */
    @Query("SELECT * FROM fps_peak_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    public suspend fun getEventsBetween(startTime: Long, endTime: Long): List<FpsPeakEventEntity>

    /**
     * Gets events of a specific type.
     */
    @Query("SELECT * FROM fps_peak_events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    public suspend fun getEventsByType(type: String, limit: Int): List<FpsPeakEventEntity>

    /**
     * Gets event count by type for a time period.
     */
    @Query("SELECT COUNT(*) FROM fps_peak_events WHERE type = :type AND timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getEventCountByType(type: String, startTime: Long, endTime: Long): Int

    /**
     * Gets total event count for a time period.
     */
    @Query("SELECT COUNT(*) FROM fps_peak_events WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getTotalEventCount(startTime: Long, endTime: Long): Int

    /**
     * Deletes events older than the specified timestamp.
     */
    @Query("DELETE FROM fps_peak_events WHERE timestamp < :cutoffTime")
    public suspend fun deleteOlderThan(cutoffTime: Long): Int

    /**
     * Deletes all events.
     */
    @Query("DELETE FROM fps_peak_events")
    public suspend fun deleteAll()

    /**
     * Observes recent events as a Flow.
     */
    @Query("SELECT * FROM fps_peak_events ORDER BY timestamp DESC LIMIT :limit")
    public fun observeRecentEvents(limit: Int): Flow<List<FpsPeakEventEntity>>
}

/**
 * Data Access Object for session operations.
 */
@Dao
public interface FpsSessionDao {

    /**
     * Inserts a new session.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(session: FpsSessionEntity): Long

    /**
     * Updates an existing session.
     */
    @Update
    public suspend fun update(session: FpsSessionEntity)

    /**
     * Gets a session by ID.
     */
    @Query("SELECT * FROM fps_sessions WHERE id = :sessionId")
    public suspend fun getSession(sessionId: Long): FpsSessionEntity?

    /**
     * Gets all sessions within a time range.
     */
    @Query("SELECT * FROM fps_sessions WHERE startTime BETWEEN :startTime AND :endTime ORDER BY startTime DESC")
    public suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<FpsSessionEntity>

    /**
     * Gets the most recent N sessions.
     */
    @Query("SELECT * FROM fps_sessions ORDER BY startTime DESC LIMIT :limit")
    public suspend fun getRecentSessions(limit: Int): List<FpsSessionEntity>

    /**
     * Deletes sessions older than the specified timestamp.
     */
    @Query("DELETE FROM fps_sessions WHERE startTime < :cutoffTime")
    public suspend fun deleteOlderThan(cutoffTime: Long): Int

    /**
     * Deletes all sessions.
     */
    @Query("DELETE FROM fps_sessions")
    public suspend fun deleteAll()
}

/**
 * Data Access Object for app metrics operations.
 */
@Dao
public interface AppMetricsRecordDao {

    /**
     * Inserts a single app metrics record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(record: AppMetricsRecordEntity)

    /**
     * Gets all records within a time range.
     */
    @Query("SELECT * FROM app_metrics_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    public suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<AppMetricsRecordEntity>

    /**
     * Gets the most recent N records.
     */
    @Query("SELECT * FROM app_metrics_records ORDER BY timestamp DESC LIMIT :limit")
    public suspend fun getRecentRecords(limit: Int): List<AppMetricsRecordEntity>

    /**
     * Gets average CPU usage for a time period.
     */
    @Query("SELECT AVG(cpuUsagePercent) FROM app_metrics_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getAverageCpuUsage(startTime: Long, endTime: Long): Float?

    /**
     * Gets average memory usage for a time period.
     */
    @Query("SELECT AVG(memoryUsageMb) FROM app_metrics_records WHERE timestamp BETWEEN :startTime AND :endTime")
    public suspend fun getAverageMemoryUsage(startTime: Long, endTime: Long): Float?

    /**
     * Deletes records older than the specified timestamp.
     */
    @Query("DELETE FROM app_metrics_records WHERE timestamp < :cutoffTime")
    public suspend fun deleteOlderThan(cutoffTime: Long): Int

    /**
     * Deletes all records.
     */
    @Query("DELETE FROM app_metrics_records")
    public suspend fun deleteAll()
}

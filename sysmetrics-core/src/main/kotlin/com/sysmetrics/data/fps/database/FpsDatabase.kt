package com.sysmetrics.data.fps.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for FPS and app metrics storage.
 *
 * This database stores:
 * - FPS metric records
 * - Peak events (drops, high performance, jank)
 * - Monitoring sessions
 * - App-specific resource metrics
 *
 * ## Usage
 *
 * ```kotlin
 * val database = FpsDatabase.getInstance(context)
 * val fpsDao = database.fpsRecordDao()
 *
 * // Insert record
 * fpsDao.insert(FpsRecordEntity(...))
 *
 * // Query records
 * val records = fpsDao.getRecordsBetween(startTime, endTime)
 * ```
 *
 * ## Migration
 *
 * When schema changes, increment [DATABASE_VERSION] and add
 * appropriate migration in the companion object.
 *
 * @see FpsRecordDao for FPS record operations
 * @see FpsPeakEventDao for peak event operations
 * @see FpsSessionDao for session operations
 * @see AppMetricsRecordDao for app metrics operations
 */
@Database(
    entities = [
        FpsRecordEntity::class,
        FpsPeakEventEntity::class,
        FpsSessionEntity::class,
        AppMetricsRecordEntity::class
    ],
    version = 1,
    exportSchema = true
)
public abstract class FpsDatabase : RoomDatabase() {

    /**
     * Returns the DAO for FPS record operations.
     */
    public abstract fun fpsRecordDao(): FpsRecordDao

    /**
     * Returns the DAO for peak event operations.
     */
    public abstract fun fpsPeakEventDao(): FpsPeakEventDao

    /**
     * Returns the DAO for session operations.
     */
    public abstract fun fpsSessionDao(): FpsSessionDao

    /**
     * Returns the DAO for app metrics operations.
     */
    public abstract fun appMetricsRecordDao(): AppMetricsRecordDao

    public companion object {
        private const val DATABASE_NAME = "sysmetrics_fps_database"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: FpsDatabase? = null

        /**
         * Returns the singleton database instance.
         *
         * Thread-safe via double-checked locking.
         *
         * @param context Application context
         * @return Database instance
         */
        public fun getInstance(context: Context): FpsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): FpsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FpsDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Clears the singleton instance (for testing).
         */
        internal fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

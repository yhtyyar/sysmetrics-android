package com.sysmetrics.data.cache

import com.sysmetrics.domain.model.SystemMetrics
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe cache for system metrics with TTL-based expiration.
 *
 * Provides temporary storage for metrics to reduce redundant collection
 * operations. The cache automatically expires entries after the configured
 * TTL (time-to-live) period.
 *
 * @property ttlMs Time-to-live in milliseconds (default: 500ms)
 */
public class MetricsCache(private val ttlMs: Long = DEFAULT_TTL_MS) {

    private val mutex = Mutex()
    
    @Volatile
    private var cachedMetrics: SystemMetrics? = null
    
    @Volatile
    private var cacheTimestamp: Long = 0L

    /**
     * Retrieves cached metrics if still valid (within TTL).
     *
     * Thread-safe: Uses mutex to ensure consistent reads.
     *
     * @return [SystemMetrics] if cache is valid, null otherwise
     */
    public suspend fun getIfValid(): SystemMetrics? = mutex.withLock {
        val cached = cachedMetrics
        val timestamp = cacheTimestamp
        val now = System.currentTimeMillis()
        
        if (cached != null && (now - timestamp) < ttlMs) {
            cached
        } else {
            null
        }
    }

    /**
     * Stores metrics in the cache with current timestamp.
     *
     * Thread-safe: Uses mutex to ensure consistent writes.
     *
     * @param metrics The [SystemMetrics] to cache
     */
    public suspend fun put(metrics: SystemMetrics): Unit = mutex.withLock {
        cachedMetrics = metrics
        cacheTimestamp = System.currentTimeMillis()
    }

    /**
     * Clears all cached data.
     *
     * Thread-safe: Uses mutex to ensure consistent state.
     */
    public suspend fun clear(): Unit = mutex.withLock {
        cachedMetrics = null
        cacheTimestamp = 0L
    }

    /**
     * Checks if the cache contains valid (non-expired) data.
     *
     * @return true if cache contains valid data, false otherwise
     */
    public suspend fun isValid(): Boolean = mutex.withLock {
        val cached = cachedMetrics
        val timestamp = cacheTimestamp
        val now = System.currentTimeMillis()
        
        cached != null && (now - timestamp) < ttlMs
    }

    /**
     * Returns the age of the cached data in milliseconds.
     *
     * @return Age in milliseconds, or -1 if no cached data exists
     */
    public suspend fun getCacheAge(): Long = mutex.withLock {
        if (cachedMetrics != null) {
            System.currentTimeMillis() - cacheTimestamp
        } else {
            -1L
        }
    }

    public companion object {
        /** Default TTL of 500ms - proven optimal for most use cases */
        public const val DEFAULT_TTL_MS: Long = 500L
    }
}

package com.sysmetrics.data.repository

import com.sysmetrics.data.aggregation.MetricsAggregationStrategy
import com.sysmetrics.data.aggregation.SimpleAggregationStrategy
import com.sysmetrics.data.cache.MetricsCache
import com.sysmetrics.data.mapper.MetricsMapper
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.HealthScore
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.domain.model.TimeWindow
import com.sysmetrics.domain.repository.IMetricsRepository
import com.sysmetrics.infrastructure.android.AndroidMetricsProvider
import com.sysmetrics.infrastructure.android.NetworkMetricsProvider
import com.sysmetrics.infrastructure.logger.NoOpLogger
import com.sysmetrics.infrastructure.proc.ProcFileReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * Implementation of [IMetricsRepository] that collects system metrics.
 *
 * Combines data from [ProcFileReader] for CPU/memory metrics and
 * [AndroidMetricsProvider] for battery/storage/thermal metrics.
 * Uses [MetricsCache] for TTL-based caching to reduce collection overhead.
 *
 * Thread-safe: All operations use appropriate synchronization.
 *
 * @property procFileReader Reader for /proc filesystem metrics
 * @property androidProvider Provider for Android-specific metrics
 * @property cache TTL-based metrics cache
 */
internal class MetricsRepositoryImpl(
    private val procFileReader: ProcFileReader,
    private val androidProvider: AndroidMetricsProvider,
    private val networkProvider: NetworkMetricsProvider,
    private val cache: MetricsCache,
    private val aggregationStrategy: MetricsAggregationStrategy = SimpleAggregationStrategy(),
    private val logger: MetricsLogger = NoOpLogger
) : IMetricsRepository {

    private val mutex = Mutex()
    private val history = ArrayDeque<SystemMetrics>(MAX_HISTORY_SIZE)
    
    @Volatile
    private var isInitialized = false

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                if (!isInitialized) {
                    // Reset proc file reader state
                    procFileReader.reset()
                    networkProvider.reset()
                    
                    // Clear any stale cache
                    cache.clear()
                    
                    // Perform initial metrics read to establish baseline
                    collectMetricsInternal()
                    
                    isInitialized = true
                }
            }
        }
    }

    override suspend fun getCurrentMetrics(): Result<SystemMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            // Check cache first
            cache.getIfValid()?.let { return@runCatching it }
            
            // Collect fresh metrics
            val metrics = collectMetricsInternal()
            
            // Update cache
            cache.put(metrics)
            
            // Add to history
            addToHistory(metrics)
            
            metrics
        }
    }

    override fun observeMetrics(intervalMs: Long): Flow<SystemMetrics> = flow {
        val effectiveInterval = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
        
        while (true) {
            val result = getCurrentMetrics()
            result.onSuccess { metrics ->
                emit(metrics)
            }
            delay(effectiveInterval)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun observeHealthScore(): Flow<HealthScore> = flow {
        while (true) {
            val result = getCurrentMetrics()
            result.onSuccess { metrics ->
                val healthScore = MetricsMapper.toHealthScore(metrics)
                emit(healthScore)
            }
            delay(HEALTH_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged { old, new ->
        // Only emit if score changed significantly or issues changed
        kotlin.math.abs(old.score - new.score) < 1f && old.issues == new.issues
    }.flowOn(Dispatchers.IO)

    override suspend fun getMetricsHistory(count: Int): Result<List<SystemMetrics>> = 
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val requestedCount = count.coerceIn(1, MAX_HISTORY_SIZE)
                    history.toList().takeLast(requestedCount)
                }
            }
        }

    override suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                history.clear()
            }
        }
    }

    override suspend fun destroy(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                cache.clear()
                history.clear()
                procFileReader.reset()
                networkProvider.reset()
                isInitialized = false
            }
        }
    }

    private suspend fun collectMetricsInternal(): SystemMetrics {
        // Collect all metrics in parallel conceptually, but sequentially for simplicity
        val cpuResult = procFileReader.readCpuMetrics()
        val memoryResult = procFileReader.readMemoryMetrics()
        val uptimeResult = procFileReader.readUptime()
        val batteryResult = androidProvider.getBatteryMetrics()
        val storageResult = androidProvider.getStorageMetrics()
        val thermalResult = androidProvider.getThermalMetrics()
        val networkResult = networkProvider.getNetworkMetrics()

        return SystemMetrics(
            cpuMetrics = cpuResult.getOrElse { 
                com.sysmetrics.domain.model.CpuMetrics.empty() 
            },
            memoryMetrics = memoryResult.getOrElse { 
                com.sysmetrics.domain.model.MemoryMetrics.empty() 
            },
            batteryMetrics = batteryResult.getOrElse { 
                com.sysmetrics.domain.model.BatteryMetrics.empty() 
            },
            thermalMetrics = thermalResult.getOrElse { 
                com.sysmetrics.domain.model.ThermalMetrics.empty() 
            },
            storageMetrics = storageResult.getOrElse { 
                com.sysmetrics.domain.model.StorageMetrics.empty() 
            },
            networkMetrics = networkResult.getOrElse {
                com.sysmetrics.domain.model.NetworkMetrics.empty()
            },
            timestamp = System.currentTimeMillis(),
            uptime = uptimeResult.getOrElse { 0L }
        )
    }

    private suspend fun addToHistory(metrics: SystemMetrics) {
        mutex.withLock {
            if (history.size >= MAX_HISTORY_SIZE) {
                history.pollFirst()
            }
            history.addLast(metrics)
        }
    }

    // ==================== Aggregation API Implementation ====================

    override suspend fun getAggregatedMetrics(timeWindow: TimeWindow): Result<AggregatedMetrics> =
        withContext(Dispatchers.IO) {
            logger.info(TAG, "Aggregating metrics for $timeWindow")
            val startTime = System.currentTimeMillis()
            
            safeCall("getAggregatedMetrics") {
                val metrics = mutex.withLock { history.toList() }
                val aggregated = aggregationStrategy.aggregate(metrics, timeWindow)
                
                val duration = System.currentTimeMillis() - startTime
                if (logger.isDebugEnabled()) {
                    logger.debug(TAG, "Aggregation completed: ${metrics.size} samples, ${duration}ms")
                }
                
                // Log warning for high resource usage
                if (aggregated.cpuPercentAverage > HIGH_CPU_THRESHOLD) {
                    logger.warn(TAG, "High CPU usage detected: ${aggregated.cpuPercentAverage}%")
                }
                if (aggregated.memoryPercentAverage > HIGH_MEMORY_THRESHOLD) {
                    logger.warn(TAG, "High memory usage detected: ${aggregated.memoryPercentAverage}%")
                }
                
                aggregated
            }
        }

    override suspend fun getAggregatedHistory(
        timeWindow: TimeWindow,
        count: Int
    ): Result<List<AggregatedMetrics>> = withContext(Dispatchers.IO) {
        logger.info(TAG, "Getting aggregated history: $timeWindow, count=$count")
        val startTime = System.currentTimeMillis()
        
        safeCall("getAggregatedHistory") {
            val metrics = mutex.withLock { history.toList() }
            val nowMillis = System.currentTimeMillis()
            val strategy = aggregationStrategy as? SimpleAggregationStrategy
                ?: SimpleAggregationStrategy()
            
            val windowDuration = timeWindow.durationMillis()
            val results = mutableListOf<AggregatedMetrics>()
            
            // Generate aggregations for 'count' previous complete windows
            for (i in count downTo 1) {
                val windowEnd = strategy.calculateWindowStart(nowMillis, timeWindow) - 
                    ((i - 1) * windowDuration)
                val windowStart = windowEnd - windowDuration
                
                results.add(
                    strategy.aggregateForWindow(metrics, timeWindow, windowStart, windowEnd)
                )
            }
            
            val duration = System.currentTimeMillis() - startTime
            if (logger.isDebugEnabled()) {
                logger.debug(TAG, "History aggregation completed: ${results.size} windows, ${duration}ms")
            }
            
            results
        }
    }

    /**
     * Wrapper for safe execution with error handling.
     */
    private suspend fun <T> safeCall(operation: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            logger.error(TAG, "$operation failed: ${e.message}", e)
            Result.failure(MetricsAggregationException("Failed $operation: ${e.message}", e))
        }
    }

    public companion object {
        private const val TAG = "MetricsRepository"
        
        /** Maximum number of metrics entries to keep in history */
        public const val MAX_HISTORY_SIZE: Int = 300
        
        /** Minimum interval between metric collections in milliseconds */
        public const val MIN_INTERVAL_MS: Long = 100L
        
        /** Interval for health score checks in milliseconds */
        public const val HEALTH_CHECK_INTERVAL_MS: Long = 1000L
        
        /** Threshold for high CPU usage warning */
        public const val HIGH_CPU_THRESHOLD: Float = 90f
        
        /** Threshold for high memory usage warning */
        public const val HIGH_MEMORY_THRESHOLD: Float = 90f
    }
}

/**
 * Exception thrown when metrics aggregation fails.
 */
public class MetricsAggregationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

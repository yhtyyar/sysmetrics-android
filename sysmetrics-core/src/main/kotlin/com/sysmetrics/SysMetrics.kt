package com.sysmetrics

import android.content.Context
import com.sysmetrics.data.cache.MetricsCache
import com.sysmetrics.data.export.CsvMetricsExporter
import com.sysmetrics.data.export.ExportManager
import com.sysmetrics.data.repository.MetricsRepositoryImpl
import com.sysmetrics.domain.export.ExportConfig
import com.sysmetrics.domain.export.MetricsExporter
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.HealthScore
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.domain.model.TimeWindow
import com.sysmetrics.domain.repository.IMetricsRepository
import com.sysmetrics.infrastructure.android.AndroidMetricsProvider
import com.sysmetrics.infrastructure.android.NetworkMetricsProvider
import com.sysmetrics.infrastructure.logger.AndroidMetricsLogger
import com.sysmetrics.infrastructure.logger.NoOpLogger
import com.sysmetrics.infrastructure.proc.ProcFileReader
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Main entry point for the SysMetrics library.
 *
 * SysMetrics provides comprehensive system metrics collection for Android devices,
 * including CPU, memory, battery, thermal, and storage metrics. It follows clean
 * architecture principles and provides both one-shot and streaming APIs.
 *
 * ## Quick Start
 *
 * ```kotlin
 * // Initialize (call once, typically in Application.onCreate())
 * SysMetrics.initialize(applicationContext)
 *
 * // Get current metrics
 * val result = SysMetrics.getCurrentMetrics()
 * result.onSuccess { metrics ->
 *     println("CPU Usage: ${metrics.cpuMetrics.usagePercent}%")
 * }
 *
 * // Observe metrics stream
 * SysMetrics.observeMetrics(intervalMs = 1000)
 *     .collect { metrics ->
 *         // Handle real-time metrics
 *     }
 *
 * // Cleanup when done
 * SysMetrics.destroy()
 * ```
 *
 * ## Thread Safety
 *
 * All methods are thread-safe and can be called from any thread.
 * Initialization is idempotent - multiple calls are safe.
 *
 * ## Performance
 *
 * - Startup: < 100ms
 * - Per-collection: < 5ms (p99)
 * - Memory: < 5MB steady state
 * - Cache TTL: 500ms
 *
 * @see IMetricsRepository for the complete repository interface
 * @see SystemMetrics for the main metrics data class
 */
public object SysMetrics {

    private val initialized = AtomicBoolean(false)
    private val repositoryRef = AtomicReference<IMetricsRepository?>(null)
    private val contextRef = AtomicReference<Context?>(null)
    private val exportManagerRef = AtomicReference<ExportManager?>(null)
    private val loggerRef = AtomicReference<MetricsLogger>(NoOpLogger)

    /**
     * Library version string.
     */
    public const val VERSION: String = "1.0.0"

    /**
     * Library name.
     */
    public const val NAME: String = "SysMetrics"

    /**
     * Initializes the SysMetrics library.
     *
     * Must be called before any other methods. Safe to call multiple times;
     * subsequent calls after successful initialization are no-ops.
     *
     * Recommended to call in [android.app.Application.onCreate].
     *
     * @param context Application context (will be stored as application context)
     * @param logger Optional custom logger (default: AndroidMetricsLogger)
     * @throws IllegalStateException if context is null
     *
     * Example:
     * ```kotlin
     * class MyApplication : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *         // Default logger
     *         SysMetrics.initialize(this)
     *         
     *         // Custom logger
     *         SysMetrics.initialize(this, AndroidMetricsLogger.forDevelopment())
     *     }
     * }
     * ```
     */
    public fun initialize(context: Context, logger: MetricsLogger = AndroidMetricsLogger()) {
        if (initialized.get()) {
            return
        }

        synchronized(this) {
            if (initialized.get()) {
                return
            }

            val appContext = context.applicationContext
            contextRef.set(appContext)
            loggerRef.set(logger)
            
            logger.info(TAG, "Initializing SysMetrics v$VERSION")

            val procFileReader = ProcFileReader()
            val androidProvider = AndroidMetricsProvider(appContext)
            val networkProvider = NetworkMetricsProvider(appContext)
            val cache = MetricsCache()

            val repository = MetricsRepositoryImpl(
                procFileReader = procFileReader,
                androidProvider = androidProvider,
                networkProvider = networkProvider,
                cache = cache,
                logger = logger
            )

            repositoryRef.set(repository)
            
            // Initialize export manager with default exporters
            exportManagerRef.set(ExportManager.withAllExporters(logger))
            
            initialized.set(true)
            logger.info(TAG, "SysMetrics initialized successfully")
        }
    }

    /**
     * Returns the underlying [IMetricsRepository] instance.
     *
     * Use this for advanced operations or when you need direct repository access.
     *
     * @return The [IMetricsRepository] instance
     * @throws IllegalStateException if library is not initialized
     *
     * Example:
     * ```kotlin
     * val repository = SysMetrics.getRepository()
     * repository.initialize()
     * val history = repository.getMetricsHistory(count = 100)
     * ```
     */
    public fun getRepository(): IMetricsRepository {
        checkInitialized()
        return repositoryRef.get()
            ?: throw IllegalStateException("Repository is null. Call initialize() first.")
    }

    /**
     * Retrieves the current system metrics snapshot.
     *
     * Returns cached metrics if still valid (within 500ms TTL), otherwise
     * collects fresh metrics from the system. This is a suspend function
     * that should be called from a coroutine context.
     *
     * @return [Result] containing [SystemMetrics] on success, or an error on failure
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     SysMetrics.getCurrentMetrics()
     *         .onSuccess { metrics ->
     *             textView.text = "CPU: ${metrics.cpuMetrics.usagePercent}%"
     *         }
     *         .onFailure { error ->
     *             Log.e("SysMetrics", "Failed to get metrics", error)
     *         }
     * }
     * ```
     */
    public suspend fun getCurrentMetrics(): Result<SystemMetrics> {
        return try {
            checkInitialized()
            getRepository().getCurrentMetrics()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observes system metrics as a continuous stream.
     *
     * Emits new metrics at the specified interval. Uses distinctUntilChanged
     * internally to avoid redundant emissions when values haven't changed
     * significantly.
     *
     * @param intervalMs Interval between emissions in milliseconds (default: 1000ms)
     * @return [Flow] of [SystemMetrics] that emits periodically
     * @throws IllegalStateException if library is not initialized
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     SysMetrics.observeMetrics(intervalMs = 500)
     *         .collect { metrics ->
     *             updateUI(metrics)
     *         }
     * }
     * ```
     */
    public fun observeMetrics(intervalMs: Long = 1000L): Flow<SystemMetrics> {
        checkInitialized()
        return getRepository().observeMetrics(intervalMs)
    }

    /**
     * Observes the system health score as a continuous stream.
     *
     * Calculates health score based on current metrics and emits updates
     * when the score or detected issues change. The health score considers
     * CPU usage, memory usage, temperature, and battery level.
     *
     * @return [Flow] of [HealthScore] that emits when health status changes
     * @throws IllegalStateException if library is not initialized
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     SysMetrics.observeHealthScore()
     *         .collect { healthScore ->
     *             when (healthScore.status) {
     *                 HealthStatus.EXCELLENT -> showGreenIndicator()
     *                 HealthStatus.GOOD -> showYellowIndicator()
     *                 HealthStatus.WARNING -> showOrangeIndicator()
     *                 HealthStatus.CRITICAL -> showRedIndicator()
     *             }
     *         }
     * }
     * ```
     */
    public fun observeHealthScore(): Flow<HealthScore> {
        checkInitialized()
        return getRepository().observeHealthScore()
    }

    /**
     * Retrieves historical metrics from the internal buffer.
     *
     * Returns up to [count] most recent metrics snapshots.
     * History is bounded to 300 items maximum.
     *
     * @param count Maximum number of historical entries to retrieve (default: 60)
     * @return [Result] containing list of [SystemMetrics] or an error
     *
     * Example:
     * ```kotlin
     * lifecycleScope.launch {
     *     SysMetrics.getMetricsHistory(count = 30)
     *         .onSuccess { history ->
     *             val averageCpu = history.map { it.cpuMetrics.usagePercent }.average()
     *             println("Average CPU over last 30 samples: $averageCpu%")
     *         }
     * }
     * ```
     */
    public suspend fun getMetricsHistory(count: Int = 60): Result<List<SystemMetrics>> {
        return try {
            checkInitialized()
            getRepository().getMetricsHistory(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears the metrics history buffer.
     *
     * Removes all stored historical metrics. Does not affect current metrics
     * or cached values.
     *
     * @return [Result.success] if cleared successfully, [Result.failure] otherwise
     */
    public suspend fun clearHistory(): Result<Unit> {
        return try {
            checkInitialized()
            getRepository().clearHistory()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Releases all resources held by the library.
     *
     * Cancels any active metric collection, clears caches and history,
     * and releases system resources. The library can be re-initialized
     * after calling this method by calling [initialize] again.
     *
     * @return [Result.success] if destroyed successfully, [Result.failure] otherwise
     *
     * Example:
     * ```kotlin
     * override fun onTerminate() {
     *     super.onTerminate()
     *     runBlocking {
     *         SysMetrics.destroy()
     *     }
     * }
     * ```
     */
    public suspend fun destroy(): Result<Unit> {
        return try {
            if (!initialized.get()) {
                return Result.success(Unit)
            }

            synchronized(this) {
                val repository = repositoryRef.get()
                val result = repository?.destroy() ?: Result.success(Unit)
                
                repositoryRef.set(null)
                contextRef.set(null)
                initialized.set(false)
                
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the library has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public fun isInitialized(): Boolean = initialized.get()

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
     * lifecycleScope.launch {
     *     SysMetrics.getAggregatedMetrics(TimeWindow.FIVE_MINUTES)
     *         .onSuccess { metrics ->
     *             println("Avg CPU: ${metrics.cpuPercentAverage}%")
     *             println("Samples: ${metrics.sampleCount}")
     *         }
     *         .onFailure { error ->
     *             Log.e("SysMetrics", "Failed to aggregate", error)
     *         }
     * }
     * ```
     */
    public suspend fun getAggregatedMetrics(timeWindow: TimeWindow): Result<AggregatedMetrics> {
        return try {
            checkInitialized()
            getRepository().getAggregatedMetrics(timeWindow)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
     * lifecycleScope.launch {
     *     // Get hourly chart data (12 five-minute intervals)
     *     SysMetrics.getAggregatedHistory(TimeWindow.FIVE_MINUTES, count = 12)
     *         .onSuccess { history ->
     *             val cpuTrend = history.map { it.cpuPercentAverage }
     *             plotChart(cpuTrend)
     *         }
     *         .onFailure { error ->
     *             Log.e("SysMetrics", "Failed to get history", error)
     *         }
     * }
     * ```
     */
    public suspend fun getAggregatedHistory(
        timeWindow: TimeWindow,
        count: Int = 12
    ): Result<List<AggregatedMetrics>> {
        return try {
            checkInitialized()
            getRepository().getAggregatedHistory(timeWindow, count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Export API ====================

    /**
     * Exports raw system metrics to the specified format.
     *
     * Supported formats depend on registered exporters. Default: "csv".
     *
     * @param metrics List of system metrics to export
     * @param format Format name (e.g., "csv", "json")
     * @param config Export configuration (use format-specific config for more options)
     * @return [Result.success] with exported string or [Result.failure] with exception
     *
     * Example:
     * ```kotlin
     * val history = SysMetrics.getMetricsHistory(count = 100).getOrNull() ?: emptyList()
     * SysMetrics.exportMetrics(history, "csv", CsvExportConfig.forExcel())
     *     .onSuccess { csv ->
     *         File(cacheDir, "metrics.csv").writeText(csv)
     *     }
     *     .onFailure { error ->
     *         Log.e("SysMetrics", "Export failed", error)
     *     }
     * ```
     */
    public fun exportMetrics(
        metrics: List<SystemMetrics>,
        format: String = "csv",
        config: ExportConfig = ExportConfig()
    ): Result<String> {
        return try {
            checkInitialized()
            val exportManager = exportManagerRef.get()
                ?: return Result.failure(IllegalStateException("ExportManager not initialized"))
            exportManager.exportRawMetrics(metrics, format, config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports aggregated metrics to the specified format.
     *
     * Useful for exporting trend analysis data and charts.
     *
     * @param aggregated List of aggregated metrics to export
     * @param format Format name (e.g., "csv", "json")
     * @param config Export configuration
     * @return [Result.success] with exported string or [Result.failure] with exception
     *
     * Example:
     * ```kotlin
     * val history = SysMetrics.getAggregatedHistory(TimeWindow.FIVE_MINUTES, 12)
     *     .getOrNull() ?: emptyList()
     * SysMetrics.exportAggregatedMetrics(history, "csv")
     *     .onSuccess { csv ->
     *         shareFile(csv, "metrics_hourly.csv")
     *     }
     * ```
     */
    public fun exportAggregatedMetrics(
        aggregated: List<AggregatedMetrics>,
        format: String = "csv",
        config: ExportConfig = ExportConfig()
    ): Result<String> {
        return try {
            checkInitialized()
            val exportManager = exportManagerRef.get()
                ?: return Result.failure(IllegalStateException("ExportManager not initialized"))
            exportManager.exportAggregatedMetrics(aggregated, format, config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns the list of supported export formats.
     *
     * @return List of format names (e.g., ["csv", "json"])
     */
    public fun getSupportedExportFormats(): List<String> {
        return exportManagerRef.get()?.getSupportedFormats() ?: emptyList()
    }

    /**
     * Returns the MIME type for a given export format.
     *
     * @param format Format name
     * @return MIME type string or null if format is not supported
     */
    public fun getExportMimeType(format: String): String? {
        return exportManagerRef.get()?.getMimeType(format)
    }

    /**
     * Returns the file extension for a given export format.
     *
     * @param format Format name
     * @return File extension (without dot) or null if format is not supported
     */
    public fun getExportFileExtension(format: String): String? {
        return exportManagerRef.get()?.getFileExtension(format)
    }

    private fun checkInitialized() {
        if (!initialized.get()) {
            throw IllegalStateException(
                "SysMetrics is not initialized. Call SysMetrics.initialize(context) first."
            )
        }
    }

    private const val TAG = "SysMetrics"
}

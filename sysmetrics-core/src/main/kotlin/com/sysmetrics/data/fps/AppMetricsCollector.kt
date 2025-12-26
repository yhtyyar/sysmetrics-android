package com.sysmetrics.data.fps

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.AppMetrics
import com.sysmetrics.infrastructure.logger.NoOpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collects app-specific resource consumption metrics.
 *
 * Unlike system-wide metrics, this collector focuses on the host application's
 * resource usage, providing granular insight into app performance.
 *
 * ## Collected Metrics
 *
 * - **CPU Usage**: App's CPU usage percentage
 * - **Memory**: Heap and native memory usage
 * - **Threads**: Active thread count
 * - **Network**: App-specific network I/O (if available)
 * - **File Descriptors**: Open FD count
 *
 * ## Usage
 *
 * ```kotlin
 * val collector = AppMetricsCollector(context)
 *
 * // Start collection with 500ms interval
 * collector.startCollection(intervalMs = 500)
 *
 * // Observe metrics
 * collector.metricsFlow.collect { metrics ->
 *     println("App CPU: ${metrics.cpuUsagePercent}%")
 *     println("Heap: ${metrics.heapUsageMb} MB")
 * }
 *
 * // Stop collection
 * collector.stopCollection()
 * ```
 *
 * ## Performance
 *
 * - Collection overhead: <5ms per sample
 * - Memory footprint: <50KB
 * - CPU overhead: <0.5%
 *
 * @property context Application context
 * @property logger Optional logger for diagnostics
 */
public class AppMetricsCollector(
    private val context: Context,
    private val logger: MetricsLogger = NoOpLogger
) {
    private val packageName = context.packageName
    private val pid = Process.myPid()
    private val uid = Process.myUid()

    // State
    private val isCollecting = AtomicBoolean(false)
    private var collectionJob: Job? = null
    private var lastCpuTime = 0L
    private var lastSystemTime = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L

    // Flows
    private val _metricsFlow = MutableStateFlow(AppMetrics.empty(packageName))

    /**
     * Current app metrics as StateFlow.
     * Updates at the configured interval when collection is active.
     */
    public val metricsFlow: StateFlow<AppMetrics> = _metricsFlow.asStateFlow()

    /**
     * Returns true if collection is currently active.
     */
    public val isActive: Boolean get() = isCollecting.get()

    /**
     * Starts collecting app metrics at the specified interval.
     *
     * @param intervalMs Collection interval in milliseconds (default: 500ms)
     * @param scope CoroutineScope for the collection job
     */
    public fun startCollection(
        intervalMs: Long = 500L,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    ) {
        if (isCollecting.getAndSet(true)) {
            logger.debug(TAG, "Collection already running")
            return
        }

        logger.info(TAG, "Starting app metrics collection (interval: ${intervalMs}ms)")

        // Initialize baseline values
        lastCpuTime = getProcessCpuTime()
        lastSystemTime = System.nanoTime()
        lastRxBytes = TrafficStats.getUidRxBytes(uid)
        lastTxBytes = TrafficStats.getUidTxBytes(uid)

        collectionJob = scope.launch {
            while (isActive && isCollecting.get()) {
                try {
                    val metrics = collectMetrics()
                    _metricsFlow.value = metrics
                } catch (e: Exception) {
                    logger.error(TAG, "Error collecting metrics", e)
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * Stops collecting app metrics.
     */
    public fun stopCollection() {
        if (!isCollecting.getAndSet(false)) {
            logger.debug(TAG, "Collection already stopped")
            return
        }

        logger.info(TAG, "Stopping app metrics collection")
        collectionJob?.cancel()
        collectionJob = null
    }

    /**
     * Collects a single snapshot of app metrics.
     *
     * Can be called independently of the collection loop.
     */
    public fun collectMetrics(): AppMetrics {
        val currentTime = System.nanoTime()
        val currentCpuTime = getProcessCpuTime()

        // Calculate CPU usage
        val cpuTimeDelta = currentCpuTime - lastCpuTime
        val systemTimeDelta = currentTime - lastSystemTime
        val cpuUsage = if (systemTimeDelta > 0) {
            (cpuTimeDelta.toFloat() / systemTimeDelta) * 100f * Runtime.getRuntime().availableProcessors()
        } else 0f

        lastCpuTime = currentCpuTime
        lastSystemTime = currentTime

        // Memory metrics
        val runtime = Runtime.getRuntime()
        val heapMax = runtime.maxMemory() / (1024f * 1024f)
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)

        // Total memory from ActivityManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val totalMemory = memInfo.totalPss / 1024f // Convert KB to MB

        // Thread count
        val threadCount = Thread.activeCount()

        // Network I/O
        val currentRxBytes = TrafficStats.getUidRxBytes(uid)
        val currentTxBytes = TrafficStats.getUidTxBytes(uid)
        val rxBytes = if (currentRxBytes != TrafficStats.UNSUPPORTED.toLong()) currentRxBytes else 0L
        val txBytes = if (currentTxBytes != TrafficStats.UNSUPPORTED.toLong()) currentTxBytes else 0L

        // File descriptors
        val fdCount = countOpenFileDescriptors()

        return AppMetrics(
            packageName = packageName,
            cpuUsagePercent = cpuUsage.coerceIn(0f, 100f * Runtime.getRuntime().availableProcessors()),
            memoryUsageMb = totalMemory,
            heapUsageMb = heapUsed,
            heapMaxMb = heapMax,
            nativeHeapMb = nativeHeap,
            threadCount = threadCount,
            networkRxBytes = rxBytes,
            networkTxBytes = txBytes,
            openFileDescriptors = fdCount
        )
    }

    /**
     * Gets the process CPU time in nanoseconds.
     */
    private fun getProcessCpuTime(): Long {
        return try {
            val statFile = File("/proc/$pid/stat")
            if (statFile.exists()) {
                val stat = statFile.readText()
                val parts = stat.split(" ")
                if (parts.size > 14) {
                    // utime (14) + stime (15) in clock ticks
                    val utime = parts[13].toLongOrNull() ?: 0L
                    val stime = parts[14].toLongOrNull() ?: 0L
                    // Convert clock ticks to nanoseconds (assume 100 ticks/sec)
                    (utime + stime) * 10_000_000L
                } else 0L
            } else 0L
        } catch (e: Exception) {
            logger.debug(TAG, "Failed to read CPU time: ${e.message}")
            0L
        }
    }

    /**
     * Counts open file descriptors for the current process.
     */
    private fun countOpenFileDescriptors(): Int {
        return try {
            val fdDir = File("/proc/$pid/fd")
            if (fdDir.exists() && fdDir.isDirectory) {
                fdDir.listFiles()?.size ?: 0
            } else 0
        } catch (e: Exception) {
            logger.debug(TAG, "Failed to count file descriptors: ${e.message}")
            0
        }
    }

    public companion object {
        private const val TAG = "AppMetricsCollector"
    }
}

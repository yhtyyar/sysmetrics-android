package com.sysmetrics.infrastructure.proc

import com.sysmetrics.domain.model.CpuMetrics
import com.sysmetrics.domain.model.MemoryMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads system metrics from /proc filesystem.
 *
 * This class provides low-level access to Linux proc files for
 * collecting CPU and memory metrics. All file I/O operations
 * are performed on [Dispatchers.IO] to avoid blocking the main thread.
 *
 * Thread-safe: Uses mutex for CPU usage calculations that require
 * maintaining state between calls.
 */
public class ProcFileReader {

    private val mutex = Mutex()
    
    @Volatile
    private var lastCpuStats: CpuStats? = null
    
    @Volatile
    private var lastCpuStatsTime: Long = 0L

    private data class CpuStats(
        val user: Long,
        val nice: Long,
        val system: Long,
        val idle: Long,
        val iowait: Long,
        val irq: Long,
        val softirq: Long,
        val steal: Long
    ) {
        val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
        val active: Long get() = total - idle - iowait
    }

    /**
     * Reads CPU metrics from /proc/stat and /proc/cpuinfo.
     *
     * Calculates CPU usage by comparing current stats with previous
     * reading. First call returns 0% usage as baseline.
     *
     * @return [Result] containing [CpuMetrics] or an error
     */
    public suspend fun readCpuMetrics(): Result<CpuMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val currentStats = parseCpuStats()
                val coreCount = getCoreCount()
                val frequencies = getCpuFrequencies(coreCount)
                
                val usagePercent = calculateCpuUsage(currentStats)
                
                CpuMetrics(
                    usagePercent = usagePercent,
                    physicalCores = coreCount,
                    logicalCores = coreCount,
                    maxFrequencyKHz = frequencies.maxOrNull(),
                    currentFrequencyKHz = frequencies.firstOrNull(),
                    coreFrequencies = frequencies.takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    /**
     * Reads memory metrics from /proc/meminfo.
     *
     * @return [Result] containing [MemoryMetrics] or an error
     */
    public suspend fun readMemoryMetrics(): Result<MemoryMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            val memInfo = parseMemInfo()
            
            val totalKB = memInfo["MemTotal"] ?: 0L
            val freeKB = memInfo["MemFree"] ?: 0L
            val availableKB = memInfo["MemAvailable"] ?: freeKB
            val buffersKB = memInfo["Buffers"]
            val cachedKB = memInfo["Cached"]
            val swapTotalKB = memInfo["SwapTotal"]
            val swapFreeKB = memInfo["SwapFree"]
            
            val totalMB = totalKB / 1024
            val freeMB = freeKB / 1024
            val availableMB = availableKB / 1024
            val usedMB = totalMB - availableMB
            
            val usagePercent = if (totalMB > 0) {
                ((usedMB.toFloat() / totalMB.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
            
            MemoryMetrics(
                totalMemoryMB = totalMB,
                usedMemoryMB = usedMB,
                freeMemoryMB = freeMB,
                availableMemoryMB = availableMB,
                usagePercent = usagePercent,
                buffersMB = buffersKB?.let { it / 1024 },
                cachedMB = cachedKB?.let { it / 1024 },
                swapTotalMB = swapTotalKB?.let { it / 1024 },
                swapFreeMB = swapFreeKB?.let { it / 1024 }
            )
        }
    }

    /**
     * Reads system uptime from /proc/uptime.
     *
     * @return [Result] containing uptime in milliseconds or an error
     */
    public suspend fun readUptime(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val uptimeFile = File("/proc/uptime")
            if (uptimeFile.exists()) {
                val content = uptimeFile.readText().trim()
                val uptimeSeconds = content.split(" ").firstOrNull()?.toDoubleOrNull() ?: 0.0
                (uptimeSeconds * 1000).toLong()
            } else {
                0L
            }
        }
    }

    private fun parseCpuStats(): CpuStats {
        val statFile = File("/proc/stat")
        if (!statFile.exists()) {
            return CpuStats(0, 0, 0, 0, 0, 0, 0, 0)
        }
        
        val cpuLine = statFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("cpu ") }
        } ?: return CpuStats(0, 0, 0, 0, 0, 0, 0, 0)
        
        val parts = cpuLine.split("\\s+".toRegex()).drop(1)
        
        return CpuStats(
            user = parts.getOrNull(0)?.toLongOrNull() ?: 0,
            nice = parts.getOrNull(1)?.toLongOrNull() ?: 0,
            system = parts.getOrNull(2)?.toLongOrNull() ?: 0,
            idle = parts.getOrNull(3)?.toLongOrNull() ?: 0,
            iowait = parts.getOrNull(4)?.toLongOrNull() ?: 0,
            irq = parts.getOrNull(5)?.toLongOrNull() ?: 0,
            softirq = parts.getOrNull(6)?.toLongOrNull() ?: 0,
            steal = parts.getOrNull(7)?.toLongOrNull() ?: 0
        )
    }

    private fun calculateCpuUsage(currentStats: CpuStats): Float {
        val previous = lastCpuStats
        val currentTime = System.currentTimeMillis()
        
        lastCpuStats = currentStats
        lastCpuStatsTime = currentTime
        
        if (previous == null) {
            return 0f
        }
        
        val totalDiff = currentStats.total - previous.total
        val activeDiff = currentStats.active - previous.active
        
        return if (totalDiff > 0) {
            ((activeDiff.toFloat() / totalDiff.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
    }

    private fun getCoreCount(): Int {
        return try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (cpuInfoFile.exists()) {
                val processorCount = cpuInfoFile.useLines { lines ->
                    lines.count { it.startsWith("processor") }
                }
                if (processorCount > 0) processorCount else Runtime.getRuntime().availableProcessors()
            } else {
                Runtime.getRuntime().availableProcessors()
            }
        } catch (e: Exception) {
            Runtime.getRuntime().availableProcessors()
        }
    }

    private fun getCpuFrequencies(coreCount: Int): List<Long> {
        val frequencies = mutableListOf<Long>()
        
        for (i in 0 until coreCount) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (freqFile.exists()) {
                try {
                    val freq = freqFile.readText().trim().toLongOrNull()
                    if (freq != null) {
                        frequencies.add(freq)
                    }
                } catch (e: Exception) {
                    // Ignore and continue
                }
            }
        }
        
        return frequencies
    }

    private fun parseMemInfo(): Map<String, Long> {
        val memInfoFile = File("/proc/meminfo")
        if (!memInfoFile.exists()) {
            return emptyMap()
        }
        
        return memInfoFile.useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val valueStr = parts[1].trim().replace(" kB", "").replace("kB", "")
                    val value = valueStr.trim().toLongOrNull()
                    if (value != null) {
                        key to value
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.toMap()
        }
    }

    /**
     * Resets internal state for CPU usage calculations.
     *
     * Call this when reinitializing metrics collection to ensure
     * accurate readings from a clean baseline.
     */
    public fun reset() {
        lastCpuStats = null
        lastCpuStatsTime = 0L
    }
}

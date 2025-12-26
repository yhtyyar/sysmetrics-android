package com.sysmetrics.data.fps

import com.sysmetrics.domain.model.AppMetrics
import com.sysmetrics.infrastructure.logger.TestLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AppMetricsCollector] and [AppMetrics].
 *
 * Tests cover:
 * - AppMetrics data class validation
 * - Memory status calculation
 * - Heap usage percentage
 * - Collector state management
 */
class AppMetricsCollectorTest {

    private lateinit var testLogger: TestLogger

    @Before
    fun setUp() {
        testLogger = TestLogger()
    }

    @After
    fun tearDown() {
    }

    // ==================== AppMetrics Validation Tests ====================

    @Test
    fun `AppMetrics validates non-negative cpuUsagePercent`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppMetrics(
                packageName = "com.test",
                cpuUsagePercent = -1f,
                memoryUsageMb = 100f,
                heapUsageMb = 50f,
                heapMaxMb = 256f,
                nativeHeapMb = 20f,
                threadCount = 10
            )
        }
    }

    @Test
    fun `AppMetrics validates non-negative memoryUsageMb`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppMetrics(
                packageName = "com.test",
                cpuUsagePercent = 10f,
                memoryUsageMb = -1f,
                heapUsageMb = 50f,
                heapMaxMb = 256f,
                nativeHeapMb = 20f,
                threadCount = 10
            )
        }
    }

    @Test
    fun `AppMetrics validates non-negative threadCount`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppMetrics(
                packageName = "com.test",
                cpuUsagePercent = 10f,
                memoryUsageMb = 100f,
                heapUsageMb = 50f,
                heapMaxMb = 256f,
                nativeHeapMb = 20f,
                threadCount = -1
            )
        }
    }

    // ==================== Heap Usage Percentage Tests ====================

    @Test
    fun `heapUsagePercent is calculated correctly`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 128f,
            heapMaxMb = 256f,
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(50f, metrics.heapUsagePercent, 0.1f)
    }

    @Test
    fun `heapUsagePercent returns zero when heapMaxMb is zero`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 50f,
            heapMaxMb = 0f,
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(0f, metrics.heapUsagePercent, 0.1f)
    }

    // ==================== Memory Status Tests ====================

    @Test
    fun `memoryStatus is HEALTHY when heap usage below 60 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 100f,
            heapMaxMb = 256f, // ~39% usage
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(AppMetrics.MemoryStatus.HEALTHY, metrics.memoryStatus)
    }

    @Test
    fun `memoryStatus is MODERATE when heap usage 60-79 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 180f,
            heapMaxMb = 256f, // ~70% usage
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(AppMetrics.MemoryStatus.MODERATE, metrics.memoryStatus)
    }

    @Test
    fun `memoryStatus is WARNING when heap usage 80-94 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 220f,
            heapMaxMb = 256f, // ~86% usage
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(AppMetrics.MemoryStatus.WARNING, metrics.memoryStatus)
    }

    @Test
    fun `memoryStatus is CRITICAL when heap usage 95 percent or higher`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 250f,
            heapMaxMb = 256f, // ~97% usage
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertEquals(AppMetrics.MemoryStatus.CRITICAL, metrics.memoryStatus)
    }

    // ==================== Warning Flags Tests ====================

    @Test
    fun `isHeapWarning returns true when heap usage above 80 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 220f,
            heapMaxMb = 256f,
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertTrue(metrics.isHeapWarning)
    }

    @Test
    fun `isHeapWarning returns false when heap usage below 80 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 100f,
            heapMaxMb = 256f,
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertFalse(metrics.isHeapWarning)
    }

    @Test
    fun `isHeapCritical returns true when heap usage above 95 percent`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 250f,
            heapMaxMb = 256f,
            nativeHeapMb = 20f,
            threadCount = 10
        )
        assertTrue(metrics.isHeapCritical)
    }

    // ==================== Total Memory Tests ====================

    @Test
    fun `totalMemoryMb sums heap and native memory`() {
        val metrics = AppMetrics(
            packageName = "com.test",
            cpuUsagePercent = 10f,
            memoryUsageMb = 100f,
            heapUsageMb = 50f,
            heapMaxMb = 256f,
            nativeHeapMb = 30f,
            threadCount = 10
        )
        assertEquals(80f, metrics.totalMemoryMb, 0.1f)
    }

    // ==================== Empty Factory Test ====================

    @Test
    fun `empty creates zero-value metrics`() {
        val empty = AppMetrics.empty("com.test")
        assertEquals("com.test", empty.packageName)
        assertEquals(0f, empty.cpuUsagePercent, 0.1f)
        assertEquals(0f, empty.memoryUsageMb, 0.1f)
        assertEquals(0f, empty.heapUsageMb, 0.1f)
        assertEquals(0, empty.threadCount)
    }
}

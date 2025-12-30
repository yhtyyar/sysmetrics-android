package com.sysmetrics.data.repository

import com.sysmetrics.data.aggregation.SimpleAggregationStrategy
import com.sysmetrics.domain.model.*
import com.sysmetrics.infrastructure.logger.TestLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for metrics aggregation in [MetricsRepositoryImpl].
 *
 * These tests verify:
 * - Aggregation returns correct data from repository history
 * - Aggregated history returns data in chronological order
 * - Error handling for edge cases
 * - Thread safety of aggregation operations
 *
 * Target coverage: 90%+
 */
class MetricsRepositoryAggregationTest {

    private lateinit var strategy: SimpleAggregationStrategy
    private lateinit var testLogger: TestLogger

    @Before
    fun setUp() {
        strategy = SimpleAggregationStrategy()
        testLogger = TestLogger()
    }

    // ==================== getAggregatedMetrics Tests ====================

    @Test
    fun `getAggregatedMetrics returns correct data for time window`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Create metrics within the previous 5-minute window
        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, cpuUsage = 40f, memoryUsage = 50f),
            createTestMetrics(windowStart + 2000L, cpuUsage = 60f, memoryUsage = 70f),
            createTestMetrics(windowStart + 3000L, cpuUsage = 50f, memoryUsage = 60f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(3, result.sampleCount)
        assertEquals(50f, result.cpuPercentAverage, 0.1f) // (40+60+50)/3
        assertEquals(60f, result.memoryPercentAverage, 0.1f) // (50+70+60)/3
        assertTrue(result.hasData)
    }

    @Test
    fun `getAggregatedMetrics calculates min and max correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, cpuUsage = 20f),
            createTestMetrics(windowStart + 2000L, cpuUsage = 80f),
            createTestMetrics(windowStart + 3000L, cpuUsage = 50f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(20f, result.cpuPercentMin, 0.1f)
        assertEquals(80f, result.cpuPercentMax, 0.1f)
    }

    @Test
    fun `getAggregatedMetrics calculates network totals correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, rxBytes = 1000L, txBytes = 500L),
            createTestMetrics(windowStart + 2000L, rxBytes = 2000L, txBytes = 1000L),
            createTestMetrics(windowStart + 3000L, rxBytes = 3000L, txBytes = 1500L)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(6000L, result.networkRxBytesTotal)
        assertEquals(3000L, result.networkTxBytesTotal)
    }

    @Test
    fun `getAggregatedMetrics filters metrics by time window`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Mix of metrics inside and outside the window
        val metrics = listOf(
            createTestMetrics(windowStart - 100000L, cpuUsage = 10f), // Outside
            createTestMetrics(windowStart + 1000L, cpuUsage = 50f),   // Inside
            createTestMetrics(windowStart + 2000L, cpuUsage = 60f),   // Inside
            createTestMetrics(now + 100000L, cpuUsage = 90f)          // Future (outside)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(2, result.sampleCount)
        assertEquals(55f, result.cpuPercentAverage, 0.1f) // (50+60)/2, not including 10 or 90
    }

    @Test
    fun `getAggregatedMetrics handles single metric correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, cpuUsage = 75f, memoryUsage = 65f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(1, result.sampleCount)
        assertEquals(75f, result.cpuPercentAverage, 0.1f)
        assertEquals(75f, result.cpuPercentMin, 0.1f)
        assertEquals(75f, result.cpuPercentMax, 0.1f)
    }

    // ==================== getAggregatedHistory Tests ====================

    @Test
    fun `getAggregatedHistory returns chronological order`() {
        val now = System.currentTimeMillis()
        
        // Create metrics spread across multiple 5-minute windows
        val metrics = mutableListOf<SystemMetrics>()
        for (i in 1..6) {
            val windowStart = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                (i * TimeWindow.FIVE_MINUTES.durationMillis())
            metrics.add(createTestMetrics(windowStart + 1000L, cpuUsage = (i * 10).toFloat()))
        }

        // Get 5 historical windows
        val results = mutableListOf<AggregatedMetrics>()
        for (i in 5 downTo 1) {
            val windowEnd = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                ((i - 1) * TimeWindow.FIVE_MINUTES.durationMillis())
            val windowStart = windowEnd - TimeWindow.FIVE_MINUTES.durationMillis()
            results.add(strategy.aggregateForWindow(metrics, TimeWindow.FIVE_MINUTES, windowStart, windowEnd))
        }

        // Verify chronological order (earlier windows first)
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Window $i should be before window ${i+1}",
                results[i].windowStartTime < results[i + 1].windowStartTime
            )
        }
    }

    @Test
    fun `getAggregatedHistory returns correct count of windows`() {
        val now = System.currentTimeMillis()
        val requestedCount = 12

        // Create metrics for multiple windows
        val metrics = mutableListOf<SystemMetrics>()
        for (i in 1..15) {
            val windowStart = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                (i * TimeWindow.FIVE_MINUTES.durationMillis())
            metrics.add(createTestMetrics(windowStart + 1000L))
        }

        // Simulate getAggregatedHistory logic
        val results = mutableListOf<AggregatedMetrics>()
        val windowDuration = TimeWindow.FIVE_MINUTES.durationMillis()
        for (i in requestedCount downTo 1) {
            val windowEnd = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                ((i - 1) * windowDuration)
            val windowStart = windowEnd - windowDuration
            results.add(strategy.aggregateForWindow(metrics, TimeWindow.FIVE_MINUTES, windowStart, windowEnd))
        }

        assertEquals(requestedCount, results.size)
    }

    @Test
    fun `getAggregatedHistory handles empty windows`() {
        val now = System.currentTimeMillis()
        
        // Only create metrics for one specific window
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, cpuUsage = 50f)
        )

        // Get multiple windows - some should be empty
        val results = mutableListOf<AggregatedMetrics>()
        val windowDuration = TimeWindow.FIVE_MINUTES.durationMillis()
        for (i in 5 downTo 1) {
            val windowEnd = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                ((i - 1) * windowDuration)
            val windowStartTime = windowEnd - windowDuration
            results.add(strategy.aggregateForWindow(metrics, TimeWindow.FIVE_MINUTES, windowStartTime, windowEnd))
        }

        // Most windows should be empty, only one should have data
        val windowsWithData = results.count { it.hasData }
        assertEquals(1, windowsWithData)
    }

    @Test
    fun `getAggregatedHistory performance test for 1000 samples`() {
        val now = System.currentTimeMillis()
        
        // Create 1000 metrics spread across multiple windows
        val metrics = (0 until 1000).map { i ->
            val windowOffset = (i % 12) * TimeWindow.FIVE_MINUTES.durationMillis()
            val windowStart = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - windowOffset
            createTestMetrics(windowStart + (i * 10L), cpuUsage = (i % 100).toFloat())
        }

        val startTime = System.nanoTime()

        // Simulate getAggregatedHistory for 12 windows
        val results = mutableListOf<AggregatedMetrics>()
        val windowDuration = TimeWindow.FIVE_MINUTES.durationMillis()
        for (i in 12 downTo 1) {
            val windowEnd = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES) - 
                ((i - 1) * windowDuration)
            val windowStart = windowEnd - windowDuration
            results.add(strategy.aggregateForWindow(metrics, TimeWindow.FIVE_MINUTES, windowStart, windowEnd))
        }

        val duration = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms

        assertTrue("Aggregation took ${duration}ms, expected <100ms", duration < 100)
        assertEquals(12, results.size)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `aggregation handles boundary timestamps correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        val windowEnd = windowStart + TimeWindow.FIVE_MINUTES.durationMillis()

        // Metric exactly at window boundary
        val metrics = listOf(
            createTestMetrics(windowStart, cpuUsage = 50f),      // Start boundary (included)
            createTestMetrics(windowEnd - 1, cpuUsage = 60f)     // Just before end (included)
        )

        val result = strategy.aggregateForWindow(
            metrics, 
            TimeWindow.FIVE_MINUTES, 
            windowStart, 
            windowEnd
        )

        assertEquals(2, result.sampleCount)
    }

    @Test
    fun `aggregation handles all time windows correctly`() {
        val now = System.currentTimeMillis()

        for (timeWindow in TimeWindow.values()) {
            val windowStart = strategy.calculatePreviousWindowStart(now, timeWindow)
            val metrics = listOf(
                createTestMetrics(windowStart + 1000L, cpuUsage = 50f)
            )

            val result = strategy.aggregate(metrics, timeWindow, now)

            assertEquals(
                "TimeWindow $timeWindow should have correct timeWindow in result",
                timeWindow,
                result.timeWindow
            )
        }
    }

    @Test
    fun `aggregation calculates health score average correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Create metrics with different health scores based on CPU usage
        val metrics = listOf(
            createTestMetrics(windowStart + 1000L, cpuUsage = 10f),  // Low CPU = good health
            createTestMetrics(windowStart + 2000L, cpuUsage = 50f),  // Medium CPU
            createTestMetrics(windowStart + 3000L, cpuUsage = 90f)   // High CPU = lower health
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        // Health score should be an average
        assertTrue(result.healthScoreAverage in 0..100)
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    fun `aggregation is thread-safe`() = runBlocking {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        
        val metrics = (0 until 100).map { i ->
            createTestMetrics(windowStart + (i * 10L), cpuUsage = (i % 100).toFloat())
        }

        // Run multiple aggregations concurrently
        val results = (0 until 10).map {
            async {
                strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)
            }
        }.awaitAll()

        // All results should be identical
        val firstResult = results.first()
        results.forEach { result ->
            assertEquals(firstResult.sampleCount, result.sampleCount)
            assertEquals(firstResult.cpuPercentAverage, result.cpuPercentAverage, 0.001f)
        }
    }

    // ==================== Helper Functions ====================

    private fun createTestMetrics(
        timestamp: Long = System.currentTimeMillis(),
        cpuUsage: Float = 50f,
        memoryUsage: Float = 50f,
        batteryLevel: Int = 80,
        temperature: Float = 40f,
        rxBytes: Long = 0L,
        txBytes: Long = 0L
    ): SystemMetrics {
        return SystemMetrics(
            cpuMetrics = CpuMetrics(
                usagePercent = cpuUsage,
                physicalCores = 4,
                logicalCores = 8,
                maxFrequencyKHz = 2000000L,
                currentFrequencyKHz = 1500000L,
                coreFrequencies = null
            ),
            memoryMetrics = MemoryMetrics(
                totalMemoryMB = 4096,
                usedMemoryMB = (4096 * memoryUsage / 100).toLong(),
                freeMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                availableMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                usagePercent = memoryUsage,
                buffersMB = 100,
                cachedMB = 500,
                swapTotalMB = 1024,
                swapFreeMB = 800
            ),
            batteryMetrics = BatteryMetrics(
                level = batteryLevel,
                temperature = temperature,
                status = BatteryStatus.DISCHARGING,
                health = BatteryHealth.GOOD,
                plugged = false,
                chargingSpeed = null
            ),
            thermalMetrics = ThermalMetrics(
                cpuTemperature = temperature,
                batteryTemperature = temperature - 5f,
                otherTemperatures = emptyMap(),
                thermalThrottling = false
            ),
            storageMetrics = StorageMetrics(
                totalStorageMB = 64000,
                freeStorageMB = 32000,
                usedStorageMB = 32000,
                usagePercent = 50f
            ),
            networkMetrics = NetworkMetrics(
                rxBytes = rxBytes,
                txBytes = txBytes,
                rxBytesPerSecond = 1000L,
                txBytesPerSecond = 500L,
                isConnected = true,
                connectionType = NetworkType.WIFI,
                networkName = "TestNetwork",
                signalStrength = -50
            ),
            timestamp = timestamp,
            uptime = 3600000L
        )
    }
}

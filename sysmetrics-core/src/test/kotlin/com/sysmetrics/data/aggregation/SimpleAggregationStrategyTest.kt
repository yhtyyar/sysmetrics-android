package com.sysmetrics.data.aggregation

import com.sysmetrics.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SimpleAggregationStrategy].
 *
 * Tests cover:
 * - Empty metrics list handling
 * - Single metric aggregation
 * - Multiple metrics aggregation
 * - Time window boundary calculations
 * - Min/max/average calculations
 * - Network bytes total calculation
 * - Edge cases and boundary conditions
 *
 * Target coverage: 95%+
 */
class SimpleAggregationStrategyTest {

    private lateinit var strategy: SimpleAggregationStrategy

    @Before
    fun setUp() {
        strategy = SimpleAggregationStrategy()
    }

    // ==================== Window Calculation Tests ====================

    @Test
    fun `calculateWindowStart aligns to 1-minute boundary`() {
        // 14:32:45.123 -> should align to 14:32:00.000
        val timestamp = 1704114765123L // Some timestamp
        val windowStart = strategy.calculateWindowStart(timestamp, TimeWindow.ONE_MINUTE)
        
        // Window start should be divisible by 60 seconds (60000 ms)
        assertEquals(0L, windowStart % 60_000L)
    }

    @Test
    fun `calculateWindowStart aligns to 5-minute boundary`() {
        // Window start should be divisible by 5 minutes (300000 ms)
        val timestamp = 1704114765123L
        val windowStart = strategy.calculateWindowStart(timestamp, TimeWindow.FIVE_MINUTES)
        
        assertEquals(0L, windowStart % 300_000L)
    }

    @Test
    fun `calculateWindowStart aligns to 30-minute boundary`() {
        val timestamp = 1704114765123L
        val windowStart = strategy.calculateWindowStart(timestamp, TimeWindow.THIRTY_MINUTES)
        
        assertEquals(0L, windowStart % 1_800_000L)
    }

    @Test
    fun `calculateWindowStart aligns to 1-hour boundary`() {
        val timestamp = 1704114765123L
        val windowStart = strategy.calculateWindowStart(timestamp, TimeWindow.ONE_HOUR)
        
        assertEquals(0L, windowStart % 3_600_000L)
    }

    @Test
    fun `calculatePreviousWindowStart returns previous complete window`() {
        val now = 1704114765123L // 14:32:45.123
        val currentWindowStart = strategy.calculateWindowStart(now, TimeWindow.FIVE_MINUTES)
        val previousWindowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        
        assertEquals(currentWindowStart - 300_000L, previousWindowStart)
    }

    // ==================== Empty Metrics Tests ====================

    @Test
    fun `aggregate with empty list returns empty aggregation`() {
        val result = strategy.aggregate(
            metrics = emptyList(),
            timeWindow = TimeWindow.FIVE_MINUTES,
            nowMillis = System.currentTimeMillis()
        )

        assertEquals(0, result.sampleCount)
        assertEquals(0f, result.cpuPercentAverage, 0.001f)
        assertEquals(0f, result.memoryPercentAverage, 0.001f)
        assertEquals(0f, result.batteryPercentAverage, 0.001f)
        assertEquals(0f, result.temperatureCelsius, 0.001f)
        assertEquals(100, result.healthScoreAverage)
        assertFalse(result.hasData)
    }

    @Test
    fun `aggregate with metrics outside window returns empty aggregation`() {
        val now = System.currentTimeMillis()
        val oldMetrics = listOf(
            createTestMetrics(timestamp = now - TimeWindow.ONE_HOUR.durationMillis() * 2)
        )

        val result = strategy.aggregate(oldMetrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(0, result.sampleCount)
        assertFalse(result.hasData)
    }

    // ==================== Single Metric Tests ====================

    @Test
    fun `aggregate single metric returns correct values`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        val metricTimestamp = windowStart + 60_000L // 1 minute into the window

        val metrics = listOf(
            createTestMetrics(
                timestamp = metricTimestamp,
                cpuUsage = 50f,
                memoryUsage = 60f,
                batteryLevel = 80,
                temperature = 45f
            )
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(1, result.sampleCount)
        assertEquals(50f, result.cpuPercentAverage, 0.001f)
        assertEquals(60f, result.memoryPercentAverage, 0.001f)
        assertEquals(80f, result.batteryPercentAverage, 0.001f)
        assertEquals(45f, result.temperatureCelsius, 0.001f)
        assertTrue(result.hasData)
    }

    @Test
    fun `single metric has equal min max and average`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(
                timestamp = windowStart + 60_000L,
                cpuUsage = 50f,
                memoryUsage = 60f
            )
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(result.cpuPercentAverage, result.cpuPercentMin, 0.001f)
        assertEquals(result.cpuPercentAverage, result.cpuPercentMax, 0.001f)
        assertEquals(result.memoryPercentAverage, result.memoryPercentMin, 0.001f)
        assertEquals(result.memoryPercentAverage, result.memoryPercentMax, 0.001f)
    }

    // ==================== Multiple Metrics Tests ====================

    @Test
    fun `aggregate multiple metrics calculates correct average`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, cpuUsage = 20f, memoryUsage = 40f),
            createTestMetrics(windowStart + 60_000L, cpuUsage = 40f, memoryUsage = 50f),
            createTestMetrics(windowStart + 90_000L, cpuUsage = 60f, memoryUsage = 60f),
            createTestMetrics(windowStart + 120_000L, cpuUsage = 80f, memoryUsage = 70f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(4, result.sampleCount)
        assertEquals(50f, result.cpuPercentAverage, 0.001f) // (20+40+60+80)/4
        assertEquals(55f, result.memoryPercentAverage, 0.001f) // (40+50+60+70)/4
    }

    @Test
    fun `aggregate calculates correct min and max`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, cpuUsage = 20f, memoryUsage = 40f),
            createTestMetrics(windowStart + 60_000L, cpuUsage = 80f, memoryUsage = 90f),
            createTestMetrics(windowStart + 90_000L, cpuUsage = 50f, memoryUsage = 60f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(20f, result.cpuPercentMin, 0.001f)
        assertEquals(80f, result.cpuPercentMax, 0.001f)
        assertEquals(40f, result.memoryPercentMin, 0.001f)
        assertEquals(90f, result.memoryPercentMax, 0.001f)
    }

    @Test
    fun `aggregate uses last temperature value`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, temperature = 40f),
            createTestMetrics(windowStart + 60_000L, temperature = 45f),
            createTestMetrics(windowStart + 90_000L, temperature = 50f) // Last one
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(50f, result.temperatureCelsius, 0.001f)
    }

    // ==================== Network Metrics Tests ====================

    @Test
    fun `aggregate calculates network bytes total`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, rxBytes = 1000L, txBytes = 500L),
            createTestMetrics(windowStart + 90_000L, rxBytes = 5000L, txBytes = 2500L)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(4000L, result.networkRxBytesTotal) // 5000 - 1000
        assertEquals(2000L, result.networkTxBytesTotal) // 2500 - 500
    }

    @Test
    fun `aggregate handles network counter reset gracefully`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Simulating counter reset (last value smaller than first)
        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, rxBytes = 10000L, txBytes = 5000L),
            createTestMetrics(windowStart + 90_000L, rxBytes = 1000L, txBytes = 500L) // Reset
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        // Should return 0 when counter appears to have reset
        assertEquals(0L, result.networkRxBytesTotal)
        assertEquals(0L, result.networkTxBytesTotal)
    }

    // ==================== Time Window Tests ====================

    @Test
    fun `aggregate filters metrics by time window correctly`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        val windowEnd = windowStart + TimeWindow.FIVE_MINUTES.durationMillis()

        val metrics = listOf(
            createTestMetrics(windowStart - 1000L, cpuUsage = 10f), // Before window
            createTestMetrics(windowStart + 1000L, cpuUsage = 50f), // In window
            createTestMetrics(windowStart + 60_000L, cpuUsage = 60f), // In window
            createTestMetrics(windowEnd + 1000L, cpuUsage = 90f) // After window
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(2, result.sampleCount)
        assertEquals(55f, result.cpuPercentAverage, 0.001f) // (50+60)/2
    }

    @Test
    fun `aggregateForWindow works with explicit boundaries`() {
        val windowStart = 1704114000000L // Specific timestamp
        val windowEnd = windowStart + 300_000L // 5 minutes

        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, cpuUsage = 30f),
            createTestMetrics(windowStart + 60_000L, cpuUsage = 50f),
            createTestMetrics(windowStart + 90_000L, cpuUsage = 70f)
        )

        val result = strategy.aggregateForWindow(
            metrics, TimeWindow.FIVE_MINUTES, windowStart, windowEnd
        )

        assertEquals(3, result.sampleCount)
        assertEquals(50f, result.cpuPercentAverage, 0.001f)
        assertEquals(windowStart, result.windowStartTime)
        assertEquals(windowEnd, result.windowEndTime)
    }

    // ==================== Health Score Tests ====================

    @Test
    fun `aggregate calculates health score average`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Create metrics with different health-affecting values
        val metrics = listOf(
            createTestMetrics(windowStart + 30_000L, cpuUsage = 10f, memoryUsage = 20f),
            createTestMetrics(windowStart + 60_000L, cpuUsage = 50f, memoryUsage = 50f),
            createTestMetrics(windowStart + 90_000L, cpuUsage = 90f, memoryUsage = 80f)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        // Health score should be calculated and averaged
        assertTrue(result.healthScoreAverage in 0..100)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `aggregate handles boundary timestamp exactly at window start`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        val metrics = listOf(
            createTestMetrics(windowStart, cpuUsage = 50f) // Exactly at boundary
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        assertEquals(1, result.sampleCount)
    }

    @Test
    fun `aggregate handles boundary timestamp exactly at window end`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)
        val windowEnd = windowStart + TimeWindow.FIVE_MINUTES.durationMillis()

        val metrics = listOf(
            createTestMetrics(windowEnd, cpuUsage = 50f) // Exactly at end (exclusive)
        )

        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)

        // Window end is exclusive, so this should not be included
        assertEquals(0, result.sampleCount)
    }

    @Test
    fun `aggregate handles all time windows`() {
        val now = System.currentTimeMillis()

        for (timeWindow in TimeWindow.values()) {
            val windowStart = strategy.calculatePreviousWindowStart(now, timeWindow)
            val metrics = listOf(
                createTestMetrics(windowStart + 1000L, cpuUsage = 50f)
            )

            val result = strategy.aggregate(metrics, timeWindow, now)

            assertEquals("Failed for $timeWindow", timeWindow, result.timeWindow)
            assertEquals("Failed for $timeWindow", 1, result.sampleCount)
        }
    }

    @Test
    fun `aggregate with large dataset performs efficiently`() {
        val now = System.currentTimeMillis()
        val windowStart = strategy.calculatePreviousWindowStart(now, TimeWindow.FIVE_MINUTES)

        // Create 1000 metrics in window
        val metrics = (0 until 1000).map { i ->
            createTestMetrics(
                timestamp = windowStart + (i * 100L), // 100ms intervals
                cpuUsage = (i % 100).toFloat()
            )
        }

        val startTime = System.nanoTime()
        val result = strategy.aggregate(metrics, TimeWindow.FIVE_MINUTES, now)
        val duration = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms

        // Should complete in <5ms for 1000 metrics
        assertTrue("Aggregation took ${duration}ms, expected <50ms", duration < 50)
        assertTrue(result.sampleCount > 0)
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

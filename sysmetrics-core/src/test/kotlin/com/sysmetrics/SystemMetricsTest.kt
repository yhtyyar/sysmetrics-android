package com.sysmetrics

import com.sysmetrics.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SystemMetrics] and health score calculation.
 */
class SystemMetricsTest {

    @Test
    fun `getHealthScore returns 100 for optimal metrics`() {
        val metrics = createMetrics(
            cpuUsage = 0f,
            memoryUsage = 0f,
            cpuTemp = 0f,
            batteryLevel = 100
        )
        
        val score = metrics.getHealthScore()
        
        assertEquals(100f, score, 0.1f)
    }

    @Test
    fun `getHealthScore returns low score for stressed system`() {
        val metrics = createMetrics(
            cpuUsage = 100f,
            memoryUsage = 100f,
            cpuTemp = 80f,
            batteryLevel = 0
        )
        
        val score = metrics.getHealthScore()
        
        assertTrue("Score should be low for stressed system", score < 20f)
    }

    @Test
    fun `getHealthScore returns moderate score for average usage`() {
        val metrics = createMetrics(
            cpuUsage = 50f,
            memoryUsage = 50f,
            cpuTemp = 40f,
            batteryLevel = 50
        )
        
        val score = metrics.getHealthScore()
        
        assertTrue("Score should be between 40 and 70", score in 40f..70f)
    }

    @Test
    fun `getHealthScore clamps result to 0-100 range`() {
        val metrics = createMetrics(
            cpuUsage = 0f,
            memoryUsage = 0f,
            cpuTemp = 0f,
            batteryLevel = 100
        )
        
        val score = metrics.getHealthScore()
        
        assertTrue("Score should be in range 0-100", score in 0f..100f)
    }

    @Test
    fun `detectIssues returns HIGH_CPU_USAGE when CPU above 85 percent`() {
        val metrics = createMetrics(cpuUsage = 90f)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.HIGH_CPU_USAGE))
    }

    @Test
    fun `detectIssues returns HIGH_MEMORY_USAGE when memory above 85 percent`() {
        val metrics = createMetrics(memoryUsage = 90f)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.HIGH_MEMORY_USAGE))
    }

    @Test
    fun `detectIssues returns HIGH_TEMPERATURE when CPU temp above 70`() {
        val metrics = createMetrics(cpuTemp = 75f)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.HIGH_TEMPERATURE))
    }

    @Test
    fun `detectIssues returns LOW_BATTERY when battery below 15`() {
        val metrics = createMetrics(batteryLevel = 10)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.LOW_BATTERY))
    }

    @Test
    fun `detectIssues returns THERMAL_THROTTLING when throttling is true`() {
        val metrics = createMetrics(thermalThrottling = true)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.THERMAL_THROTTLING))
    }

    @Test
    fun `detectIssues returns LOW_STORAGE when storage usage above 90 percent`() {
        val metrics = createMetrics(storageUsage = 95f)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.LOW_STORAGE))
    }

    @Test
    fun `detectIssues returns POOR_PERFORMANCE when CPU and memory both above 90`() {
        val metrics = createMetrics(cpuUsage = 95f, memoryUsage = 95f)
        
        val issues = metrics.detectIssues()
        
        assertTrue(issues.contains(HealthIssue.POOR_PERFORMANCE))
    }

    @Test
    fun `detectIssues returns empty list for healthy system`() {
        val metrics = createMetrics(
            cpuUsage = 30f,
            memoryUsage = 40f,
            cpuTemp = 35f,
            batteryLevel = 80,
            storageUsage = 50f
        )
        
        val issues = metrics.detectIssues()
        
        assertTrue("Should have no issues", issues.isEmpty())
    }

    @Test
    fun `generateRecommendations returns recommendations for issues`() {
        val issues = listOf(HealthIssue.HIGH_CPU_USAGE, HealthIssue.LOW_BATTERY)
        val metrics = createMetrics()
        
        val recommendations = metrics.generateRecommendations(issues)
        
        assertTrue("Should have recommendations", recommendations.isNotEmpty())
        assertTrue(recommendations.size >= 2)
    }

    @Test
    fun `generateRecommendations returns empty for no issues`() {
        val metrics = createMetrics()
        
        val recommendations = metrics.generateRecommendations(emptyList())
        
        assertTrue(recommendations.isEmpty())
    }

    @Test
    fun `SystemMetrics empty creates valid default instance`() {
        val empty = SystemMetrics.empty()
        
        assertNotNull(empty)
        assertEquals(0f, empty.cpuMetrics.usagePercent, 0.01f)
        assertEquals(0f, empty.memoryMetrics.usagePercent, 0.01f)
    }

    private fun createMetrics(
        cpuUsage: Float = 50f,
        memoryUsage: Float = 50f,
        cpuTemp: Float = 40f,
        batteryLevel: Int = 50,
        storageUsage: Float = 50f,
        thermalThrottling: Boolean = false
    ): SystemMetrics {
        return SystemMetrics(
            cpuMetrics = CpuMetrics(
                usagePercent = cpuUsage,
                physicalCores = 4,
                logicalCores = 8
            ),
            memoryMetrics = MemoryMetrics(
                totalMemoryMB = 4096,
                usedMemoryMB = (4096 * memoryUsage / 100).toLong(),
                freeMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                availableMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                usagePercent = memoryUsage
            ),
            batteryMetrics = BatteryMetrics(
                level = batteryLevel,
                temperature = 25f,
                status = BatteryStatus.DISCHARGING,
                health = BatteryHealth.GOOD,
                plugged = false
            ),
            thermalMetrics = ThermalMetrics(
                cpuTemperature = cpuTemp,
                batteryTemperature = 25f,
                thermalThrottling = thermalThrottling
            ),
            storageMetrics = StorageMetrics(
                totalStorageMB = 64000,
                freeStorageMB = (64000 * (100 - storageUsage) / 100).toLong(),
                usedStorageMB = (64000 * storageUsage / 100).toLong(),
                usagePercent = storageUsage
            ),
            networkMetrics = NetworkMetrics(
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSecond = 0L,
                txBytesPerSecond = 0L,
                isConnected = true,
                connectionType = NetworkType.WIFI
            ),
            timestamp = System.currentTimeMillis(),
            uptime = 3600000L
        )
    }
}

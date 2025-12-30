package com.sysmetrics

import com.sysmetrics.data.mapper.MetricsMapper
import com.sysmetrics.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [MetricsMapper].
 */
class MetricsMapperTest {

    @Test
    fun `calculateHealthScore returns 100 for optimal metrics`() {
        val metrics = createMetrics(
            cpuUsage = 0f,
            memoryUsage = 0f,
            cpuTemp = 0f,
            batteryLevel = 100
        )
        
        val score = MetricsMapper.calculateHealthScore(metrics)
        
        assertEquals(100f, score, 0.1f)
    }

    @Test
    fun `calculateHealthScore returns low score for stressed system`() {
        val metrics = createMetrics(
            cpuUsage = 100f,
            memoryUsage = 100f,
            cpuTemp = 80f,
            batteryLevel = 0
        )
        
        val score = MetricsMapper.calculateHealthScore(metrics)
        
        assertTrue("Score should be very low", score < 10f)
    }

    @Test
    fun `getStatusFromScore returns EXCELLENT for score above 80`() {
        assertEquals(HealthStatus.EXCELLENT, MetricsMapper.getStatusFromScore(80f))
        assertEquals(HealthStatus.EXCELLENT, MetricsMapper.getStatusFromScore(100f))
        assertEquals(HealthStatus.EXCELLENT, MetricsMapper.getStatusFromScore(90f))
    }

    @Test
    fun `getStatusFromScore returns GOOD for score 60-79`() {
        assertEquals(HealthStatus.GOOD, MetricsMapper.getStatusFromScore(60f))
        assertEquals(HealthStatus.GOOD, MetricsMapper.getStatusFromScore(79f))
        assertEquals(HealthStatus.GOOD, MetricsMapper.getStatusFromScore(70f))
    }

    @Test
    fun `getStatusFromScore returns WARNING for score 40-59`() {
        assertEquals(HealthStatus.WARNING, MetricsMapper.getStatusFromScore(40f))
        assertEquals(HealthStatus.WARNING, MetricsMapper.getStatusFromScore(59f))
        assertEquals(HealthStatus.WARNING, MetricsMapper.getStatusFromScore(50f))
    }

    @Test
    fun `getStatusFromScore returns CRITICAL for score below 40`() {
        assertEquals(HealthStatus.CRITICAL, MetricsMapper.getStatusFromScore(39f))
        assertEquals(HealthStatus.CRITICAL, MetricsMapper.getStatusFromScore(0f))
        assertEquals(HealthStatus.CRITICAL, MetricsMapper.getStatusFromScore(20f))
    }

    @Test
    fun `detectIssues identifies HIGH_CPU_USAGE`() {
        val metrics = createMetrics(cpuUsage = 90f)
        
        val issues = MetricsMapper.detectIssues(metrics)
        
        assertTrue(issues.contains(HealthIssue.HIGH_CPU_USAGE))
    }

    @Test
    fun `detectIssues identifies HIGH_MEMORY_USAGE`() {
        val metrics = createMetrics(memoryUsage = 90f)
        
        val issues = MetricsMapper.detectIssues(metrics)
        
        assertTrue(issues.contains(HealthIssue.HIGH_MEMORY_USAGE))
    }

    @Test
    fun `detectIssues identifies multiple issues`() {
        val metrics = createMetrics(
            cpuUsage = 95f,
            memoryUsage = 95f,
            cpuTemp = 75f,
            batteryLevel = 10
        )
        
        val issues = MetricsMapper.detectIssues(metrics)
        
        assertTrue(issues.contains(HealthIssue.HIGH_CPU_USAGE))
        assertTrue(issues.contains(HealthIssue.HIGH_MEMORY_USAGE))
        assertTrue(issues.contains(HealthIssue.HIGH_TEMPERATURE))
        assertTrue(issues.contains(HealthIssue.LOW_BATTERY))
        assertTrue(issues.contains(HealthIssue.POOR_PERFORMANCE))
    }

    @Test
    fun `generateRecommendations creates actionable items`() {
        val issues = listOf(HealthIssue.HIGH_CPU_USAGE)
        
        val recommendations = MetricsMapper.generateRecommendations(issues)
        
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.contains("CPU", ignoreCase = true) })
    }

    @Test
    fun `toHealthScore creates complete HealthScore`() {
        val metrics = createMetrics(cpuUsage = 50f, memoryUsage = 50f)
        
        val healthScore = MetricsMapper.toHealthScore(metrics)
        
        assertNotNull(healthScore)
        assertTrue(healthScore.score in 0f..100f)
        assertNotNull(healthScore.status)
        assertNotNull(healthScore.issues)
        assertNotNull(healthScore.recommendations)
        assertTrue(healthScore.timestamp > 0)
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

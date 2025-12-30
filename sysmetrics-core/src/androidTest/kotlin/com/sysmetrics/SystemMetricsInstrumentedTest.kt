package com.sysmetrics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysmetrics.domain.model.HealthStatus
import com.sysmetrics.domain.model.SystemMetrics
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [SysMetrics] on real Android devices/emulators.
 *
 * These tests verify:
 * - Library initialization on real device
 * - Metrics collection from actual /proc filesystem
 * - Battery metrics from Android system services
 * - Storage metrics from real storage
 * - Network metrics from TrafficStats
 * - Health score calculation with real data
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SystemMetricsInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SysMetrics.initialize(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            SysMetrics.destroy()
        }
    }

    @Test
    fun testLibraryInitialization() {
        assertTrue("SysMetrics should be initialized", SysMetrics.isInitialized())
    }

    @Test
    fun testGetCurrentMetrics() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        
        assertTrue("Should successfully get metrics", result.isSuccess)
        
        val metrics = result.getOrNull()
        assertNotNull("Metrics should not be null", metrics)
        
        metrics?.let {
            assertTrue("Timestamp should be positive", it.timestamp > 0)
            assertTrue("Uptime should be positive", it.uptime >= 0)
        }
    }

    @Test
    fun testCpuMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.cpuMetrics?.let { cpu ->
            assertTrue("CPU usage should be 0-100", cpu.usagePercent in 0f..100f)
            assertTrue("Physical cores should be positive", cpu.physicalCores > 0)
            assertTrue("Logical cores should be >= physical", cpu.logicalCores >= cpu.physicalCores)
        }
    }

    @Test
    fun testMemoryMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.memoryMetrics?.let { memory ->
            assertTrue("Total memory should be positive", memory.totalMemoryMB > 0)
            assertTrue("Used memory should be non-negative", memory.usedMemoryMB >= 0)
            assertTrue("Free memory should be non-negative", memory.freeMemoryMB >= 0)
            assertTrue("Available memory should be non-negative", memory.availableMemoryMB >= 0)
            assertTrue("Usage percent should be 0-100", memory.usagePercent in 0f..100f)
        }
    }

    @Test
    fun testBatteryMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.batteryMetrics?.let { battery ->
            assertTrue("Battery level should be 0-100", battery.level in 0..100)
            assertNotNull("Battery status should not be null", battery.status)
            assertNotNull("Battery health should not be null", battery.health)
        }
    }

    @Test
    fun testStorageMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.storageMetrics?.let { storage ->
            assertTrue("Total storage should be positive", storage.totalStorageMB > 0)
            assertTrue("Free storage should be non-negative", storage.freeStorageMB >= 0)
            assertTrue("Used storage should be non-negative", storage.usedStorageMB >= 0)
            assertTrue("Usage percent should be 0-100", storage.usagePercent in 0f..100f)
        }
    }

    @Test
    fun testNetworkMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.networkMetrics?.let { network ->
            assertTrue("RX bytes should be non-negative", network.rxBytes >= 0)
            assertTrue("TX bytes should be non-negative", network.txBytes >= 0)
            assertNotNull("Connection type should not be null", network.connectionType)
        }
    }

    @Test
    fun testThermalMetricsCollection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.thermalMetrics?.let { thermal ->
            // Temperature can be 0 on some devices/emulators
            assertTrue("CPU temp should be reasonable", thermal.cpuTemperature >= -40f && thermal.cpuTemperature <= 150f)
        }
    }

    @Test
    fun testHealthScoreCalculation() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        metrics?.let {
            val healthScore = it.getHealthScore()
            assertTrue("Health score should be 0-100", healthScore in 0f..100f)
        }
    }

    @Test
    fun testGetCurrentHealthScore() = runBlocking {
        val result = SysMetrics.getCurrentHealthScore()
        
        assertTrue("Should successfully get health score", result.isSuccess)
        
        result.getOrNull()?.let { healthScore ->
            assertTrue("Score should be 0-100", healthScore.score in 0f..100f)
            assertNotNull("Status should not be null", healthScore.status)
            assertNotNull("Issues list should not be null", healthScore.issues)
            assertNotNull("Recommendations should not be null", healthScore.recommendations)
            assertTrue("Timestamp should be positive", healthScore.timestamp > 0)
        }
    }

    @Test
    fun testMultipleConsecutiveCollections() = runBlocking {
        val metrics1 = SysMetrics.getCurrentMetrics().getOrNull()
        val metrics2 = SysMetrics.getCurrentMetrics().getOrNull()
        val metrics3 = SysMetrics.getCurrentMetrics().getOrNull()
        
        assertNotNull(metrics1)
        assertNotNull(metrics2)
        assertNotNull(metrics3)
        
        // Timestamps should be non-decreasing
        assertTrue("Timestamps should be ordered", 
            metrics1!!.timestamp <= metrics2!!.timestamp &&
            metrics2.timestamp <= metrics3!!.timestamp
        )
    }

    @Test
    fun testMetricsHistory() = runBlocking {
        // Collect some metrics to populate history
        repeat(3) {
            SysMetrics.getCurrentMetrics()
        }
        
        val result = SysMetrics.getMetricsHistory(count = 5)
        
        assertTrue("Should successfully get history", result.isSuccess)
        
        result.getOrNull()?.let { history ->
            assertTrue("History should not be empty after collection", history.isNotEmpty())
            
            // Verify chronological order
            for (i in 0 until history.size - 1) {
                assertTrue(
                    "History should be chronologically ordered",
                    history[i].timestamp <= history[i + 1].timestamp
                )
            }
        }
    }

    @Test
    fun testIssueDetection() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        val issues = metrics!!.detectIssues()
        
        // Issues list should be valid (may or may not have issues)
        assertNotNull("Issues list should not be null", issues)
    }

    @Test
    fun testRecommendationsGeneration() = runBlocking {
        val result = SysMetrics.getCurrentMetrics()
        val metrics = result.getOrNull()
        
        assertNotNull(metrics)
        val issues = metrics!!.detectIssues()
        val recommendations = metrics.generateRecommendations(issues)
        
        assertNotNull("Recommendations should not be null", recommendations)
        
        // If there are issues, there should be recommendations
        if (issues.isNotEmpty()) {
            assertTrue("Should have recommendations for issues", recommendations.isNotEmpty())
        }
    }

    @Test
    fun testSystemMetricsEmpty() {
        val empty = SystemMetrics.empty()
        
        assertNotNull(empty)
        assertEquals(0f, empty.cpuMetrics.usagePercent, 0.01f)
        assertEquals(0f, empty.memoryMetrics.usagePercent, 0.01f)
        assertTrue(empty.timestamp > 0)
    }
}

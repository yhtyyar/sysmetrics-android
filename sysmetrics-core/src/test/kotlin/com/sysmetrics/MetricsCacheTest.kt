package com.sysmetrics

import com.sysmetrics.data.cache.MetricsCache
import com.sysmetrics.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MetricsCache] TTL behavior.
 */
class MetricsCacheTest {

    private lateinit var cache: MetricsCache

    @Before
    fun setup() {
        cache = MetricsCache(ttlMs = 100L) // Short TTL for testing
    }

    @Test
    fun `getIfValid returns null when cache is empty`() = runBlocking {
        val result = cache.getIfValid()
        
        assertNull(result)
    }

    @Test
    fun `getIfValid returns cached value within TTL`() = runBlocking {
        val metrics = createTestMetrics()
        cache.put(metrics)
        
        val result = cache.getIfValid()
        
        assertNotNull(result)
        assertEquals(metrics.timestamp, result?.timestamp)
    }

    @Test
    fun `getIfValid returns null after TTL expires`() = runBlocking {
        val metrics = createTestMetrics()
        cache.put(metrics)
        
        delay(150L) // Wait for TTL to expire
        
        val result = cache.getIfValid()
        
        assertNull(result)
    }

    @Test
    fun `put updates cached value`() = runBlocking {
        val metrics1 = createTestMetrics(timestamp = 1000L)
        val metrics2 = createTestMetrics(timestamp = 2000L)
        
        cache.put(metrics1)
        cache.put(metrics2)
        
        val result = cache.getIfValid()
        
        assertEquals(2000L, result?.timestamp)
    }

    @Test
    fun `clear removes cached data`() = runBlocking {
        val metrics = createTestMetrics()
        cache.put(metrics)
        
        cache.clear()
        
        val result = cache.getIfValid()
        assertNull(result)
    }

    @Test
    fun `isValid returns false when empty`() = runBlocking {
        val result = cache.isValid()
        
        assertFalse(result)
    }

    @Test
    fun `isValid returns true when cache has valid data`() = runBlocking {
        cache.put(createTestMetrics())
        
        val result = cache.isValid()
        
        assertTrue(result)
    }

    @Test
    fun `isValid returns false after TTL expires`() = runBlocking {
        cache.put(createTestMetrics())
        
        delay(150L)
        
        val result = cache.isValid()
        
        assertFalse(result)
    }

    @Test
    fun `getCacheAge returns negative when empty`() = runBlocking {
        val age = cache.getCacheAge()
        
        assertEquals(-1L, age)
    }

    @Test
    fun `getCacheAge returns positive value after put`() = runBlocking {
        cache.put(createTestMetrics())
        
        delay(50L)
        
        val age = cache.getCacheAge()
        
        assertTrue("Age should be positive", age >= 0)
        assertTrue("Age should be around 50ms", age in 40L..100L)
    }

    @Test
    fun `default TTL is 500ms`() {
        val defaultCache = MetricsCache()
        
        assertEquals(500L, MetricsCache.DEFAULT_TTL_MS)
    }

    @Test
    fun `cache is thread-safe with concurrent access`() = runBlocking {
        val iterations = 100
        val jobs = (1..iterations).map { i ->
            kotlinx.coroutines.async {
                val metrics = createTestMetrics(timestamp = i.toLong())
                cache.put(metrics)
                cache.getIfValid()
            }
        }
        
        jobs.forEach { it.await() }
        
        // Should not throw any exceptions
        val final = cache.getIfValid()
        assertNotNull(final)
    }

    private fun createTestMetrics(timestamp: Long = System.currentTimeMillis()): SystemMetrics {
        return SystemMetrics(
            cpuMetrics = CpuMetrics(
                usagePercent = 50f,
                physicalCores = 4,
                logicalCores = 8
            ),
            memoryMetrics = MemoryMetrics(
                totalMemoryMB = 4096,
                usedMemoryMB = 2048,
                freeMemoryMB = 2048,
                availableMemoryMB = 2048,
                usagePercent = 50f
            ),
            batteryMetrics = BatteryMetrics(
                level = 50,
                temperature = 25f,
                status = BatteryStatus.DISCHARGING,
                health = BatteryHealth.GOOD,
                plugged = false
            ),
            thermalMetrics = ThermalMetrics(
                cpuTemperature = 40f,
                batteryTemperature = 25f
            ),
            storageMetrics = StorageMetrics(
                totalStorageMB = 64000,
                freeStorageMB = 32000,
                usedStorageMB = 32000,
                usagePercent = 50f
            ),
            networkMetrics = NetworkMetrics(
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSecond = 0L,
                txBytesPerSecond = 0L,
                isConnected = true,
                connectionType = NetworkType.WIFI
            ),
            timestamp = timestamp,
            uptime = 3600000L
        )
    }
}

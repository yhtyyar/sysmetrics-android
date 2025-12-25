package com.sysmetrics

import com.sysmetrics.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for domain model data classes.
 */
class DataClassesTest {

    // CpuMetrics Tests
    @Test
    fun `CpuMetrics creation with valid values succeeds`() {
        val cpu = CpuMetrics(
            usagePercent = 50f,
            physicalCores = 4,
            logicalCores = 8,
            maxFrequencyKHz = 2400000L,
            currentFrequencyKHz = 1800000L,
            coreFrequencies = listOf(1800000L, 1900000L, 1800000L, 1850000L)
        )
        
        assertEquals(50f, cpu.usagePercent, 0.01f)
        assertEquals(4, cpu.physicalCores)
        assertEquals(8, cpu.logicalCores)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CpuMetrics throws for negative usage`() {
        CpuMetrics(usagePercent = -1f, physicalCores = 4, logicalCores = 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CpuMetrics throws for usage above 100`() {
        CpuMetrics(usagePercent = 101f, physicalCores = 4, logicalCores = 8)
    }

    @Test
    fun `CpuMetrics empty creates valid default`() {
        val empty = CpuMetrics.empty()
        assertEquals(0f, empty.usagePercent, 0.01f)
        assertEquals(1, empty.physicalCores)
        assertEquals(1, empty.logicalCores)
    }

    // MemoryMetrics Tests
    @Test
    fun `MemoryMetrics creation with valid values succeeds`() {
        val memory = MemoryMetrics(
            totalMemoryMB = 4096,
            usedMemoryMB = 2048,
            freeMemoryMB = 1024,
            availableMemoryMB = 2048,
            usagePercent = 50f,
            buffersMB = 256,
            cachedMB = 512
        )
        
        assertEquals(4096L, memory.totalMemoryMB)
        assertEquals(50f, memory.usagePercent, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MemoryMetrics throws for negative total`() {
        MemoryMetrics(
            totalMemoryMB = -1,
            usedMemoryMB = 0,
            freeMemoryMB = 0,
            availableMemoryMB = 0,
            usagePercent = 0f
        )
    }

    @Test
    fun `MemoryMetrics empty creates valid default`() {
        val empty = MemoryMetrics.empty()
        assertEquals(0L, empty.totalMemoryMB)
        assertEquals(0f, empty.usagePercent, 0.01f)
    }

    // BatteryMetrics Tests
    @Test
    fun `BatteryMetrics creation with valid values succeeds`() {
        val battery = BatteryMetrics(
            level = 75,
            temperature = 28.5f,
            status = BatteryStatus.CHARGING,
            health = BatteryHealth.GOOD,
            plugged = true,
            chargingSpeed = 2000
        )
        
        assertEquals(75, battery.level)
        assertEquals(28.5f, battery.temperature, 0.01f)
        assertTrue(battery.plugged)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BatteryMetrics throws for level below 0`() {
        BatteryMetrics(
            level = -1,
            temperature = 25f,
            status = BatteryStatus.UNKNOWN,
            health = BatteryHealth.UNKNOWN,
            plugged = false
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `BatteryMetrics throws for level above 100`() {
        BatteryMetrics(
            level = 101,
            temperature = 25f,
            status = BatteryStatus.UNKNOWN,
            health = BatteryHealth.UNKNOWN,
            plugged = false
        )
    }

    @Test
    fun `BatteryMetrics empty creates valid default`() {
        val empty = BatteryMetrics.empty()
        assertEquals(0, empty.level)
        assertEquals(BatteryStatus.UNKNOWN, empty.status)
        assertEquals(BatteryHealth.UNKNOWN, empty.health)
    }

    // ThermalMetrics Tests
    @Test
    fun `ThermalMetrics creation with valid values succeeds`() {
        val thermal = ThermalMetrics(
            cpuTemperature = 45.5f,
            batteryTemperature = 32.0f,
            otherTemperatures = mapOf("gpu" to 50f, "skin" to 35f),
            thermalThrottling = false
        )
        
        assertEquals(45.5f, thermal.cpuTemperature, 0.01f)
        assertEquals(2, thermal.otherTemperatures.size)
        assertFalse(thermal.thermalThrottling)
    }

    @Test
    fun `ThermalMetrics empty creates valid default`() {
        val empty = ThermalMetrics.empty()
        assertEquals(0f, empty.cpuTemperature, 0.01f)
        assertTrue(empty.otherTemperatures.isEmpty())
        assertFalse(empty.thermalThrottling)
    }

    // StorageMetrics Tests
    @Test
    fun `StorageMetrics creation with valid values succeeds`() {
        val storage = StorageMetrics(
            totalStorageMB = 64000,
            freeStorageMB = 32000,
            usedStorageMB = 32000,
            usagePercent = 50f
        )
        
        assertEquals(64000L, storage.totalStorageMB)
        assertEquals(50f, storage.usagePercent, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `StorageMetrics throws for negative values`() {
        StorageMetrics(
            totalStorageMB = -1,
            freeStorageMB = 0,
            usedStorageMB = 0,
            usagePercent = 0f
        )
    }

    @Test
    fun `StorageMetrics empty creates valid default`() {
        val empty = StorageMetrics.empty()
        assertEquals(0L, empty.totalStorageMB)
        assertEquals(0f, empty.usagePercent, 0.01f)
    }

    // Enum Tests
    @Test
    fun `HealthStatus enum has all expected values`() {
        val values = HealthStatus.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(HealthStatus.EXCELLENT))
        assertTrue(values.contains(HealthStatus.GOOD))
        assertTrue(values.contains(HealthStatus.WARNING))
        assertTrue(values.contains(HealthStatus.CRITICAL))
    }

    @Test
    fun `BatteryStatus enum has all expected values`() {
        val values = BatteryStatus.values()
        assertEquals(5, values.size)
        assertTrue(values.contains(BatteryStatus.UNKNOWN))
        assertTrue(values.contains(BatteryStatus.CHARGING))
        assertTrue(values.contains(BatteryStatus.DISCHARGING))
        assertTrue(values.contains(BatteryStatus.NOT_CHARGING))
        assertTrue(values.contains(BatteryStatus.FULL))
    }

    @Test
    fun `BatteryHealth enum has all expected values`() {
        val values = BatteryHealth.values()
        assertEquals(7, values.size)
        assertTrue(values.contains(BatteryHealth.UNKNOWN))
        assertTrue(values.contains(BatteryHealth.GOOD))
        assertTrue(values.contains(BatteryHealth.OVERHEAT))
        assertTrue(values.contains(BatteryHealth.DEAD))
        assertTrue(values.contains(BatteryHealth.OVER_VOLTAGE))
        assertTrue(values.contains(BatteryHealth.UNSPECIFIED_FAILURE))
        assertTrue(values.contains(BatteryHealth.COLD))
    }

    @Test
    fun `HealthIssue enum has all expected values`() {
        val values = HealthIssue.values()
        assertEquals(7, values.size)
        assertTrue(values.contains(HealthIssue.HIGH_CPU_USAGE))
        assertTrue(values.contains(HealthIssue.HIGH_MEMORY_USAGE))
        assertTrue(values.contains(HealthIssue.HIGH_TEMPERATURE))
        assertTrue(values.contains(HealthIssue.LOW_BATTERY))
        assertTrue(values.contains(HealthIssue.THERMAL_THROTTLING))
        assertTrue(values.contains(HealthIssue.LOW_STORAGE))
        assertTrue(values.contains(HealthIssue.POOR_PERFORMANCE))
    }
}

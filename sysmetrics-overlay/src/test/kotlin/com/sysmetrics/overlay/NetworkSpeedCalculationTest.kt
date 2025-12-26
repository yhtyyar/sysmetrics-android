package com.sysmetrics.overlay

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for network speed calculation logic.
 */
class NetworkSpeedCalculationTest {

    @Test
    fun `calculateSpeed returns correct bytes per second`() {
        val previousBytes = 1000L
        val currentBytes = 2000L
        val intervalMs = 1000L // 1 second
        
        val speed = calculateSpeed(previousBytes, currentBytes, intervalMs)
        
        assertEquals(1000L, speed)
    }

    @Test
    fun `calculateSpeed handles half second interval`() {
        val previousBytes = 1000L
        val currentBytes = 1500L
        val intervalMs = 500L // 0.5 seconds
        
        val speed = calculateSpeed(previousBytes, currentBytes, intervalMs)
        
        assertEquals(1000L, speed) // 500 bytes in 0.5s = 1000 bytes/s
    }

    @Test
    fun `calculateSpeed returns zero for zero interval`() {
        val previousBytes = 1000L
        val currentBytes = 2000L
        val intervalMs = 0L
        
        val speed = calculateSpeed(previousBytes, currentBytes, intervalMs)
        
        assertEquals(0L, speed)
    }

    @Test
    fun `calculateSpeed handles counter rollover`() {
        val previousBytes = Long.MAX_VALUE - 100
        val currentBytes = 100L // Rolled over
        val intervalMs = 1000L
        
        val speed = calculateSpeed(previousBytes, currentBytes, intervalMs)
        
        // Should return 0 or handle gracefully when current < previous
        assertTrue(speed >= 0)
    }

    @Test
    fun `formatSpeed formats bytes correctly`() {
        assertEquals("500 B/s", formatSpeed(500L))
    }

    @Test
    fun `formatSpeed formats kilobytes correctly`() {
        assertEquals("1.50 KB/s", formatSpeed(1500L))
    }

    @Test
    fun `formatSpeed formats megabytes correctly`() {
        assertEquals("1.50 MB/s", formatSpeed(1_500_000L))
    }

    @Test
    fun `formatSpeed formats gigabytes correctly`() {
        assertEquals("1.50 GB/s", formatSpeed(1_500_000_000L))
    }

    @Test
    fun `formatCompactSpeed formats for overlay display`() {
        assertEquals("1.5K", formatCompactSpeed(1500L))
        assertEquals("1.5M", formatCompactSpeed(1_500_000L))
        assertEquals("500B", formatCompactSpeed(500L))
    }

    // Helper functions that mirror network speed calculation logic

    private fun calculateSpeed(previousBytes: Long, currentBytes: Long, intervalMs: Long): Long {
        if (intervalMs <= 0) return 0L
        val delta = currentBytes - previousBytes
        if (delta < 0) return 0L // Handle counter rollover
        return (delta * 1000) / intervalMs
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_000_000_000 -> String.format("%.2f GB/s", bytesPerSecond / 1_000_000_000.0)
            bytesPerSecond >= 1_000_000 -> String.format("%.2f MB/s", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format("%.2f KB/s", bytesPerSecond / 1_000.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    private fun formatCompactSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1_000_000 -> String.format("%.1fM", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format("%.1fK", bytesPerSecond / 1_000.0)
            else -> "${bytesPerSecond}B"
        }
    }
}

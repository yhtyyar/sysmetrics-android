package com.sysmetrics.overlay.fps

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for FPS calculation logic.
 */
class FpsCalculationTest {

    @Test
    fun `calculateFps returns correct value for 60 frames per second`() {
        val frames = 60
        val elapsedNanos = 1_000_000_000L // 1 second
        
        val fps = calculateFps(frames, elapsedNanos)
        
        assertEquals(60, fps)
    }

    @Test
    fun `calculateFps returns correct value for 30 frames in half second`() {
        val frames = 30
        val elapsedNanos = 500_000_000L // 0.5 seconds
        
        val fps = calculateFps(frames, elapsedNanos)
        
        assertEquals(60, fps)
    }

    @Test
    fun `calculateFps returns zero for zero elapsed time`() {
        val frames = 60
        val elapsedNanos = 0L
        
        val fps = calculateFps(frames, elapsedNanos)
        
        assertEquals(0, fps)
    }

    @Test
    fun `calculateFps clamps to max fps`() {
        val frames = 500
        val elapsedNanos = 1_000_000_000L
        
        val fps = calculateFps(frames, elapsedNanos, maxFps = 240)
        
        assertEquals(240, fps)
    }

    @Test
    fun `calculateEma returns initial value on first call`() {
        val currentFps = 60f
        val previousEma = 0f
        
        val ema = calculateEma(currentFps, previousEma, alpha = 0.3f)
        
        assertEquals(60f, ema, 0.01f)
    }

    @Test
    fun `calculateEma smooths value correctly`() {
        val currentFps = 30f
        val previousEma = 60f
        val alpha = 0.3f
        
        val ema = calculateEma(currentFps, previousEma, alpha)
        
        // EMA = alpha * current + (1 - alpha) * previous
        // EMA = 0.3 * 30 + 0.7 * 60 = 9 + 42 = 51
        assertEquals(51f, ema, 0.01f)
    }

    @Test
    fun `isJankFrame detects jank for slow frames`() {
        val frameDurationMs = 20f // > 16.67ms
        
        assertTrue(isJankFrame(frameDurationMs, threshold = 16.67f))
    }

    @Test
    fun `isJankFrame does not detect jank for fast frames`() {
        val frameDurationMs = 15f // < 16.67ms
        
        assertFalse(isJankFrame(frameDurationMs, threshold = 16.67f))
    }

    @Test
    fun `frameDurationToFps converts correctly`() {
        val durationMs = 16.67f
        
        val fps = frameDurationToFps(durationMs)
        
        assertEquals(60, fps, 1) // Allow 1 fps margin
    }

    @Test
    fun `fpsToFrameDuration converts correctly`() {
        val fps = 60
        
        val durationMs = fpsToFrameDuration(fps)
        
        assertEquals(16.67f, durationMs, 0.1f)
    }

    // Helper functions that mirror the logic in FrameRateMonitor
    
    private fun calculateFps(frames: Int, elapsedNanos: Long, maxFps: Int = 240): Int {
        if (elapsedNanos <= 0) return 0
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        return (frames / elapsedSeconds).toInt().coerceIn(0, maxFps)
    }

    private fun calculateEma(currentFps: Float, previousEma: Float, alpha: Float): Float {
        return if (previousEma == 0f) {
            currentFps
        } else {
            alpha * currentFps + (1 - alpha) * previousEma
        }
    }

    private fun isJankFrame(frameDurationMs: Float, threshold: Float): Boolean {
        return frameDurationMs > threshold
    }

    private fun frameDurationToFps(durationMs: Float): Int {
        return if (durationMs > 0) (1000f / durationMs).toInt() else 0
    }

    private fun fpsToFrameDuration(fps: Int): Float {
        return if (fps > 0) 1000f / fps else 0f
    }
}

package com.sysmetrics.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayConfigTest {

    @Test
    fun `default config has sensible defaults`() {
        val config = OverlayConfig()
        
        assertEquals(500L, config.updateIntervalMs)
        assertFalse(config.startExpanded)
        assertTrue(config.showNetworkSpeed)
        assertTrue(config.showFps)
        assertTrue(config.draggable)
        assertEquals(11f, config.textSizeSp, 0.01f)
        assertEquals(1.0f, config.opacity, 0.01f)
        assertFalse(config.enableInRelease)
    }

    @Test
    fun `forDebug creates debug-appropriate config`() {
        val config = OverlayConfig.forDebug()
        
        assertEquals(500L, config.updateIntervalMs)
        assertFalse(config.enableInRelease)
    }

    @Test
    fun `forReleaseTesting creates release-safe config`() {
        val config = OverlayConfig.forReleaseTesting()
        
        assertEquals(1000L, config.updateIntervalMs)
        assertTrue(config.enableInRelease)
        assertEquals(0.8f, config.opacity, 0.01f)
    }

    @Test
    fun `fpsOnly hides network metrics`() {
        val config = OverlayConfig.fpsOnly()
        
        assertFalse(config.showNetworkSpeed)
        assertTrue(config.showFps)
    }

    @Test
    fun `compact uses smaller text size`() {
        val config = OverlayConfig.compact()
        
        assertEquals(10f, config.textSizeSp, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects update interval below minimum`() {
        OverlayConfig(updateIntervalMs = 50L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects text size below minimum`() {
        OverlayConfig(textSizeSp = 5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects text size above maximum`() {
        OverlayConfig(textSizeSp = 30f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects opacity below zero`() {
        OverlayConfig(opacity = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects opacity above one`() {
        OverlayConfig(opacity = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid initial position X`() {
        OverlayConfig(initialPositionX = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid initial position Y`() {
        OverlayConfig(initialPositionY = -0.1f)
    }

    @Test
    fun `accepts valid custom config`() {
        val config = OverlayConfig(
            updateIntervalMs = 1000L,
            startExpanded = true,
            showNetworkSpeed = false,
            showFps = true,
            draggable = false,
            textSizeSp = 14f,
            opacity = 0.9f,
            initialPositionX = 0.5f,
            initialPositionY = 0.5f,
            enableInRelease = true
        )
        
        assertEquals(1000L, config.updateIntervalMs)
        assertTrue(config.startExpanded)
        assertFalse(config.showNetworkSpeed)
        assertTrue(config.showFps)
        assertFalse(config.draggable)
        assertEquals(14f, config.textSizeSp, 0.01f)
        assertEquals(0.9f, config.opacity, 0.01f)
        assertEquals(0.5f, config.initialPositionX, 0.01f)
        assertEquals(0.5f, config.initialPositionY, 0.01f)
        assertTrue(config.enableInRelease)
    }
}

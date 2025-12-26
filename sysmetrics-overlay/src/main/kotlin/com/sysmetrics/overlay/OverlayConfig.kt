package com.sysmetrics.overlay

import android.graphics.Color

/**
 * Configuration for the SysMetrics overlay.
 *
 * All options are opt-in with sensible defaults for debug usage.
 *
 * @property updateIntervalMs Interval between metric updates (min 100ms)
 * @property startExpanded Whether to start in expanded mode
 * @property showNetworkSpeed Whether to show network speed metrics
 * @property showFps Whether to show FPS metrics
 * @property draggable Whether the overlay can be dragged
 * @property textSizeSp Text size in SP units
 * @property backgroundColor Background color with alpha
 * @property textColor Text color
 * @property warningColor Color for warning states
 * @property criticalColor Color for critical states
 * @property initialPositionX Initial X position (0.0 = left, 1.0 = right)
 * @property initialPositionY Initial Y position (0.0 = top, 1.0 = bottom)
 * @property opacity Overall opacity (0.0 - 1.0)
 * @property enableInRelease Allow overlay in release builds (default false for safety)
 */
public data class OverlayConfig(
    val updateIntervalMs: Long = 500L,
    val startExpanded: Boolean = false,
    val showNetworkSpeed: Boolean = true,
    val showFps: Boolean = true,
    val draggable: Boolean = true,
    val textSizeSp: Float = 11f,
    val backgroundColor: Int = Color.argb(200, 0, 0, 0),
    val textColor: Int = Color.WHITE,
    val warningColor: Int = Color.rgb(255, 193, 7),
    val criticalColor: Int = Color.rgb(244, 67, 54),
    val goodColor: Int = Color.rgb(76, 175, 80),
    val initialPositionX: Float = 0f,
    val initialPositionY: Float = 0f,
    val opacity: Float = 1.0f,
    val enableInRelease: Boolean = false
) {
    init {
        require(updateIntervalMs >= MIN_UPDATE_INTERVAL_MS) {
            "updateIntervalMs must be at least $MIN_UPDATE_INTERVAL_MS ms"
        }
        require(textSizeSp in 8f..24f) { "textSizeSp must be between 8 and 24" }
        require(opacity in 0f..1f) { "opacity must be between 0.0 and 1.0" }
        require(initialPositionX in 0f..1f) { "initialPositionX must be between 0.0 and 1.0" }
        require(initialPositionY in 0f..1f) { "initialPositionY must be between 0.0 and 1.0" }
    }

    public companion object {
        /** Minimum allowed update interval to prevent performance issues */
        public const val MIN_UPDATE_INTERVAL_MS: Long = 100L

        /** Default configuration for debug builds */
        public fun forDebug(): OverlayConfig = OverlayConfig(
            updateIntervalMs = 500L,
            enableInRelease = false
        )

        /** Configuration allowing release builds */
        public fun forReleaseTesting(): OverlayConfig = OverlayConfig(
            updateIntervalMs = 1000L,
            enableInRelease = true,
            opacity = 0.8f
        )

        /** Minimal overlay showing only FPS */
        public fun fpsOnly(): OverlayConfig = OverlayConfig(
            showNetworkSpeed = false,
            showFps = true,
            startExpanded = false
        )

        /** Compact configuration for smaller screens */
        public fun compact(): OverlayConfig = OverlayConfig(
            textSizeSp = 10f,
            startExpanded = false,
            opacity = 0.9f
        )
    }
}

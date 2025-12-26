package com.sysmetrics.overlay

/**
 * Handle for controlling an attached overlay instance.
 *
 * Use this to detach, update configuration, or toggle visibility.
 *
 * ## Usage
 *
 * ```kotlin
 * val handle = SysMetricsOverlay.attach(activity)
 *
 * // Later, when done:
 * handle.detach()
 * ```
 */
public interface OverlayHandle {
    /**
     * Returns true if the overlay is currently attached and visible.
     */
    public val isAttached: Boolean

    /**
     * Returns true if the overlay is in expanded mode.
     */
    public val isExpanded: Boolean

    /**
     * Detaches and removes the overlay from the activity.
     *
     * After calling this, the handle becomes invalid and
     * should not be reused.
     */
    public fun detach()

    /**
     * Toggles between collapsed and expanded modes.
     */
    public fun toggleExpanded()

    /**
     * Sets the expanded state explicitly.
     */
    public fun setExpanded(expanded: Boolean)

    /**
     * Shows the overlay if it was hidden.
     */
    public fun show()

    /**
     * Hides the overlay without detaching.
     */
    public fun hide()

    /**
     * Updates the overlay configuration.
     *
     * @param config New configuration to apply
     */
    public fun updateConfig(config: OverlayConfig)
}

package com.sysmetrics.infrastructure.extension

/**
 * Utility extensions for the SysMetrics library.
 */

/**
 * Safely converts a [Long] representing kilobytes to megabytes.
 *
 * @return The value in megabytes
 */
public fun Long.kbToMb(): Long = this / 1024

/**
 * Safely converts a [Long] representing bytes to megabytes.
 *
 * @return The value in megabytes
 */
public fun Long.bytesToMb(): Long = this / (1024 * 1024)

/**
 * Clamps a [Float] value to be within a percentage range (0-100).
 *
 * @return The clamped percentage value
 */
public fun Float.clampPercent(): Float = this.coerceIn(0f, 100f)

/**
 * Formats a [Float] to a specified number of decimal places.
 *
 * @param decimals Number of decimal places
 * @return Formatted float value
 */
public fun Float.roundTo(decimals: Int): Float {
    var multiplier = 1f
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

/**
 * Calculates percentage of [this] value relative to [total].
 *
 * @param total The total value
 * @return Percentage (0-100) or 0 if total is 0
 */
public fun Long.percentOf(total: Long): Float {
    return if (total > 0) {
        ((this.toFloat() / total.toFloat()) * 100f).clampPercent()
    } else {
        0f
    }
}

/**
 * Converts milliseconds to a human-readable duration string.
 *
 * @return Formatted duration string (e.g., "2d 5h 30m 15s")
 */
public fun Long.toReadableDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return buildString {
        if (days > 0) append("${days}d ")
        if (hours % 24 > 0) append("${hours % 24}h ")
        if (minutes % 60 > 0) append("${minutes % 60}m ")
        append("${seconds % 60}s")
    }.trim()
}

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the overall health status of the system.
 *
 * Used to categorize system health into discrete levels for
 * quick assessment and decision making.
 */
@Serializable
public enum class HealthStatus {
    /** System is performing optimally with no issues */
    EXCELLENT,
    /** System is performing well with minor concerns */
    GOOD,
    /** System has issues that may need attention */
    WARNING,
    /** System has critical issues requiring immediate attention */
    CRITICAL
}

/**
 * Represents the current battery charging status.
 *
 * Maps directly to Android's BatteryManager status constants.
 */
@Serializable
public enum class BatteryStatus {
    /** Battery status cannot be determined */
    UNKNOWN,
    /** Battery is currently charging */
    CHARGING,
    /** Battery is discharging (in use) */
    DISCHARGING,
    /** Battery is not charging but connected to power */
    NOT_CHARGING,
    /** Battery is fully charged */
    FULL
}

/**
 * Represents the health condition of the battery.
 *
 * Maps directly to Android's BatteryManager health constants.
 */
@Serializable
public enum class BatteryHealth {
    /** Battery health cannot be determined */
    UNKNOWN,
    /** Battery is in good condition */
    GOOD,
    /** Battery is overheating */
    OVERHEAT,
    /** Battery is dead and needs replacement */
    DEAD,
    /** Battery voltage is too high */
    OVER_VOLTAGE,
    /** Battery has an unspecified failure */
    UNSPECIFIED_FAILURE,
    /** Battery temperature is too low */
    COLD
}

/**
 * Represents specific health issues detected in the system.
 *
 * Each issue type corresponds to a specific threshold violation
 * or abnormal system condition that may require attention.
 */
@Serializable
public enum class HealthIssue {
    /** CPU usage exceeds 85% threshold */
    HIGH_CPU_USAGE,
    /** Memory usage exceeds 85% threshold */
    HIGH_MEMORY_USAGE,
    /** Temperature exceeds safe operating limits */
    HIGH_TEMPERATURE,
    /** Battery level is critically low (below 15%) */
    LOW_BATTERY,
    /** Device is thermally throttling to prevent damage */
    THERMAL_THROTTLING,
    /** Storage space is critically low (below 10%) */
    LOW_STORAGE,
    /** Overall system performance is degraded */
    POOR_PERFORMANCE
}

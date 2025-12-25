package com.sysmetrics.infrastructure.logger

import android.util.Log
import com.sysmetrics.domain.logger.LogLevel
import com.sysmetrics.domain.logger.MetricsLogger

/**
 * Android-specific implementation of [MetricsLogger] using Android's [Log] class.
 *
 * This logger wraps Android's native logging system and provides:
 * - Configurable minimum log level
 * - Consistent tag formatting
 * - Thread-safe logging
 *
 * ## Usage
 *
 * ```kotlin
 * // Default: INFO level with "SysMetrics" tag
 * val logger = AndroidMetricsLogger()
 *
 * // Custom configuration
 * val debugLogger = AndroidMetricsLogger(
 *     tag = "MyApp",
 *     minLevel = LogLevel.DEBUG
 * )
 *
 * // In production
 * val prodLogger = AndroidMetricsLogger(
 *     tag = "SysMetrics",
 *     minLevel = LogLevel.WARN  // Only warnings and errors
 * )
 * ```
 *
 * ## Logcat Filtering
 *
 * Filter logs by tag in logcat:
 * ```bash
 * adb logcat -s SysMetrics:*
 * adb logcat SysMetrics:D *:S  # Debug and above
 * ```
 *
 * @property tag Base tag for all log messages (appears in logcat)
 * @property minLevel Minimum log level to output (lower levels are filtered)
 */
public class AndroidMetricsLogger(
    private val tag: String = DEFAULT_TAG,
    private val minLevel: LogLevel = LogLevel.INFO
) : MetricsLogger {

    override fun debug(tag: String, message: String) {
        if (LogLevel.DEBUG.isEnabled(minLevel)) {
            Log.d(this.tag, formatMessage(tag, message))
        }
    }

    override fun info(tag: String, message: String) {
        if (LogLevel.INFO.isEnabled(minLevel)) {
            Log.i(this.tag, formatMessage(tag, message))
        }
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        if (LogLevel.WARN.isEnabled(minLevel)) {
            if (error != null) {
                Log.w(this.tag, formatMessage(tag, message), error)
            } else {
                Log.w(this.tag, formatMessage(tag, message))
            }
        }
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        // Errors are always logged
        if (error != null) {
            Log.e(this.tag, formatMessage(tag, message), error)
        } else {
            Log.e(this.tag, formatMessage(tag, message))
        }
    }

    override fun isDebugEnabled(): Boolean = LogLevel.DEBUG.isEnabled(minLevel)

    override fun isInfoEnabled(): Boolean = LogLevel.INFO.isEnabled(minLevel)

    private fun formatMessage(componentTag: String, message: String): String {
        return "[$componentTag] $message"
    }

    public companion object {
        /** Default tag for SysMetrics logs */
        public const val DEFAULT_TAG: String = "SysMetrics"

        /**
         * Creates a logger for development with DEBUG level.
         */
        public fun forDevelopment(tag: String = DEFAULT_TAG): AndroidMetricsLogger =
            AndroidMetricsLogger(tag, LogLevel.DEBUG)

        /**
         * Creates a logger for production with WARN level.
         */
        public fun forProduction(tag: String = DEFAULT_TAG): AndroidMetricsLogger =
            AndroidMetricsLogger(tag, LogLevel.WARN)
    }
}

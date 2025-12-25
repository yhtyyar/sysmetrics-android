package com.sysmetrics.infrastructure.logger

import com.sysmetrics.domain.logger.MetricsLogger

/**
 * Composite logger that delegates to multiple [MetricsLogger] implementations.
 *
 * Use this when you need to log to multiple destinations simultaneously,
 * such as both Android logcat and a file.
 *
 * ## Usage
 *
 * ```kotlin
 * val androidLogger = AndroidMetricsLogger()
 * val fileLogger = FileMetricsLogger(logFile)
 *
 * val compositeLogger = CompositeMetricsLogger(
 *     androidLogger,
 *     fileLogger
 * )
 *
 * // Logs to both destinations
 * compositeLogger.info("App", "Application started")
 * ```
 *
 * ## Level Checks
 *
 * [isDebugEnabled] and [isInfoEnabled] return true if ANY of the
 * underlying loggers have that level enabled. This ensures debug
 * messages are constructed if at least one logger needs them.
 *
 * @property loggers The loggers to delegate to
 */
public class CompositeMetricsLogger(
    private vararg val loggers: MetricsLogger
) : MetricsLogger {

    override fun debug(tag: String, message: String) {
        loggers.forEach { it.debug(tag, message) }
    }

    override fun info(tag: String, message: String) {
        loggers.forEach { it.info(tag, message) }
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        loggers.forEach { it.warn(tag, message, error) }
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        loggers.forEach { it.error(tag, message, error) }
    }

    override fun isDebugEnabled(): Boolean = loggers.any { it.isDebugEnabled() }

    override fun isInfoEnabled(): Boolean = loggers.any { it.isInfoEnabled() }

    /**
     * Returns the number of underlying loggers.
     */
    public fun loggerCount(): Int = loggers.size

    public companion object {
        /**
         * Creates a composite logger with Android and file loggers.
         *
         * @param logFile File to write logs to
         * @param androidTag Tag for Android logcat
         * @return Composite logger writing to both destinations
         */
        public fun withFileBackup(
            logFile: java.io.File,
            androidTag: String = AndroidMetricsLogger.DEFAULT_TAG
        ): CompositeMetricsLogger = CompositeMetricsLogger(
            AndroidMetricsLogger(androidTag),
            FileMetricsLogger(logFile)
        )
    }
}

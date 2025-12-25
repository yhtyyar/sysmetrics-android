package com.sysmetrics.domain.logger

/**
 * Interface for structured logging in SysMetrics library.
 *
 * Provides a platform-agnostic logging abstraction that allows:
 * - Dependency injection of custom loggers
 * - Zero-cost debug logging when disabled
 * - Structured logging with tags for filtering
 * - Testable logging via mock implementations
 *
 * ## Usage
 *
 * ```kotlin
 * class MyComponent(private val logger: MetricsLogger) {
 *     fun doWork() {
 *         logger.info("MyComponent", "Starting work")
 *         
 *         // Avoid expensive string construction when debug is disabled
 *         if (logger.isDebugEnabled()) {
 *             logger.debug("MyComponent", "Details: ${expensiveToString()}")
 *         }
 *         
 *         try {
 *             // ... work
 *         } catch (e: Exception) {
 *             logger.error("MyComponent", "Work failed", e)
 *         }
 *     }
 * }
 * ```
 *
 * ## Log Levels
 *
 * - **DEBUG**: Detailed diagnostic info, disabled in production
 * - **INFO**: General operational events
 * - **WARN**: Potential issues that don't prevent operation
 * - **ERROR**: Errors that require attention
 *
 * @see AndroidMetricsLogger for Android implementation
 * @see NoOpLogger for testing/disabled logging
 * @see CompositeMetricsLogger for multi-destination logging
 */
public interface MetricsLogger {

    /**
     * Log a debug message.
     *
     * Debug messages are for detailed diagnostic information during
     * development. They are typically disabled in production builds.
     *
     * @param tag Component or class name for filtering
     * @param message Debug message
     */
    public fun debug(tag: String, message: String)

    /**
     * Log an info message.
     *
     * Info messages are for general operational events like
     * initialization, configuration changes, or successful operations.
     *
     * @param tag Component or class name for filtering
     * @param message Info message
     */
    public fun info(tag: String, message: String)

    /**
     * Log a warning message.
     *
     * Warnings indicate potential issues that don't prevent the
     * operation from completing, such as degraded performance,
     * fallback behavior, or deprecated usage.
     *
     * @param tag Component or class name for filtering
     * @param message Warning message
     * @param error Optional exception associated with the warning
     */
    public fun warn(tag: String, message: String, error: Throwable? = null)

    /**
     * Log an error message.
     *
     * Errors indicate failures that require attention. Always logged
     * regardless of log level configuration.
     *
     * @param tag Component or class name for filtering
     * @param message Error message
     * @param error Optional exception that caused the error
     */
    public fun error(tag: String, message: String, error: Throwable? = null)

    /**
     * Check if debug logging is enabled.
     *
     * Use this to avoid expensive string construction when debug
     * logging is disabled:
     *
     * ```kotlin
     * if (logger.isDebugEnabled()) {
     *     logger.debug("Tag", "Large object: ${expensiveToString()}")
     * }
     * ```
     *
     * @return true if debug messages will be logged
     */
    public fun isDebugEnabled(): Boolean

    /**
     * Check if info logging is enabled.
     *
     * @return true if info messages will be logged
     */
    public fun isInfoEnabled(): Boolean
}

/**
 * Log level enumeration for configuring minimum log level.
 *
 * Higher priority values indicate more severe log levels.
 * Setting a minimum level will filter out all lower priority messages.
 *
 * @property priority Numeric priority (higher = more severe)
 */
public enum class LogLevel(public val priority: Int) {
    /** Detailed diagnostic info, typically disabled in production */
    DEBUG(3),
    /** General operational events */
    INFO(4),
    /** Potential issues that don't prevent operation */
    WARN(5),
    /** Errors that require attention */
    ERROR(6);

    /**
     * Check if this level is enabled given a minimum level.
     *
     * @param minLevel The minimum enabled log level
     * @return true if this level should be logged
     */
    public fun isEnabled(minLevel: LogLevel): Boolean = this.priority >= minLevel.priority
}

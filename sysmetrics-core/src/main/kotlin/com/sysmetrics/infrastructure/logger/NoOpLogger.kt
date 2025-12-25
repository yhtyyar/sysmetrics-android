package com.sysmetrics.infrastructure.logger

import com.sysmetrics.domain.logger.MetricsLogger

/**
 * No-operation logger that discards all log messages.
 *
 * Use this when:
 * - You want to completely disable logging
 * - In unit tests where logging is not needed
 * - As a default when no logger is configured
 *
 * ## Usage
 *
 * ```kotlin
 * // Disable all logging
 * val repository = MetricsRepositoryImpl(
 *     logger = NoOpLogger
 * )
 *
 * // In tests
 * @Test
 * fun testSomething() {
 *     val component = MyComponent(NoOpLogger)
 *     // ...
 * }
 * ```
 *
 * This is implemented as an object (singleton) since it has no state.
 */
public object NoOpLogger : MetricsLogger {

    override fun debug(tag: String, message: String) {
        // No-op
    }

    override fun info(tag: String, message: String) {
        // No-op
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        // No-op
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        // No-op
    }

    override fun isDebugEnabled(): Boolean = false

    override fun isInfoEnabled(): Boolean = false
}

/**
 * Logger that captures log messages for testing.
 *
 * Use this in unit tests to verify logging behavior.
 *
 * ## Usage
 *
 * ```kotlin
 * @Test
 * fun `logs error on failure`() {
 *     val testLogger = TestLogger()
 *     val component = MyComponent(testLogger)
 *
 *     component.doSomethingThatFails()
 *
 *     assertEquals(1, testLogger.errorCount)
 *     assertTrue(testLogger.lastErrorMessage?.contains("failed") == true)
 * }
 * ```
 */
public class TestLogger : MetricsLogger {

    private val _debugMessages = mutableListOf<LogEntry>()
    private val _infoMessages = mutableListOf<LogEntry>()
    private val _warnMessages = mutableListOf<LogEntry>()
    private val _errorMessages = mutableListOf<LogEntry>()

    /** All captured debug messages */
    public val debugMessages: List<LogEntry> get() = _debugMessages.toList()

    /** All captured info messages */
    public val infoMessages: List<LogEntry> get() = _infoMessages.toList()

    /** All captured warning messages */
    public val warnMessages: List<LogEntry> get() = _warnMessages.toList()

    /** All captured error messages */
    public val errorMessages: List<LogEntry> get() = _errorMessages.toList()

    /** Number of debug messages logged */
    public val debugCount: Int get() = _debugMessages.size

    /** Number of info messages logged */
    public val infoCount: Int get() = _infoMessages.size

    /** Number of warning messages logged */
    public val warnCount: Int get() = _warnMessages.size

    /** Number of error messages logged */
    public val errorCount: Int get() = _errorMessages.size

    /** Last debug message or null */
    public val lastDebugMessage: String? get() = _debugMessages.lastOrNull()?.message

    /** Last info message or null */
    public val lastInfoMessage: String? get() = _infoMessages.lastOrNull()?.message

    /** Last warning message or null */
    public val lastWarnMessage: String? get() = _warnMessages.lastOrNull()?.message

    /** Last error message or null */
    public val lastErrorMessage: String? get() = _errorMessages.lastOrNull()?.message

    override fun debug(tag: String, message: String) {
        _debugMessages.add(LogEntry(tag, message, null))
    }

    override fun info(tag: String, message: String) {
        _infoMessages.add(LogEntry(tag, message, null))
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        _warnMessages.add(LogEntry(tag, message, error))
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        _errorMessages.add(LogEntry(tag, message, error))
    }

    override fun isDebugEnabled(): Boolean = true

    override fun isInfoEnabled(): Boolean = true

    /**
     * Clears all captured log messages.
     */
    public fun clear() {
        _debugMessages.clear()
        _infoMessages.clear()
        _warnMessages.clear()
        _errorMessages.clear()
    }

    /**
     * Returns true if any messages were logged at any level.
     */
    public fun hasAnyLogs(): Boolean =
        _debugMessages.isNotEmpty() ||
        _infoMessages.isNotEmpty() ||
        _warnMessages.isNotEmpty() ||
        _errorMessages.isNotEmpty()

    /**
     * Returns all log entries across all levels.
     */
    public fun allEntries(): List<LogEntry> =
        _debugMessages + _infoMessages + _warnMessages + _errorMessages

    /**
     * A captured log entry.
     *
     * @property tag Component tag
     * @property message Log message
     * @property error Associated exception (if any)
     */
    public data class LogEntry(
        val tag: String,
        val message: String,
        val error: Throwable?
    )
}

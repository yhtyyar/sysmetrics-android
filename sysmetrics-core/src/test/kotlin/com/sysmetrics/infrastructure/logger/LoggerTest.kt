package com.sysmetrics.infrastructure.logger

import com.sysmetrics.domain.logger.LogLevel
import com.sysmetrics.domain.logger.MetricsLogger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for logger implementations.
 *
 * Tests cover:
 * - NoOpLogger behavior
 * - TestLogger capture functionality
 * - CompositeMetricsLogger delegation
 * - FileMetricsLogger file operations and rotation
 * - LogLevel filtering
 */
class LoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== NoOpLogger Tests ====================

    @Test
    fun `NoOpLogger does not throw on any log call`() {
        // Should not throw
        NoOpLogger.debug("tag", "message")
        NoOpLogger.info("tag", "message")
        NoOpLogger.warn("tag", "message", Exception("test"))
        NoOpLogger.error("tag", "message", Exception("test"))
    }

    @Test
    fun `NoOpLogger reports debug and info as disabled`() {
        assertFalse(NoOpLogger.isDebugEnabled())
        assertFalse(NoOpLogger.isInfoEnabled())
    }

    // ==================== TestLogger Tests ====================

    @Test
    fun `TestLogger captures debug messages`() {
        val logger = TestLogger()
        
        logger.debug("TestTag", "Debug message")
        
        assertEquals(1, logger.debugCount)
        assertEquals("Debug message", logger.lastDebugMessage)
        assertEquals("TestTag", logger.debugMessages.first().tag)
    }

    @Test
    fun `TestLogger captures info messages`() {
        val logger = TestLogger()
        
        logger.info("TestTag", "Info message")
        
        assertEquals(1, logger.infoCount)
        assertEquals("Info message", logger.lastInfoMessage)
    }

    @Test
    fun `TestLogger captures warn messages with error`() {
        val logger = TestLogger()
        val exception = RuntimeException("Test error")
        
        logger.warn("TestTag", "Warning message", exception)
        
        assertEquals(1, logger.warnCount)
        assertEquals("Warning message", logger.lastWarnMessage)
        assertEquals(exception, logger.warnMessages.first().error)
    }

    @Test
    fun `TestLogger captures error messages`() {
        val logger = TestLogger()
        val exception = RuntimeException("Test error")
        
        logger.error("TestTag", "Error message", exception)
        
        assertEquals(1, logger.errorCount)
        assertEquals("Error message", logger.lastErrorMessage)
        assertEquals(exception, logger.errorMessages.first().error)
    }

    @Test
    fun `TestLogger clear removes all messages`() {
        val logger = TestLogger()
        
        logger.debug("tag", "debug")
        logger.info("tag", "info")
        logger.warn("tag", "warn")
        logger.error("tag", "error")
        
        assertTrue(logger.hasAnyLogs())
        
        logger.clear()
        
        assertFalse(logger.hasAnyLogs())
        assertEquals(0, logger.debugCount)
        assertEquals(0, logger.infoCount)
        assertEquals(0, logger.warnCount)
        assertEquals(0, logger.errorCount)
    }

    @Test
    fun `TestLogger allEntries returns all log entries`() {
        val logger = TestLogger()
        
        logger.debug("tag", "debug")
        logger.info("tag", "info")
        logger.warn("tag", "warn")
        logger.error("tag", "error")
        
        val entries = logger.allEntries()
        assertEquals(4, entries.size)
    }

    @Test
    fun `TestLogger reports debug and info as enabled`() {
        val logger = TestLogger()
        
        assertTrue(logger.isDebugEnabled())
        assertTrue(logger.isInfoEnabled())
    }

    // ==================== CompositeMetricsLogger Tests ====================

    @Test
    fun `CompositeMetricsLogger delegates to all loggers`() {
        val logger1 = TestLogger()
        val logger2 = TestLogger()
        val composite = CompositeMetricsLogger(logger1, logger2)
        
        composite.debug("tag", "debug")
        composite.info("tag", "info")
        composite.warn("tag", "warn")
        composite.error("tag", "error")
        
        // Both loggers should receive all messages
        assertEquals(1, logger1.debugCount)
        assertEquals(1, logger2.debugCount)
        assertEquals(1, logger1.infoCount)
        assertEquals(1, logger2.infoCount)
        assertEquals(1, logger1.warnCount)
        assertEquals(1, logger2.warnCount)
        assertEquals(1, logger1.errorCount)
        assertEquals(1, logger2.errorCount)
    }

    @Test
    fun `CompositeMetricsLogger isDebugEnabled returns true if any logger has debug enabled`() {
        val enabledLogger = TestLogger()
        val disabledLogger = NoOpLogger
        
        val composite = CompositeMetricsLogger(enabledLogger, disabledLogger)
        
        assertTrue(composite.isDebugEnabled())
    }

    @Test
    fun `CompositeMetricsLogger isDebugEnabled returns false if all loggers have debug disabled`() {
        val composite = CompositeMetricsLogger(NoOpLogger, NoOpLogger)
        
        assertFalse(composite.isDebugEnabled())
    }

    @Test
    fun `CompositeMetricsLogger loggerCount returns correct count`() {
        val composite = CompositeMetricsLogger(TestLogger(), TestLogger(), TestLogger())
        
        assertEquals(3, composite.loggerCount())
    }

    // ==================== FileMetricsLogger Tests ====================

    @Test
    fun `FileMetricsLogger creates log file on first write`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(logFile)
        
        assertFalse(logFile.exists())
        
        logger.info("TestTag", "Test message")
        
        assertTrue(logFile.exists())
    }

    @Test
    fun `FileMetricsLogger writes formatted log entries`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(logFile)
        
        logger.info("TestTag", "Test message")
        
        val content = logFile.readText()
        assertTrue(content.contains("INFO"))
        assertTrue(content.contains("[TestTag]"))
        assertTrue(content.contains("Test message"))
    }

    @Test
    fun `FileMetricsLogger writes stack trace for errors`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(logFile)
        val exception = RuntimeException("Test exception")
        
        logger.error("TestTag", "Error occurred", exception)
        
        val content = logFile.readText()
        assertTrue(content.contains("ERROR"))
        assertTrue(content.contains("Test exception"))
        assertTrue(content.contains("RuntimeException"))
    }

    @Test
    fun `FileMetricsLogger respects minimum log level`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(logFile, minLevel = LogLevel.WARN)
        
        logger.debug("tag", "debug message")
        logger.info("tag", "info message")
        logger.warn("tag", "warn message")
        logger.error("tag", "error message")
        
        val content = logFile.readText()
        assertFalse(content.contains("debug message"))
        assertFalse(content.contains("info message"))
        assertTrue(content.contains("warn message"))
        assertTrue(content.contains("error message"))
    }

    @Test
    fun `FileMetricsLogger rotates files when size exceeded`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(
            logFile = logFile,
            maxSizeBytes = 100, // Very small for testing
            maxBackupFiles = 2
        )
        
        // Write enough to trigger rotation
        repeat(50) {
            logger.info("TestTag", "Message $it with some extra content to fill space")
        }
        
        // Check backup file was created
        val backupFile = File("${logFile.absolutePath}.1")
        assertTrue(backupFile.exists())
    }

    @Test
    fun `FileMetricsLogger clearLogs removes all files`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(
            logFile = logFile,
            maxSizeBytes = 100,
            maxBackupFiles = 2
        )
        
        // Create some log files
        repeat(50) {
            logger.info("tag", "Message $it extra content for size")
        }
        
        // Verify files exist
        assertTrue(logFile.exists())
        
        // Clear logs
        logger.clearLogs()
        
        // Verify files are gone
        assertFalse(logFile.exists())
    }

    @Test
    fun `FileMetricsLogger getAllLogFiles returns main and backup files`() {
        val logFile = File(tempFolder.root, "test.log")
        val logger = FileMetricsLogger(
            logFile = logFile,
            maxSizeBytes = 100,
            maxBackupFiles = 3
        )
        
        // Create some log files
        repeat(100) {
            logger.info("tag", "Message $it extra content")
        }
        
        val allFiles = logger.getAllLogFiles()
        assertTrue(allFiles.isNotEmpty())
        assertTrue(allFiles.contains(logFile))
    }

    @Test
    fun `FileMetricsLogger isDebugEnabled respects minLevel`() {
        val debugLogger = FileMetricsLogger(
            logFile = File(tempFolder.root, "debug.log"),
            minLevel = LogLevel.DEBUG
        )
        val warnLogger = FileMetricsLogger(
            logFile = File(tempFolder.root, "warn.log"),
            minLevel = LogLevel.WARN
        )
        
        assertTrue(debugLogger.isDebugEnabled())
        assertFalse(warnLogger.isDebugEnabled())
    }

    // ==================== LogLevel Tests ====================

    @Test
    fun `LogLevel isEnabled returns correct values`() {
        assertTrue(LogLevel.DEBUG.isEnabled(LogLevel.DEBUG))
        assertTrue(LogLevel.INFO.isEnabled(LogLevel.DEBUG))
        assertTrue(LogLevel.WARN.isEnabled(LogLevel.DEBUG))
        assertTrue(LogLevel.ERROR.isEnabled(LogLevel.DEBUG))
        
        assertFalse(LogLevel.DEBUG.isEnabled(LogLevel.INFO))
        assertTrue(LogLevel.INFO.isEnabled(LogLevel.INFO))
        assertTrue(LogLevel.WARN.isEnabled(LogLevel.INFO))
        assertTrue(LogLevel.ERROR.isEnabled(LogLevel.INFO))
        
        assertFalse(LogLevel.DEBUG.isEnabled(LogLevel.WARN))
        assertFalse(LogLevel.INFO.isEnabled(LogLevel.WARN))
        assertTrue(LogLevel.WARN.isEnabled(LogLevel.WARN))
        assertTrue(LogLevel.ERROR.isEnabled(LogLevel.WARN))
        
        assertFalse(LogLevel.DEBUG.isEnabled(LogLevel.ERROR))
        assertFalse(LogLevel.INFO.isEnabled(LogLevel.ERROR))
        assertFalse(LogLevel.WARN.isEnabled(LogLevel.ERROR))
        assertTrue(LogLevel.ERROR.isEnabled(LogLevel.ERROR))
    }

    @Test
    fun `LogLevel priority ordering is correct`() {
        assertTrue(LogLevel.DEBUG.priority < LogLevel.INFO.priority)
        assertTrue(LogLevel.INFO.priority < LogLevel.WARN.priority)
        assertTrue(LogLevel.WARN.priority < LogLevel.ERROR.priority)
    }

    // ==================== Integration Tests ====================

    @Test
    internal fun `TestLogger can be used to verify logging behavior`() {
        val logger = TestLogger()
        
        // Simulate component that uses logging
        simulateComponentWithLogging(logger)
        
        // Verify expected logging
        assertEquals(1, logger.infoCount)
        assertTrue(logger.lastInfoMessage?.contains("Starting") == true)
        
        assertEquals(1, logger.debugCount)
        assertTrue(logger.lastDebugMessage?.contains("Processing") == true)
    }

    private fun simulateComponentWithLogging(logger: MetricsLogger) {
        logger.info("Component", "Starting operation")
        if (logger.isDebugEnabled()) {
            logger.debug("Component", "Processing data: [1, 2, 3]")
        }
    }
}

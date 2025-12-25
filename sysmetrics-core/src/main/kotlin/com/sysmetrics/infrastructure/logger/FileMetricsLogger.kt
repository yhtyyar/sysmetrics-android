package com.sysmetrics.infrastructure.logger

import com.sysmetrics.domain.logger.LogLevel
import com.sysmetrics.domain.logger.MetricsLogger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * File-based implementation of [MetricsLogger] with automatic rotation.
 *
 * Logs are written to a file with automatic rotation when the file
 * exceeds the configured size limit. Old log files are renamed with
 * a numeric suffix (e.g., `sysmetrics.log.1`).
 *
 * ## Features
 *
 * - Thread-safe file writing
 * - Automatic size-based rotation
 * - Configurable retention (number of backup files)
 * - Timestamped log entries
 * - Buffered writing for performance
 *
 * ## Usage
 *
 * ```kotlin
 * val logDir = context.getExternalFilesDir(null) ?: context.filesDir
 * val logFile = File(logDir, "sysmetrics.log")
 *
 * val logger = FileMetricsLogger(
 *     logFile = logFile,
 *     maxSizeBytes = 5 * 1024 * 1024,  // 5 MB
 *     maxBackupFiles = 3
 * )
 *
 * logger.info("App", "Application started")
 * ```
 *
 * ## Log Format
 *
 * ```
 * 2025-01-01 12:00:00.123 INFO [ComponentTag] Message text
 * 2025-01-01 12:00:01.456 ERROR [ComponentTag] Error message
 * java.lang.Exception: Stack trace here
 *     at com.example.Class.method(Class.kt:42)
 * ```
 *
 * @property logFile Target log file
 * @property maxSizeBytes Maximum file size before rotation (default: 10 MB)
 * @property maxBackupFiles Number of backup files to keep (default: 3)
 * @property minLevel Minimum log level to write
 */
public class FileMetricsLogger(
    private val logFile: File,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE,
    private val maxBackupFiles: Int = DEFAULT_MAX_BACKUPS,
    private val minLevel: LogLevel = LogLevel.DEBUG
) : MetricsLogger {

    private val lock = ReentrantLock()
    private val dateFormat = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US)

    init {
        logFile.parentFile?.mkdirs()
    }

    override fun debug(tag: String, message: String) {
        if (LogLevel.DEBUG.isEnabled(minLevel)) {
            writeLog(LogLevel.DEBUG, tag, message, null)
        }
    }

    override fun info(tag: String, message: String) {
        if (LogLevel.INFO.isEnabled(minLevel)) {
            writeLog(LogLevel.INFO, tag, message, null)
        }
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        if (LogLevel.WARN.isEnabled(minLevel)) {
            writeLog(LogLevel.WARN, tag, message, error)
        }
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        writeLog(LogLevel.ERROR, tag, message, error)
    }

    override fun isDebugEnabled(): Boolean = LogLevel.DEBUG.isEnabled(minLevel)

    override fun isInfoEnabled(): Boolean = LogLevel.INFO.isEnabled(minLevel)

    private fun writeLog(level: LogLevel, tag: String, message: String, error: Throwable?) {
        lock.withLock {
            try {
                rotateIfNeeded()

                FileOutputStream(logFile, true).use { fos ->
                    OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                        val timestamp = dateFormat.format(Date())
                        writer.append("$timestamp ${level.name} [$tag] $message\n")

                        error?.let { throwable ->
                            writer.append(throwable.stackTraceToString())
                            writer.append("\n")
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore logging errors to prevent cascading failures
            }
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < maxSizeBytes) {
            return
        }

        // Rotate existing backup files
        for (i in maxBackupFiles - 1 downTo 1) {
            val older = File("${logFile.absolutePath}.$i")
            val newer = File("${logFile.absolutePath}.${i + 1}")
            if (older.exists()) {
                if (i == maxBackupFiles - 1) {
                    older.delete()
                } else {
                    older.renameTo(newer)
                }
            }
        }

        // Move current log to .1
        val backup = File("${logFile.absolutePath}.1")
        logFile.renameTo(backup)
    }

    /**
     * Clears all log files including backups.
     */
    public fun clearLogs() {
        lock.withLock {
            logFile.delete()
            for (i in 1..maxBackupFiles) {
                File("${logFile.absolutePath}.$i").delete()
            }
        }
    }

    /**
     * Returns the current size of the main log file.
     */
    public fun getCurrentLogSize(): Long = logFile.length()

    /**
     * Returns all log files (main + backups).
     */
    public fun getAllLogFiles(): List<File> {
        val files = mutableListOf<File>()
        if (logFile.exists()) {
            files.add(logFile)
        }
        for (i in 1..maxBackupFiles) {
            val backup = File("${logFile.absolutePath}.$i")
            if (backup.exists()) {
                files.add(backup)
            }
        }
        return files
    }

    public companion object {
        /** Default maximum log file size: 10 MB */
        public const val DEFAULT_MAX_SIZE: Long = 10 * 1024 * 1024

        /** Default number of backup files to keep */
        public const val DEFAULT_MAX_BACKUPS: Int = 3

        /** Date format pattern for log timestamps */
        public const val DATE_FORMAT_PATTERN: String = "yyyy-MM-dd HH:mm:ss.SSS"
    }
}

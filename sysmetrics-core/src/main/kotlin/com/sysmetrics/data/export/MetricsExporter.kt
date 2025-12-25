package com.sysmetrics.data.export

import com.sysmetrics.domain.model.SystemMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports system metrics to various formats.
 *
 * Provides functionality to export metrics history to CSV and JSON formats
 * for analysis, sharing, or backup purposes.
 *
 * Thread-safe: All export operations run on [Dispatchers.IO].
 */
public object MetricsExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Exports metrics history to CSV format.
     *
     * The CSV includes columns for all metric types with headers.
     * Timestamps are formatted as human-readable dates.
     *
     * @param metrics List of [SystemMetrics] to export
     * @return [Result] containing CSV string or an error
     *
     * Example output:
     * ```csv
     * timestamp,cpu_usage,memory_usage,battery_level,cpu_temp,storage_usage,network_rx,network_tx
     * 2024-01-15 10:30:00,45.5,62.3,85,42.0,55.2,1024000,512000
     * ```
     */
    public suspend fun exportToCsv(metrics: List<SystemMetrics>): Result<String> = 
        withContext(Dispatchers.IO) {
            runCatching {
                buildString {
                    // Header
                    appendLine(CSV_HEADER)

                    // Data rows
                    metrics.forEach { metric ->
                        appendLine(formatCsvRow(metric))
                    }
                }
            }
        }

    /**
     * Exports metrics history to JSON format.
     *
     * Uses kotlinx.serialization for proper JSON encoding.
     * Output is pretty-printed for readability.
     *
     * @param metrics List of [SystemMetrics] to export
     * @return [Result] containing JSON string or an error
     */
    public suspend fun exportToJson(metrics: List<SystemMetrics>): Result<String> = 
        withContext(Dispatchers.IO) {
            runCatching {
                json.encodeToString(ExportWrapper(
                    exportDate = dateFormat.format(Date()),
                    count = metrics.size,
                    metrics = metrics
                ))
            }
        }

    /**
     * Exports a single metrics snapshot to JSON format.
     *
     * @param metrics Single [SystemMetrics] to export
     * @return [Result] containing JSON string or an error
     */
    public suspend fun exportSingleToJson(metrics: SystemMetrics): Result<String> = 
        withContext(Dispatchers.IO) {
            runCatching {
                json.encodeToString(metrics)
            }
        }

    /**
     * Generates a summary report of metrics history.
     *
     * Includes averages, min/max values, and trends for all metric types.
     *
     * @param metrics List of [SystemMetrics] to analyze
     * @return [Result] containing summary report string or an error
     */
    public suspend fun generateSummaryReport(metrics: List<SystemMetrics>): Result<String> = 
        withContext(Dispatchers.IO) {
            runCatching {
                if (metrics.isEmpty()) {
                    return@runCatching "No metrics data available for summary."
                }

                val cpuUsages = metrics.map { it.cpuMetrics.usagePercent }
                val memUsages = metrics.map { it.memoryMetrics.usagePercent }
                val batteryLevels = metrics.map { it.batteryMetrics.level }
                val cpuTemps = metrics.map { it.thermalMetrics.cpuTemperature }
                val storageUsages = metrics.map { it.storageMetrics.usagePercent }

                buildString {
                    appendLine("=" .repeat(50))
                    appendLine("SYSMETRICS SUMMARY REPORT")
                    appendLine("=" .repeat(50))
                    appendLine()
                    appendLine("Report Generated: ${dateFormat.format(Date())}")
                    appendLine("Data Points: ${metrics.size}")
                    appendLine("Time Range: ${formatTimeRange(metrics)}")
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine("CPU USAGE")
                    appendLine("-".repeat(50))
                    appendLine("  Average: ${String.format("%.1f", cpuUsages.average())}%")
                    appendLine("  Min: ${String.format("%.1f", cpuUsages.minOrNull() ?: 0f)}%")
                    appendLine("  Max: ${String.format("%.1f", cpuUsages.maxOrNull() ?: 0f)}%")
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine("MEMORY USAGE")
                    appendLine("-".repeat(50))
                    appendLine("  Average: ${String.format("%.1f", memUsages.average())}%")
                    appendLine("  Min: ${String.format("%.1f", memUsages.minOrNull() ?: 0f)}%")
                    appendLine("  Max: ${String.format("%.1f", memUsages.maxOrNull() ?: 0f)}%")
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine("BATTERY")
                    appendLine("-".repeat(50))
                    appendLine("  Average: ${String.format("%.1f", batteryLevels.average())}%")
                    appendLine("  Min: ${batteryLevels.minOrNull() ?: 0}%")
                    appendLine("  Max: ${batteryLevels.maxOrNull() ?: 0}%")
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine("CPU TEMPERATURE")
                    appendLine("-".repeat(50))
                    appendLine("  Average: ${String.format("%.1f", cpuTemps.average())}°C")
                    appendLine("  Min: ${String.format("%.1f", cpuTemps.minOrNull() ?: 0f)}°C")
                    appendLine("  Max: ${String.format("%.1f", cpuTemps.maxOrNull() ?: 0f)}°C")
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine("STORAGE USAGE")
                    appendLine("-".repeat(50))
                    appendLine("  Average: ${String.format("%.1f", storageUsages.average())}%")
                    appendLine("  Min: ${String.format("%.1f", storageUsages.minOrNull() ?: 0f)}%")
                    appendLine("  Max: ${String.format("%.1f", storageUsages.maxOrNull() ?: 0f)}%")
                    appendLine()
                    appendLine("=" .repeat(50))
                }
            }
        }

    private fun formatCsvRow(metrics: SystemMetrics): String {
        return listOf(
            dateFormat.format(Date(metrics.timestamp)),
            String.format("%.2f", metrics.cpuMetrics.usagePercent),
            String.format("%.2f", metrics.memoryMetrics.usagePercent),
            metrics.batteryMetrics.level.toString(),
            String.format("%.1f", metrics.thermalMetrics.cpuTemperature),
            String.format("%.2f", metrics.storageMetrics.usagePercent),
            metrics.networkMetrics.rxBytesPerSecond.toString(),
            metrics.networkMetrics.txBytesPerSecond.toString(),
            metrics.networkMetrics.connectionType.name,
            metrics.uptime.toString()
        ).joinToString(",")
    }

    private fun formatTimeRange(metrics: List<SystemMetrics>): String {
        if (metrics.isEmpty()) return "N/A"
        val first = metrics.minByOrNull { it.timestamp }?.timestamp ?: return "N/A"
        val last = metrics.maxByOrNull { it.timestamp }?.timestamp ?: return "N/A"
        return "${dateFormat.format(Date(first))} - ${dateFormat.format(Date(last))}"
    }

    private const val CSV_HEADER = 
        "timestamp,cpu_usage,memory_usage,battery_level,cpu_temp,storage_usage,network_rx_bps,network_tx_bps,network_type,uptime_ms"

    @kotlinx.serialization.Serializable
    private data class ExportWrapper(
        val exportDate: String,
        val count: Int,
        val metrics: List<SystemMetrics>
    )
}

package com.sysmetrics.data.export

import com.sysmetrics.domain.export.CsvExportConfig
import com.sysmetrics.domain.export.ExportConfig
import com.sysmetrics.domain.export.ExportException
import com.sysmetrics.domain.export.MetricsExporter
import com.sysmetrics.domain.logger.MetricsLogger
import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.SystemMetrics
import com.sysmetrics.infrastructure.logger.NoOpLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/**
 * CSV metrics exporter following RFC 4180 specification.
 *
 * Produces CSV files compatible with:
 * - Microsoft Excel (with UTF-8 BOM option)
 * - Google Sheets
 * - LibreOffice Calc
 * - Most data analysis tools
 *
 * Features:
 * - RFC 4180 compliant field escaping
 * - UTF-8 BOM for Excel compatibility
 * - Configurable delimiter, quote, and escape characters
 * - Support for both raw and aggregated metrics
 *
 * Thread-safe: This class is stateless and can be safely used from multiple threads.
 *
 * Example:
 * ```kotlin
 * val exporter = CsvMetricsExporter()
 * val csv = exporter.exportRawMetrics(
 *     metrics = metricsHistory,
 *     config = CsvExportConfig.forExcel()
 * )
 * File("metrics.csv").writeText(csv)
 * ```
 */
public class CsvMetricsExporter(
    private val logger: MetricsLogger = NoOpLogger
) : MetricsExporter {

    override val mimeType: String = "text/csv"
    override val formatName: String = "csv"
    override val fileExtension: String = "csv"

    /**
     * Exports raw system metrics to CSV format.
     *
     * Columns exported:
     * - Timestamp (ISO format or epoch millis)
     * - CPU Usage (%)
     * - Memory Usage (%)
     * - Battery Level (%)
     * - Temperature (°C)
     * - Health Score
     * - Network RX (bytes/s)
     * - Network TX (bytes/s)
     * - Network Type
     * - Uptime (ms)
     *
     * @param metrics List of system metrics to export
     * @param config Export configuration (CsvExportConfig recommended)
     * @return CSV formatted string
     */
    override fun exportRawMetrics(
        metrics: List<SystemMetrics>,
        config: ExportConfig
    ): String {
        val csvConfig = config as? CsvExportConfig ?: CsvExportConfig()
        logger.info(TAG, "Exporting ${metrics.size} raw metrics to CSV")
        
        if (logger.isDebugEnabled()) {
            logger.debug(TAG, "Config: delimiter='${csvConfig.delimiter}', bom=${csvConfig.includeUtf8Bom}, headers=${csvConfig.includeHeaders}")
        }
        
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = buildString {
                // UTF-8 BOM for Excel compatibility
                if (csvConfig.includeUtf8Bom) {
                    append(UTF8_BOM)
                }

                // Headers
                if (csvConfig.includeHeaders) {
                    append(buildCsvLine(RAW_METRICS_HEADERS, csvConfig))
                    append(csvConfig.lineEnding)
                }

                // Data rows
                val dateFormat = createDateFormat(csvConfig)
                metrics.forEach { metric ->
                    append(buildRawMetricLine(metric, csvConfig, dateFormat))
                    append(csvConfig.lineEnding)
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            logger.info(TAG, "Export completed: ${result.length} bytes in ${duration}ms")
            
            result
        } catch (e: Exception) {
            logger.error(TAG, "Export failed: ${e.message}", e)
            throw ExportException(formatName, "Failed to export raw metrics: ${e.message}", e)
        }
    }

    /**
     * Exports aggregated metrics to CSV format.
     *
     * Columns exported:
     * - Time Window
     * - Start Time
     * - End Time
     * - Sample Count
     * - CPU Avg (%)
     * - CPU Min (%)
     * - CPU Max (%)
     * - Memory Avg (%)
     * - Memory Min (%)
     * - Memory Max (%)
     * - Battery Avg (%)
     * - Temperature (°C)
     * - Health Score Avg
     * - Network RX Total (bytes)
     * - Network TX Total (bytes)
     *
     * @param aggregated List of aggregated metrics to export
     * @param config Export configuration
     * @return CSV formatted string
     */
    override fun exportAggregatedMetrics(
        aggregated: List<AggregatedMetrics>,
        config: ExportConfig
    ): String {
        val csvConfig = config as? CsvExportConfig ?: CsvExportConfig()
        logger.info(TAG, "Exporting ${aggregated.size} aggregated metrics to CSV")
        
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = buildString {
                // UTF-8 BOM for Excel compatibility
                if (csvConfig.includeUtf8Bom) {
                    append(UTF8_BOM)
                }

                // Headers
                if (csvConfig.includeHeaders) {
                    append(buildCsvLine(AGGREGATED_METRICS_HEADERS, csvConfig))
                    append(csvConfig.lineEnding)
                }

                // Data rows
                val dateFormat = createDateFormat(csvConfig)
                aggregated.forEach { agg ->
                    append(buildAggregatedMetricLine(agg, csvConfig, dateFormat))
                    append(csvConfig.lineEnding)
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            logger.info(TAG, "Export completed: ${result.length} bytes in ${duration}ms")
            
            result
        } catch (e: Exception) {
            logger.error(TAG, "Export failed: ${e.message}", e)
            throw ExportException(formatName, "Failed to export aggregated metrics: ${e.message}", e)
        }
    }

    /**
     * Streaming export for raw metrics (memory-efficient for large datasets).
     */
    override fun exportRawMetricsTo(
        metrics: List<SystemMetrics>,
        config: ExportConfig,
        output: StringBuilder
    ) {
        val csvConfig = config as? CsvExportConfig ?: CsvExportConfig()
        val dateFormat = createDateFormat(csvConfig)

        if (csvConfig.includeUtf8Bom) {
            output.append(UTF8_BOM)
        }

        if (csvConfig.includeHeaders) {
            output.append(buildCsvLine(RAW_METRICS_HEADERS, csvConfig))
            output.append(csvConfig.lineEnding)
        }

        metrics.forEach { metric ->
            output.append(buildRawMetricLine(metric, csvConfig, dateFormat))
            output.append(csvConfig.lineEnding)
        }
    }

    /**
     * Streaming export for aggregated metrics.
     */
    override fun exportAggregatedMetricsTo(
        aggregated: List<AggregatedMetrics>,
        config: ExportConfig,
        output: StringBuilder
    ) {
        val csvConfig = config as? CsvExportConfig ?: CsvExportConfig()
        val dateFormat = createDateFormat(csvConfig)

        if (csvConfig.includeUtf8Bom) {
            output.append(UTF8_BOM)
        }

        if (csvConfig.includeHeaders) {
            output.append(buildCsvLine(AGGREGATED_METRICS_HEADERS, csvConfig))
            output.append(csvConfig.lineEnding)
        }

        aggregated.forEach { agg ->
            output.append(buildAggregatedMetricLine(agg, csvConfig, dateFormat))
            output.append(csvConfig.lineEnding)
        }
    }

    // ==================== Private Helper Methods ====================

    private fun buildRawMetricLine(
        metric: SystemMetrics,
        config: CsvExportConfig,
        dateFormat: SimpleDateFormat
    ): String {
        val values = listOf(
            if (config.includeTimestamp) dateFormat.format(Date(metric.timestamp)) else metric.timestamp.toString(),
            formatFloat(metric.cpuMetrics.usagePercent),
            formatFloat(metric.memoryMetrics.usagePercent),
            metric.batteryMetrics.level.toString(),
            formatFloat(metric.thermalMetrics.cpuTemperature),
            formatFloat(metric.getHealthScore()),
            metric.networkMetrics.rxBytesPerSecond.toString(),
            metric.networkMetrics.txBytesPerSecond.toString(),
            metric.networkMetrics.connectionType.name,
            metric.uptime.toString()
        )
        return buildCsvLine(values, config)
    }

    private fun buildAggregatedMetricLine(
        agg: AggregatedMetrics,
        config: CsvExportConfig,
        dateFormat: SimpleDateFormat
    ): String {
        val values = listOf(
            agg.timeWindow.name,
            if (config.includeTimestamp) dateFormat.format(Date(agg.windowStartTime)) else agg.windowStartTime.toString(),
            if (config.includeTimestamp) dateFormat.format(Date(agg.windowEndTime)) else agg.windowEndTime.toString(),
            agg.sampleCount.toString(),
            formatFloat(agg.cpuPercentAverage),
            formatFloat(agg.cpuPercentMin),
            formatFloat(agg.cpuPercentMax),
            formatFloat(agg.memoryPercentAverage),
            formatFloat(agg.memoryPercentMin),
            formatFloat(agg.memoryPercentMax),
            formatFloat(agg.batteryPercentAverage),
            formatFloat(agg.temperatureCelsius),
            agg.healthScoreAverage.toString(),
            agg.networkRxBytesTotal.toString(),
            agg.networkTxBytesTotal.toString()
        )
        return buildCsvLine(values, config)
    }

    /**
     * Builds a CSV line from a list of values following RFC 4180.
     *
     * RFC 4180 rules:
     * 1. Fields containing delimiter, quote, or newline must be enclosed in quotes
     * 2. Quote characters within a field must be escaped by doubling them
     * 3. Each record should be on a separate line, delimited by line break (CRLF)
     */
    private fun buildCsvLine(values: List<String>, config: CsvExportConfig): String {
        return values.map { value -> escapeField(value, config) }
            .joinToString(config.delimiter.toString())
    }

    /**
     * Escapes a field value according to RFC 4180.
     */
    private fun escapeField(value: String, config: CsvExportConfig): String {
        val needsQuoting = value.contains(config.delimiter) ||
                value.contains(config.quoteChar) ||
                value.contains('\n') ||
                value.contains('\r')

        return if (needsQuoting) {
            val escaped = value.replace(
                config.quoteChar.toString(),
                "${config.escapeChar}${config.quoteChar}"
            )
            "${config.quoteChar}$escaped${config.quoteChar}"
        } else {
            value
        }
    }

    private fun createDateFormat(config: CsvExportConfig): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", config.locale).apply {
            timeZone = TimeZone.getTimeZone(config.timezone)
        }
    }

    private fun formatFloat(value: Float): String {
        return if (value == value.toLong().toFloat()) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value)
        }
    }

    public companion object {
        private const val TAG = "CsvExporter"
        
        /** UTF-8 Byte Order Mark for Excel compatibility */
        public const val UTF8_BOM: String = "\uFEFF"

        /** Headers for raw metrics CSV */
        public val RAW_METRICS_HEADERS: List<String> = listOf(
            "Timestamp",
            "CPU (%)",
            "Memory (%)",
            "Battery (%)",
            "Temperature (°C)",
            "Health Score",
            "Network RX (B/s)",
            "Network TX (B/s)",
            "Network Type",
            "Uptime (ms)"
        )

        /** Headers for aggregated metrics CSV */
        public val AGGREGATED_METRICS_HEADERS: List<String> = listOf(
            "Time Window",
            "Start Time",
            "End Time",
            "Samples",
            "CPU Avg (%)",
            "CPU Min (%)",
            "CPU Max (%)",
            "Memory Avg (%)",
            "Memory Min (%)",
            "Memory Max (%)",
            "Battery Avg (%)",
            "Temperature (°C)",
            "Health Score Avg",
            "Network RX Total (B)",
            "Network TX Total (B)"
        )
    }
}

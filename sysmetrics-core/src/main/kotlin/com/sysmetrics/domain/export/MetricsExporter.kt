package com.sysmetrics.domain.export

import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.SystemMetrics

/**
 * Interface for exporting metrics to various formats.
 *
 * Implementations of this interface provide format-specific serialization
 * of metrics data. The Strategy pattern allows easy addition of new formats
 * (CSV, JSON, XML, Protobuf, etc.) without modifying existing code.
 *
 * Thread-safe: Implementations should be stateless and safe to use from
 * multiple threads concurrently.
 *
 * Example implementation:
 * ```kotlin
 * class CsvMetricsExporter : MetricsExporter {
 *     override val mimeType = "text/csv"
 *     override val formatName = "csv"
 *     
 *     override fun exportRawMetrics(metrics, config) = buildString {
 *         // CSV serialization logic
 *     }
 * }
 * ```
 *
 * @see CsvExportConfig for CSV-specific configuration
 */
public interface MetricsExporter {

    /**
     * The MIME type produced by this exporter.
     *
     * Used for HTTP Content-Type headers and file type detection.
     * Examples: "text/csv", "application/json", "application/xml"
     */
    public val mimeType: String

    /**
     * Short format name for registry lookup.
     *
     * Should be lowercase without special characters.
     * Examples: "csv", "json", "xml"
     */
    public val formatName: String

    /**
     * Recommended file extension for this format.
     *
     * Without the leading dot.
     * Examples: "csv", "json", "xml"
     */
    public val fileExtension: String

    /**
     * Exports raw system metrics to the target format.
     *
     * Serializes a list of [SystemMetrics] snapshots into the format
     * specified by this exporter.
     *
     * @param metrics List of system metrics to export
     * @param config Export configuration (use format-specific subclass for additional options)
     * @return Formatted string containing the exported data
     * @throws ExportException if serialization fails
     *
     * Example:
     * ```kotlin
     * val exporter = CsvMetricsExporter()
     * val csv = exporter.exportRawMetrics(
     *     metrics = metricsHistory,
     *     config = CsvExportConfig(includeHeaders = true)
     * )
     * File("metrics.csv").writeText(csv)
     * ```
     */
    public fun exportRawMetrics(
        metrics: List<SystemMetrics>,
        config: ExportConfig = ExportConfig()
    ): String

    /**
     * Exports aggregated metrics to the target format.
     *
     * Serializes a list of [AggregatedMetrics] into the format
     * specified by this exporter. Useful for trend analysis exports.
     *
     * @param aggregated List of aggregated metrics to export
     * @param config Export configuration
     * @return Formatted string containing the exported data
     * @throws ExportException if serialization fails
     *
     * Example:
     * ```kotlin
     * val exporter = CsvMetricsExporter()
     * val csv = exporter.exportAggregatedMetrics(
     *     aggregated = hourlyAggregations,
     *     config = CsvExportConfig(delimiter = ';')
     * )
     * ```
     */
    public fun exportAggregatedMetrics(
        aggregated: List<AggregatedMetrics>,
        config: ExportConfig = ExportConfig()
    ): String

    /**
     * Exports raw metrics to a StringBuilder for streaming/chunked output.
     *
     * Default implementation delegates to [exportRawMetrics].
     * Override for memory-efficient streaming of large datasets.
     *
     * @param metrics List of system metrics to export
     * @param config Export configuration
     * @param output StringBuilder to append the output to
     */
    public fun exportRawMetricsTo(
        metrics: List<SystemMetrics>,
        config: ExportConfig = ExportConfig(),
        output: StringBuilder
    ) {
        output.append(exportRawMetrics(metrics, config))
    }

    /**
     * Exports aggregated metrics to a StringBuilder for streaming/chunked output.
     *
     * Default implementation delegates to [exportAggregatedMetrics].
     * Override for memory-efficient streaming of large datasets.
     *
     * @param aggregated List of aggregated metrics to export
     * @param config Export configuration
     * @param output StringBuilder to append the output to
     */
    public fun exportAggregatedMetricsTo(
        aggregated: List<AggregatedMetrics>,
        config: ExportConfig = ExportConfig(),
        output: StringBuilder
    ) {
        output.append(exportAggregatedMetrics(aggregated, config))
    }
}

/**
 * Exception thrown when metrics export fails.
 *
 * @property format The export format that failed
 * @property reason Description of the failure
 */
public class ExportException(
    public val format: String,
    public val reason: String,
    cause: Throwable? = null
) : Exception("Export to $format failed: $reason", cause)

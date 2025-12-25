package com.sysmetrics.data.export

import com.sysmetrics.domain.export.ExportConfig
import com.sysmetrics.domain.export.ExportException
import com.sysmetrics.domain.export.MetricsExporter
import com.sysmetrics.domain.model.AggregatedMetrics
import com.sysmetrics.domain.model.SystemMetrics

/**
 * Manages metrics export operations with multiple format support.
 *
 * ExportManager provides a registry of exporters and handles format selection,
 * error handling, and result wrapping. It follows the dependency injection
 * pattern for extensibility.
 *
 * Thread-safe: All operations are thread-safe when using thread-safe exporters.
 *
 * Example:
 * ```kotlin
 * val manager = ExportManager(
 *     listOf(CsvMetricsExporter(), JsonMetricsExporter())
 * )
 * 
 * val result = manager.exportRawMetrics(
 *     metrics = metricsHistory,
 *     format = "csv",
 *     config = CsvExportConfig.forExcel()
 * )
 * ```
 *
 * @param exporters Map of format names to exporter implementations
 */
public class ExportManager private constructor(
    private val exporters: Map<String, MetricsExporter>
) {

    /**
     * Creates an ExportManager from a list of exporters.
     *
     * Exporters are registered by their [MetricsExporter.formatName].
     * If multiple exporters have the same format name, the last one wins.
     *
     * @param exporterList List of exporters to register
     */
    public constructor(exporterList: List<MetricsExporter>) : this(
        exporterList.associateBy { it.formatName.lowercase() }
    )

    /**
     * Creates an ExportManager with default exporters.
     *
     * Default exporters: CSV
     */
    public constructor() : this(listOf(CsvMetricsExporter()))

    /**
     * Exports raw system metrics to the specified format.
     *
     * @param metrics List of system metrics to export
     * @param format Format name (e.g., "csv", "json")
     * @param config Export configuration
     * @return [Result.success] with exported string or [Result.failure] with exception
     *
     * Example:
     * ```kotlin
     * manager.exportRawMetrics(metrics, "csv", CsvExportConfig())
     *     .onSuccess { csv -> saveToFile(csv) }
     *     .onFailure { error -> showError(error) }
     * ```
     */
    public fun exportRawMetrics(
        metrics: List<SystemMetrics>,
        format: String,
        config: ExportConfig = ExportConfig()
    ): Result<String> = safeCall(format) {
        val exporter = getExporter(format)
        exporter.exportRawMetrics(metrics, config)
    }

    /**
     * Exports aggregated metrics to the specified format.
     *
     * @param aggregated List of aggregated metrics to export
     * @param format Format name (e.g., "csv", "json")
     * @param config Export configuration
     * @return [Result.success] with exported string or [Result.failure] with exception
     */
    public fun exportAggregatedMetrics(
        aggregated: List<AggregatedMetrics>,
        format: String,
        config: ExportConfig = ExportConfig()
    ): Result<String> = safeCall(format) {
        val exporter = getExporter(format)
        exporter.exportAggregatedMetrics(aggregated, config)
    }

    /**
     * Exports raw metrics to a StringBuilder for streaming output.
     *
     * Useful for memory-efficient export of large datasets.
     *
     * @param metrics List of system metrics to export
     * @param format Format name
     * @param config Export configuration
     * @param output StringBuilder to append the output to
     * @return [Result.success] if successful, [Result.failure] with exception otherwise
     */
    public fun exportRawMetricsTo(
        metrics: List<SystemMetrics>,
        format: String,
        config: ExportConfig = ExportConfig(),
        output: StringBuilder
    ): Result<Unit> = safeCall(format) {
        val exporter = getExporter(format)
        exporter.exportRawMetricsTo(metrics, config, output)
    }

    /**
     * Exports aggregated metrics to a StringBuilder for streaming output.
     *
     * @param aggregated List of aggregated metrics to export
     * @param format Format name
     * @param config Export configuration
     * @param output StringBuilder to append the output to
     * @return [Result.success] if successful, [Result.failure] with exception otherwise
     */
    public fun exportAggregatedMetricsTo(
        aggregated: List<AggregatedMetrics>,
        format: String,
        config: ExportConfig = ExportConfig(),
        output: StringBuilder
    ): Result<Unit> = safeCall(format) {
        val exporter = getExporter(format)
        exporter.exportAggregatedMetricsTo(aggregated, config, output)
    }

    /**
     * Returns the list of supported export format names.
     */
    public fun getSupportedFormats(): List<String> = exporters.keys.toList()

    /**
     * Returns the MIME type for a given format.
     *
     * @param format Format name
     * @return MIME type string or null if format is not supported
     */
    public fun getMimeType(format: String): String? = exporters[format.lowercase()]?.mimeType

    /**
     * Returns the file extension for a given format.
     *
     * @param format Format name
     * @return File extension (without dot) or null if format is not supported
     */
    public fun getFileExtension(format: String): String? = exporters[format.lowercase()]?.fileExtension

    /**
     * Checks if a format is supported.
     *
     * @param format Format name to check
     * @return true if format is supported, false otherwise
     */
    public fun isFormatSupported(format: String): Boolean = exporters.containsKey(format.lowercase())

    /**
     * Returns the exporter for a given format.
     *
     * @param format Format name
     * @return The exporter instance
     * @throws ExportException if format is not supported
     */
    private fun getExporter(format: String): MetricsExporter {
        return exporters[format.lowercase()]
            ?: throw ExportException(
                format = format,
                reason = "Unsupported format. Supported formats: ${exporters.keys.joinToString()}"
            )
    }

    /**
     * Wraps an operation in try-catch and returns Result.
     */
    private fun <T> safeCall(format: String, block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: ExportException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(ExportException(format, e.message ?: "Unknown error", e))
        }
    }

    public companion object {
        /**
         * Creates an ExportManager with all available exporters.
         */
        public fun withAllExporters(): ExportManager = ExportManager(
            listOf(
                CsvMetricsExporter()
                // Add more exporters here as they are implemented
            )
        )

        /**
         * Creates an ExportManager with only CSV exporter.
         */
        public fun csvOnly(): ExportManager = ExportManager(
            listOf(CsvMetricsExporter())
        )
    }
}

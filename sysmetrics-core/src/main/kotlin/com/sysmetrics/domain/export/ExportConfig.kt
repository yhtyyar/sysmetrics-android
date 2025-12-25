package com.sysmetrics.domain.export

import java.time.ZoneId
import java.util.Locale

/**
 * Base configuration for metrics export.
 *
 * Contains common settings applicable to all export formats.
 * Use format-specific subclasses for additional options.
 *
 * @property includeHeaders Whether to include column headers in output
 * @property includeTimestamp Whether to include timestamp information
 * @property timezone Timezone for formatting date/time values
 * @property locale Locale for number/date formatting
 *
 * Example:
 * ```kotlin
 * val config = ExportConfig(
 *     includeHeaders = true,
 *     timezone = ZoneId.of("Europe/Moscow")
 * )
 * ```
 */
public open class ExportConfig(
    public open val includeHeaders: Boolean = true,
    public open val includeTimestamp: Boolean = true,
    public open val timezone: ZoneId = ZoneId.systemDefault(),
    public open val locale: Locale = Locale.US
)

/**
 * CSV-specific export configuration.
 *
 * Extends [ExportConfig] with options specific to CSV format.
 * Follows RFC 4180 specification for CSV files.
 *
 * @property delimiter Field delimiter character (default: comma)
 * @property includeUtf8Bom Whether to include UTF-8 BOM for Excel compatibility
 * @property quoteChar Character used for quoting fields
 * @property escapeChar Character used for escaping quotes within fields
 * @property lineEnding Line ending style (CRLF for RFC 4180 compliance)
 *
 * Example:
 * ```kotlin
 * val csvConfig = CsvExportConfig(
 *     delimiter = ';',  // European style
 *     includeUtf8Bom = true,
 *     includeHeaders = true
 * )
 * ```
 */
public data class CsvExportConfig(
    val delimiter: Char = ',',
    val includeUtf8Bom: Boolean = true,
    val quoteChar: Char = '"',
    val escapeChar: Char = '"',
    val lineEnding: String = "\r\n",
    override val includeHeaders: Boolean = true,
    override val includeTimestamp: Boolean = true,
    override val timezone: ZoneId = ZoneId.systemDefault(),
    override val locale: Locale = Locale.US
) : ExportConfig(includeHeaders, includeTimestamp, timezone, locale) {

    public companion object {
        /**
         * Creates a CSV config optimized for Microsoft Excel.
         * Uses UTF-8 BOM and standard comma delimiter.
         */
        public fun forExcel(): CsvExportConfig = CsvExportConfig(
            includeUtf8Bom = true,
            delimiter = ',',
            lineEnding = "\r\n"
        )

        /**
         * Creates a CSV config for European locales.
         * Uses semicolon delimiter (common in countries using comma as decimal separator).
         */
        public fun forEuropean(): CsvExportConfig = CsvExportConfig(
            delimiter = ';',
            includeUtf8Bom = true,
            locale = Locale.GERMANY
        )

        /**
         * Creates a minimal CSV config for programmatic use.
         * No BOM, no headers, Unix line endings.
         */
        public fun minimal(): CsvExportConfig = CsvExportConfig(
            includeUtf8Bom = false,
            includeHeaders = false,
            lineEnding = "\n"
        )
    }
}

/**
 * JSON-specific export configuration.
 *
 * Extends [ExportConfig] with options specific to JSON format.
 *
 * @property prettyPrint Whether to format JSON with indentation
 * @property indent Indentation string (spaces or tabs)
 * @property includeNulls Whether to include null fields in output
 *
 * Example:
 * ```kotlin
 * val jsonConfig = JsonExportConfig(
 *     prettyPrint = true,
 *     indent = "  "
 * )
 * ```
 */
public data class JsonExportConfig(
    val prettyPrint: Boolean = true,
    val indent: String = "  ",
    val includeNulls: Boolean = false,
    override val includeHeaders: Boolean = true,
    override val includeTimestamp: Boolean = true,
    override val timezone: ZoneId = ZoneId.systemDefault(),
    override val locale: Locale = Locale.US
) : ExportConfig(includeHeaders, includeTimestamp, timezone, locale) {

    public companion object {
        /**
         * Creates a compact JSON config without formatting.
         */
        public fun compact(): JsonExportConfig = JsonExportConfig(
            prettyPrint = false
        )

        /**
         * Creates a pretty-printed JSON config for readability.
         */
        public fun pretty(): JsonExportConfig = JsonExportConfig(
            prettyPrint = true,
            indent = "  "
        )
    }
}

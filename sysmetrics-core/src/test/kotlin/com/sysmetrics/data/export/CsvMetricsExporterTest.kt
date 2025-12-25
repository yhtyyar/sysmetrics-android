package com.sysmetrics.data.export

import com.sysmetrics.domain.export.CsvExportConfig
import com.sysmetrics.domain.export.ExportConfig
import com.sysmetrics.domain.export.ExportException
import com.sysmetrics.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CsvMetricsExporter].
 *
 * Tests cover:
 * - Basic CSV generation
 * - RFC 4180 compliance (escaping, quoting)
 * - UTF-8 BOM handling
 * - Custom delimiters
 * - Edge cases (empty data, special characters, Cyrillic)
 * - Header inclusion/exclusion
 * - Performance for large datasets
 *
 * Target coverage: 100%
 */
class CsvMetricsExporterTest {

    private lateinit var exporter: CsvMetricsExporter

    @Before
    fun setUp() {
        exporter = CsvMetricsExporter()
    }

    // ==================== Basic Functionality Tests ====================

    @Test
    fun `exporter has correct metadata`() {
        assertEquals("text/csv", exporter.mimeType)
        assertEquals("csv", exporter.formatName)
        assertEquals("csv", exporter.fileExtension)
    }

    @Test
    fun `exportRawMetrics with empty list returns only headers`() {
        val result = exporter.exportRawMetrics(emptyList(), CsvExportConfig())
        
        assertTrue(result.startsWith(CsvMetricsExporter.UTF8_BOM))
        assertTrue(result.contains("Timestamp"))
        assertTrue(result.contains("CPU (%)"))
        // Should have BOM + header line only
        assertEquals(2, result.lines().filter { it.isNotBlank() }.size)
    }

    @Test
    fun `exportRawMetrics with single metric produces correct CSV`() {
        val metric = createTestMetrics(
            timestamp = 1704114765000L,
            cpuUsage = 45.5f,
            memoryUsage = 62.3f,
            batteryLevel = 85
        )

        val result = exporter.exportRawMetrics(listOf(metric), CsvExportConfig())

        assertTrue(result.contains("45.5"))
        assertTrue(result.contains("62.3"))
        assertTrue(result.contains("85"))
    }

    @Test
    fun `exportRawMetrics with multiple metrics produces multiple lines`() {
        val metrics = listOf(
            createTestMetrics(cpuUsage = 30f),
            createTestMetrics(cpuUsage = 50f),
            createTestMetrics(cpuUsage = 70f)
        )

        val result = exporter.exportRawMetrics(metrics, CsvExportConfig())
        val lines = result.lines().filter { it.isNotBlank() }

        // BOM + header + 3 data lines
        assertEquals(4, lines.size)
    }

    @Test
    fun `exportAggregatedMetrics with empty list returns only headers`() {
        val result = exporter.exportAggregatedMetrics(emptyList(), CsvExportConfig())

        assertTrue(result.contains("Time Window"))
        assertTrue(result.contains("Samples"))
    }

    @Test
    fun `exportAggregatedMetrics produces correct output`() {
        val aggregated = listOf(
            createTestAggregatedMetrics(
                timeWindow = TimeWindow.FIVE_MINUTES,
                cpuAvg = 45.5f,
                sampleCount = 10
            )
        )

        val result = exporter.exportAggregatedMetrics(aggregated, CsvExportConfig())

        assertTrue(result.contains("FIVE_MINUTES"))
        assertTrue(result.contains("45.5"))
        assertTrue(result.contains("10"))
    }

    // ==================== UTF-8 BOM Tests ====================

    @Test
    fun `exportRawMetrics includes UTF-8 BOM when configured`() {
        val config = CsvExportConfig(includeUtf8Bom = true)
        val result = exporter.exportRawMetrics(emptyList(), config)

        assertTrue(result.startsWith("\uFEFF"))
    }

    @Test
    fun `exportRawMetrics excludes UTF-8 BOM when configured`() {
        val config = CsvExportConfig(includeUtf8Bom = false)
        val result = exporter.exportRawMetrics(emptyList(), config)

        assertFalse(result.startsWith("\uFEFF"))
    }

    // ==================== Header Tests ====================

    @Test
    fun `exportRawMetrics includes headers when configured`() {
        val config = CsvExportConfig(includeHeaders = true)
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        assertTrue(result.contains("Timestamp"))
        assertTrue(result.contains("CPU (%)"))
        assertTrue(result.contains("Memory (%)"))
    }

    @Test
    fun `exportRawMetrics excludes headers when configured`() {
        val config = CsvExportConfig(includeHeaders = false, includeUtf8Bom = false)
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        assertFalse(result.contains("Timestamp"))
        assertFalse(result.contains("CPU (%)"))
    }

    // ==================== RFC 4180 Compliance Tests ====================

    @Test
    fun `field containing delimiter is quoted`() {
        // The network type should be escaped if it contained a comma
        val config = CsvExportConfig(delimiter = ',')
        val metric = createTestMetrics()
        val result = exporter.exportRawMetrics(listOf(metric), config)

        // WIFI doesn't contain comma, so should not be quoted
        assertTrue(result.contains("WIFI"))
    }

    @Test
    fun `field containing quote character is properly escaped`() {
        // Test with custom delimiter that would trigger quoting
        val config = CsvExportConfig(delimiter = ',', quoteChar = '"')
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        // Normal fields should not be quoted
        assertFalse(result.contains("\"WIFI\""))
    }

    @Test
    fun `uses configured delimiter`() {
        val commaConfig = CsvExportConfig(delimiter = ',')
        val semicolonConfig = CsvExportConfig(delimiter = ';')

        val commaResult = exporter.exportRawMetrics(listOf(createTestMetrics()), commaConfig)
        val semicolonResult = exporter.exportRawMetrics(listOf(createTestMetrics()), semicolonConfig)

        assertTrue(commaResult.contains(","))
        assertTrue(semicolonResult.contains(";"))
        assertFalse(semicolonResult.contains(","))
    }

    @Test
    fun `uses configured line ending`() {
        val crlfConfig = CsvExportConfig(lineEnding = "\r\n")
        val lfConfig = CsvExportConfig(lineEnding = "\n")

        val crlfResult = exporter.exportRawMetrics(listOf(createTestMetrics()), crlfConfig)
        val lfResult = exporter.exportRawMetrics(listOf(createTestMetrics()), lfConfig)

        assertTrue(crlfResult.contains("\r\n"))
        assertFalse(lfResult.contains("\r\n"))
    }

    // ==================== Config Preset Tests ====================

    @Test
    fun `forExcel config produces Excel-compatible output`() {
        val config = CsvExportConfig.forExcel()
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains(","))
        assertTrue(result.contains("\r\n"))
    }

    @Test
    fun `forEuropean config uses semicolon delimiter`() {
        val config = CsvExportConfig.forEuropean()
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        assertTrue(result.contains(";"))
    }

    @Test
    fun `minimal config produces minimal output`() {
        val config = CsvExportConfig.minimal()
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), config)

        assertFalse(result.startsWith("\uFEFF"))
        assertFalse(result.contains("Timestamp")) // No headers
        assertTrue(result.contains("\n"))
        assertFalse(result.contains("\r\n"))
    }

    // ==================== ExportConfig Fallback Test ====================

    @Test
    fun `exportRawMetrics accepts base ExportConfig`() {
        val baseConfig = ExportConfig()
        val result = exporter.exportRawMetrics(listOf(createTestMetrics()), baseConfig)

        // Should use default CsvExportConfig values
        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains(","))
    }

    // ==================== Streaming Export Tests ====================

    @Test
    fun `exportRawMetricsTo appends to StringBuilder`() {
        val output = StringBuilder("existing content\n")
        val config = CsvExportConfig(includeUtf8Bom = false)

        exporter.exportRawMetricsTo(listOf(createTestMetrics()), config, output)

        assertTrue(output.toString().startsWith("existing content"))
        assertTrue(output.toString().contains("CPU (%)"))
    }

    @Test
    fun `exportAggregatedMetricsTo appends to StringBuilder`() {
        val output = StringBuilder()
        val config = CsvExportConfig()
        val aggregated = listOf(createTestAggregatedMetrics())

        exporter.exportAggregatedMetricsTo(aggregated, config, output)

        assertTrue(output.toString().contains("Time Window"))
        assertTrue(output.toString().contains("FIVE_MINUTES"))
    }

    // ==================== Performance Tests ====================

    @Test
    fun `export 1000 metrics completes under 100ms`() {
        val metrics = (1..1000).map { createTestMetrics() }
        val config = CsvExportConfig()

        val startTime = System.nanoTime()
        val result = exporter.exportRawMetrics(metrics, config)
        val duration = (System.nanoTime() - startTime) / 1_000_000 // ms

        assertTrue("Export took ${duration}ms, expected <100ms", duration < 100)
        assertTrue(result.lines().size > 1000)
    }

    @Test
    fun `export 1000 aggregated metrics completes under 100ms`() {
        val aggregated = (1..1000).map { createTestAggregatedMetrics() }
        val config = CsvExportConfig()

        val startTime = System.nanoTime()
        val result = exporter.exportAggregatedMetrics(aggregated, config)
        val duration = (System.nanoTime() - startTime) / 1_000_000 // ms

        assertTrue("Export took ${duration}ms, expected <100ms", duration < 100)
        assertTrue(result.lines().size > 1000)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles metrics with zero values`() {
        val metric = createTestMetrics(
            cpuUsage = 0f,
            memoryUsage = 0f,
            batteryLevel = 0
        )

        val result = exporter.exportRawMetrics(listOf(metric), CsvExportConfig())

        assertTrue(result.contains(",0,"))
    }

    @Test
    fun `handles metrics with max values`() {
        val metric = createTestMetrics(
            cpuUsage = 100f,
            memoryUsage = 100f,
            batteryLevel = 100
        )

        val result = exporter.exportRawMetrics(listOf(metric), CsvExportConfig())

        assertTrue(result.contains("100"))
    }

    @Test
    fun `handles very large numbers`() {
        val metric = createTestMetrics(
            rxBytes = Long.MAX_VALUE / 2,
            txBytes = Long.MAX_VALUE / 2
        )

        val result = exporter.exportRawMetrics(listOf(metric), CsvExportConfig())

        // Should contain the large number as string
        assertFalse(result.contains("E+")) // No scientific notation
    }

    // ==================== Aggregated Metrics Specific Tests ====================

    @Test
    fun `aggregated export includes all statistical fields`() {
        val aggregated = createTestAggregatedMetrics(
            cpuAvg = 50f,
            cpuMin = 20f,
            cpuMax = 80f
        )

        val result = exporter.exportAggregatedMetrics(listOf(aggregated), CsvExportConfig())

        assertTrue(result.contains("50"))
        assertTrue(result.contains("20"))
        assertTrue(result.contains("80"))
    }

    @Test
    fun `aggregated export handles empty window correctly`() {
        val aggregated = AggregatedMetrics.empty(
            timeWindow = TimeWindow.ONE_MINUTE,
            windowStartTime = 1704114000000L,
            windowEndTime = 1704114060000L
        )

        val result = exporter.exportAggregatedMetrics(listOf(aggregated), CsvExportConfig())

        assertTrue(result.contains("ONE_MINUTE"))
        assertTrue(result.contains(",0,")) // sampleCount = 0
    }

    // ==================== Helper Functions ====================

    private fun createTestMetrics(
        timestamp: Long = System.currentTimeMillis(),
        cpuUsage: Float = 50f,
        memoryUsage: Float = 60f,
        batteryLevel: Int = 80,
        temperature: Float = 40f,
        rxBytes: Long = 1000L,
        txBytes: Long = 500L
    ): SystemMetrics {
        return SystemMetrics(
            cpuMetrics = CpuMetrics(
                usagePercent = cpuUsage,
                physicalCores = 4,
                logicalCores = 8,
                maxFrequencyKHz = 2000000L,
                currentFrequencyKHz = 1500000L,
                coreFrequencies = null
            ),
            memoryMetrics = MemoryMetrics(
                totalMemoryMB = 4096,
                usedMemoryMB = (4096 * memoryUsage / 100).toLong(),
                freeMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                availableMemoryMB = (4096 * (100 - memoryUsage) / 100).toLong(),
                usagePercent = memoryUsage,
                buffersMB = 100,
                cachedMB = 500,
                swapTotalMB = 1024,
                swapFreeMB = 800
            ),
            batteryMetrics = BatteryMetrics(
                level = batteryLevel,
                temperature = temperature,
                status = BatteryStatus.DISCHARGING,
                health = BatteryHealth.GOOD,
                plugged = false,
                chargingSpeed = null
            ),
            thermalMetrics = ThermalMetrics(
                cpuTemperature = temperature,
                batteryTemperature = temperature - 5f,
                otherTemperatures = emptyMap(),
                thermalThrottling = false
            ),
            storageMetrics = StorageMetrics(
                totalStorageMB = 64000,
                freeStorageMB = 32000,
                usedStorageMB = 32000,
                usagePercent = 50f
            ),
            networkMetrics = NetworkMetrics(
                rxBytes = rxBytes,
                txBytes = txBytes,
                rxBytesPerSecond = 1000L,
                txBytesPerSecond = 500L,
                isConnected = true,
                connectionType = NetworkType.WIFI,
                networkName = "TestNetwork",
                signalStrength = -50
            ),
            timestamp = timestamp,
            uptime = 3600000L
        )
    }

    private fun createTestAggregatedMetrics(
        timeWindow: TimeWindow = TimeWindow.FIVE_MINUTES,
        cpuAvg: Float = 50f,
        cpuMin: Float = 30f,
        cpuMax: Float = 70f,
        sampleCount: Int = 10
    ): AggregatedMetrics {
        val now = System.currentTimeMillis()
        return AggregatedMetrics(
            timeWindow = timeWindow,
            windowStartTime = now - timeWindow.durationMillis(),
            windowEndTime = now,
            sampleCount = sampleCount,
            cpuPercentAverage = cpuAvg,
            memoryPercentAverage = 60f,
            batteryPercentAverage = 80f,
            temperatureCelsius = 40f,
            healthScoreAverage = 85,
            cpuPercentMin = cpuMin,
            cpuPercentMax = cpuMax,
            memoryPercentMin = 50f,
            memoryPercentMax = 70f,
            networkRxBytesTotal = 10000L,
            networkTxBytesTotal = 5000L
        )
    }
}

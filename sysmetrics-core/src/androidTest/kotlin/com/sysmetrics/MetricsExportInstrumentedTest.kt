package com.sysmetrics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysmetrics.domain.export.CsvExportConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for metrics export functionality.
 *
 * These tests verify:
 * - CSV export with real metrics data
 * - UTF-8 BOM handling
 * - Export configuration options
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MetricsExportInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SysMetrics.initialize(context)
    }

    @After
    fun tearDown() {
        runBlocking {
            SysMetrics.destroy()
        }
    }

    @Test
    fun testExportMetricsToCsv() = runBlocking {
        // Collect some metrics first
        repeat(3) {
            SysMetrics.getCurrentMetrics()
        }

        val result = SysMetrics.exportMetrics(CsvExportConfig.forExcel())
        
        assertTrue("Export should succeed", result.isSuccess)
        
        result.getOrNull()?.let { csvData ->
            assertTrue("CSV should not be empty", csvData.isNotEmpty())
            assertTrue("CSV should start with UTF-8 BOM", csvData.startsWith("\uFEFF"))
            assertTrue("CSV should contain headers", csvData.contains("Timestamp"))
            assertTrue("CSV should contain CPU header", csvData.contains("CPU"))
        }
    }

    @Test
    fun testExportWithDifferentDelimiters() = runBlocking {
        // Collect metrics
        SysMetrics.getCurrentMetrics()

        val commaConfig = CsvExportConfig(delimiter = ',')
        val semicolonConfig = CsvExportConfig(delimiter = ';')

        val commaResult = SysMetrics.exportMetrics(commaConfig)
        val semicolonResult = SysMetrics.exportMetrics(semicolonConfig)

        assertTrue("Comma export should succeed", commaResult.isSuccess)
        assertTrue("Semicolon export should succeed", semicolonResult.isSuccess)

        commaResult.getOrNull()?.let { csv ->
            assertTrue("Should contain comma delimiter", csv.contains(","))
        }

        semicolonResult.getOrNull()?.let { csv ->
            assertTrue("Should contain semicolon delimiter", csv.contains(";"))
        }
    }

    @Test
    fun testExportWithoutBom() = runBlocking {
        SysMetrics.getCurrentMetrics()

        val config = CsvExportConfig(includeUtf8Bom = false)
        val result = SysMetrics.exportMetrics(config)

        assertTrue("Export should succeed", result.isSuccess)
        
        result.getOrNull()?.let { csv ->
            assertFalse("Should not start with BOM", csv.startsWith("\uFEFF"))
        }
    }

    @Test
    fun testExportWithoutHeaders() = runBlocking {
        SysMetrics.getCurrentMetrics()

        val config = CsvExportConfig(includeHeaders = false, includeUtf8Bom = false)
        val result = SysMetrics.exportMetrics(config)

        assertTrue("Export should succeed", result.isSuccess)
        
        result.getOrNull()?.let { csv ->
            assertFalse("Should not contain Timestamp header", csv.contains("Timestamp"))
        }
    }

    @Test
    fun testExportEuropeanFormat() = runBlocking {
        SysMetrics.getCurrentMetrics()

        val config = CsvExportConfig.forEuropean()
        val result = SysMetrics.exportMetrics(config)

        assertTrue("Export should succeed", result.isSuccess)
        
        result.getOrNull()?.let { csv ->
            assertTrue("Should use semicolon delimiter", csv.contains(";"))
        }
    }

    @Test
    fun testExportMinimalFormat() = runBlocking {
        SysMetrics.getCurrentMetrics()

        val config = CsvExportConfig.minimal()
        val result = SysMetrics.exportMetrics(config)

        assertTrue("Export should succeed", result.isSuccess)
        
        result.getOrNull()?.let { csv ->
            assertFalse("Should not have BOM", csv.startsWith("\uFEFF"))
            assertFalse("Should not have headers", csv.contains("Timestamp"))
        }
    }
}

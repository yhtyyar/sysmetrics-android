# SysMetrics Android Library

[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.10-blue.svg)](https://kotlinlang.org)

A production-ready Android library for collecting comprehensive system metrics including CPU, memory, battery, thermal, storage, and network information.

## 📚 Documentation | Документация

| Language | Link |
|----------|------|
| 🇬🇧 **English** | [Full Documentation](docs/DOCUMENTATION_EN.md) |
| 🇷🇺 **Русский** | [Полная документация](docs/DOCUMENTATION_RU.md) |

## Features

- 📊 **Comprehensive Metrics** - CPU, Memory, Battery, Thermal, Storage, and Network
- 🔄 **Real-time Streaming** - Flow-based reactive API
- 💪 **Health Scoring** - Automatic system health assessment
- 🏗️ **Clean Architecture** - Domain/Data/Infrastructure layers
- 🔒 **Thread-safe** - Safe concurrent access
- ⚡ **High Performance** - <5ms latency, <5MB memory
- 🎯 **Zero Dependencies** - Only Kotlin stdlib, Coroutines, Serialization
- 📤 **Data Export** - CSV and JSON export functionality
- 🖥️ **Debug Overlay** - In-app HUD for real-time metrics visualization (optional module)

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core metrics library
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
    
    // Optional: Debug overlay (HUD)
    debugImplementation("com.sysmetrics:sysmetrics-overlay:1.0.0")
}
```

## Quick Start

### Initialize

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SysMetrics.initialize(this)
    }
}
```

### Get Current Metrics

```kotlin
lifecycleScope.launch {
    SysMetrics.getCurrentMetrics()
        .onSuccess { metrics ->
            println("CPU Usage: ${metrics.cpuMetrics.usagePercent}%")
            println("Memory Usage: ${metrics.memoryMetrics.usagePercent}%")
            println("Battery Level: ${metrics.batteryMetrics.level}%")
        }
        .onFailure { error ->
            Log.e("SysMetrics", "Failed to get metrics", error)
        }
}
```

### Observe Metrics Stream

```kotlin
lifecycleScope.launch {
    SysMetrics.observeMetrics(intervalMs = 1000)
        .collect { metrics ->
            updateUI(metrics)
        }
}
```

### Monitor Health Score

```kotlin
lifecycleScope.launch {
    SysMetrics.observeHealthScore()
        .collect { healthScore ->
            when (healthScore.status) {
                HealthStatus.EXCELLENT -> showGreenIndicator()
                HealthStatus.GOOD -> showYellowIndicator()
                HealthStatus.WARNING -> showOrangeIndicator()
                HealthStatus.CRITICAL -> showRedIndicator()
            }
            
            // Display recommendations
            healthScore.recommendations.forEach { recommendation ->
                showRecommendation(recommendation)
            }
        }
}
```

### Cleanup

```kotlin
override fun onTerminate() {
    super.onTerminate()
    runBlocking {
        SysMetrics.destroy()
    }
}
```

### Debug Overlay (Optional)

Show real-time metrics overlay in your app:

```kotlin
class MainActivity : AppCompatActivity() {
    private var overlayHandle: OverlayHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Attach overlay (debug builds only by default)
        overlayHandle = SysMetricsOverlay.attach(this)
    }

    override fun onDestroy() {
        overlayHandle?.detach()
        super.onDestroy()
    }
}
```

The overlay shows FPS, CPU%, RAM%, and network speed. Tap "▼ More" to expand and see all metrics. See [OVERLAY_GUIDE.md](docs/OVERLAY_GUIDE.md) for full documentation.

## API Reference

### SysMetrics Singleton

| Method | Description |
|--------|-------------|
| `initialize(context, logger)` | Initialize the library with optional logger |
| `getCurrentMetrics()` | Get current system metrics snapshot |
| `observeMetrics(intervalMs)` | Stream metrics at specified interval |
| `observeHealthScore()` | Stream health score updates |
| `getMetricsHistory(count)` | Get historical metrics |
| `getAggregatedMetrics(timeWindow)` | Get aggregated metrics for a time window |
| `getAggregatedHistory(timeWindow, count)` | Get historical aggregated metrics |
| `exportMetrics(metrics, format, config)` | Export raw metrics to CSV/JSON |
| `exportAggregatedMetrics(aggregated, format)` | Export aggregated metrics |
| `getSupportedExportFormats()` | Get list of supported export formats |
| `clearHistory()` | Clear metrics history |
| `destroy()` | Release all resources |

### Data Classes

- **SystemMetrics** - Complete metrics snapshot
- **CpuMetrics** - CPU usage, cores, frequencies
- **MemoryMetrics** - RAM usage, available, cached
- **BatteryMetrics** - Level, temperature, status, health
- **ThermalMetrics** - CPU/battery temperature, throttling
- **StorageMetrics** - Storage capacity and usage
- **NetworkMetrics** - Network traffic and connection info
- **HealthScore** - Overall system health assessment
- **AggregatedMetrics** - Aggregated statistics over time window
- **TimeWindow** - Time window for aggregation (1min, 5min, 30min, 1hour)
- **ExportConfig** - Base configuration for metrics export
- **CsvExportConfig** - CSV-specific export configuration (RFC 4180)
- **MetricsLogger** - Logging interface for diagnostics
- **LogLevel** - Log level enumeration (DEBUG, INFO, WARN, ERROR)

### Enums

- **HealthStatus** - EXCELLENT, GOOD, WARNING, CRITICAL
- **BatteryStatus** - UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL
- **BatteryHealth** - UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD
- **HealthIssue** - HIGH_CPU_USAGE, HIGH_MEMORY_USAGE, HIGH_TEMPERATURE, LOW_BATTERY, THERMAL_THROTTLING, LOW_STORAGE, POOR_PERFORMANCE

## Advanced Features

### Metrics Aggregation

Aggregate metrics over configurable time windows for trend analysis:

```kotlin
// Get aggregated metrics for the last 5-minute window
lifecycleScope.launch {
    SysMetrics.getAggregatedMetrics(TimeWindow.FIVE_MINUTES)
        .onSuccess { aggregated ->
            println("Avg CPU: ${aggregated.cpuPercentAverage}%")
            println("Min CPU: ${aggregated.cpuPercentMin}%")
            println("Max CPU: ${aggregated.cpuPercentMax}%")
            println("Samples: ${aggregated.sampleCount}")
            
            // Use for chart drawing
            drawCpuGauge(aggregated.cpuPercentAverage)
        }
}

// Get historical aggregated data (e.g., last hour as 12 five-minute windows)
lifecycleScope.launch {
    SysMetrics.getAggregatedHistory(TimeWindow.FIVE_MINUTES, count = 12)
        .onSuccess { history ->
            val cpuTrend = history.map { it.cpuPercentAverage }
            val memoryTrend = history.map { it.memoryPercentAverage }
            
            drawTrendChart(cpuTrend, memoryTrend)
        }
}
```

Available time windows: `ONE_MINUTE`, `FIVE_MINUTES`, `THIRTY_MINUTES`, `ONE_HOUR`

### Data Export

Export metrics to CSV format (RFC 4180 compliant):

```kotlin
// Export raw metrics to CSV
lifecycleScope.launch {
    val history = SysMetrics.getMetricsHistory(100).getOrNull() ?: emptyList()
    
    SysMetrics.exportMetrics(
        metrics = history,
        format = "csv",
        config = CsvExportConfig.forExcel() // UTF-8 BOM, comma delimiter
    ).onSuccess { csv ->
        // Save to file
        File(context.cacheDir, "metrics.csv").writeText(csv)
        
        // Or share
        shareFile("metrics.csv", csv, "text/csv")
    }
}

// Export aggregated metrics
lifecycleScope.launch {
    val aggregated = SysMetrics.getAggregatedHistory(TimeWindow.FIVE_MINUTES, 12)
        .getOrNull() ?: emptyList()
    
    SysMetrics.exportAggregatedMetrics(aggregated, "csv")
        .onSuccess { csv ->
            saveToDownloads("hourly_report.csv", csv)
        }
}

// Custom CSV configuration
val europeanConfig = CsvExportConfig(
    delimiter = ';',           // Semicolon for European locales
    includeUtf8Bom = true,     // Excel compatibility
    includeHeaders = true,
    lineEnding = "\r\n"        // RFC 4180 compliant
)
```

### Logging Configuration

Configure logging for debugging and monitoring:

```kotlin
// Default logging (INFO level)
SysMetrics.initialize(context)

// Development mode (DEBUG level - verbose logging)
SysMetrics.initialize(
    context,
    logger = AndroidMetricsLogger.forDevelopment()
)

// Production mode (WARN level - minimal logging)
SysMetrics.initialize(
    context,
    logger = AndroidMetricsLogger.forProduction()
)

// Custom log level
SysMetrics.initialize(
    context,
    logger = AndroidMetricsLogger(
        tag = "MyApp",
        minLevel = LogLevel.DEBUG
    )
)

// File logging with rotation
val logFile = File(context.filesDir, "sysmetrics.log")
val fileLogger = FileMetricsLogger(
    logFile = logFile,
    maxSizeBytes = 5 * 1024 * 1024,  // 5 MB
    maxBackupFiles = 3
)
SysMetrics.initialize(context, fileLogger)

// Combined logging (Android + File)
val compositeLogger = CompositeMetricsLogger(
    AndroidMetricsLogger(),
    FileMetricsLogger(logFile)
)
SysMetrics.initialize(context, compositeLogger)

// Disable logging completely
SysMetrics.initialize(context, NoOpLogger)
```

## Health Score Calculation

The health score (0-100) is calculated using weighted factors:

| Factor | Weight | Description |
|--------|--------|-------------|
| CPU Usage | 30% | Lower usage = higher score |
| Memory Usage | 35% | Lower usage = higher score |
| Temperature | 20% | Lower temp = higher score (max 80°C) |
| Battery Level | 15% | Higher level = higher score |

## Performance

| Metric | Target | Achieved |
|--------|--------|----------|
| Startup | <100ms | ✅ |
| Per-collection | <5ms (p99) | ✅ |
| Memory | <5MB steady state | ✅ |
| Cache TTL | 500ms | ✅ |
| History | 300 items max | ✅ |

## Architecture

```
sysmetrics-core/                    # Core metrics library (no UI dependencies)
├── domain/
│   ├── model/                      # Data classes & enums
│   ├── repository/                 # IMetricsRepository interface
│   ├── logger/                     # MetricsLogger interface
│   └── export/                     # MetricsExporter interface
├── data/
│   ├── repository/                 # MetricsRepositoryImpl
│   ├── aggregation/                # MetricsAggregationStrategy
│   ├── export/                     # CsvMetricsExporter, ExportManager
│   ├── mapper/                     # Data transformers
│   └── cache/                      # MetricsCache (500ms TTL)
├── infrastructure/
│   ├── proc/                       # ProcFileReader (/proc files)
│   ├── android/                    # AndroidMetricsProvider
│   ├── logger/                     # AndroidMetricsLogger, FileMetricsLogger
│   └── extension/                  # Utility extensions
└── SysMetrics.kt                   # Public API singleton

sysmetrics-overlay/                 # Optional debug overlay module
├── SysMetricsOverlay.kt            # Public API
├── OverlayConfig.kt                # Configuration
├── OverlayHandle.kt                # Control interface
├── fps/
│   └── FrameRateMonitor.kt         # FPS monitoring (Choreographer)
└── view/
    └── MetricsOverlayView.kt       # UI component
```

## Requirements

- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9.10+
- **Coroutines**: 1.7.3+

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2025 Yhtyyar Kadyrow

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

## Author

**Yhtyyar Kadyrow**
- Email: kadyrow1506@gmail.com
- GitHub: [@yhtyyar](https://github.com/yhtyyar)

## Support

For issues and feature requests, please use the GitHub issue tracker or contact kadyrow1506@gmail.com

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

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
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

## API Reference

### SysMetrics Singleton

| Method | Description |
|--------|-------------|
| `initialize(context)` | Initialize the library (call once) |
| `getCurrentMetrics()` | Get current system metrics snapshot |
| `observeMetrics(intervalMs)` | Stream metrics at specified interval |
| `observeHealthScore()` | Stream health score updates |
| `getMetricsHistory(count)` | Get historical metrics |
| `clearHistory()` | Clear metrics history |
| `destroy()` | Release all resources |

### Data Classes

- **SystemMetrics** - Complete metrics snapshot
- **CpuMetrics** - CPU usage, cores, frequencies
- **MemoryMetrics** - RAM usage, available, cached
- **BatteryMetrics** - Level, temperature, status, health
- **ThermalMetrics** - CPU/battery temperature, throttling
- **StorageMetrics** - Storage capacity and usage
- **HealthScore** - Overall system health assessment

### Enums

- **HealthStatus** - EXCELLENT, GOOD, WARNING, CRITICAL
- **BatteryStatus** - UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL
- **BatteryHealth** - UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD
- **HealthIssue** - HIGH_CPU_USAGE, HIGH_MEMORY_USAGE, HIGH_TEMPERATURE, LOW_BATTERY, THERMAL_THROTTLING, LOW_STORAGE, POOR_PERFORMANCE

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
sysmetrics-core/
├── domain/
│   ├── model/          # Data classes & enums
│   └── repository/     # IMetricsRepository interface
├── data/
│   ├── repository/     # MetricsRepositoryImpl
│   ├── mapper/         # Data transformers
│   └── cache/          # MetricsCache (500ms TTL)
├── infrastructure/
│   ├── proc/           # ProcFileReader (/proc files)
│   ├── android/        # AndroidMetricsProvider
│   └── extension/      # Utility extensions
└── SysMetrics.kt       # Public API singleton
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

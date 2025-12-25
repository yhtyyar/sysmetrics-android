# SysMetrics Android Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

A production-ready Android library for collecting comprehensive system metrics including CPU, memory, battery, thermal, and storage information.

## Features

- 📊 **Comprehensive Metrics** - CPU, Memory, Battery, Thermal, and Storage
- 🔄 **Real-time Streaming** - Flow-based reactive API
- 💪 **Health Scoring** - Automatic system health assessment
- 🏗️ **Clean Architecture** - Domain/Data/Infrastructure layers
- 🔒 **Thread-safe** - Safe concurrent access
- ⚡ **High Performance** - <5ms latency, <5MB memory
- 🎯 **Zero Dependencies** - Only Kotlin stdlib, Coroutines, Serialization

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

```
Copyright 2024 SysMetrics

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

## Support

For issues and feature requests, please use the GitHub issue tracker.

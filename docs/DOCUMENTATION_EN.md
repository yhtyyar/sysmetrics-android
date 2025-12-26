# SysMetrics Android Library

## Documentation in English

---

# 📚 Complete Guide to the SysMetrics Library

## Table of Contents

1. [Introduction](#introduction)
2. [Installation](#installation)
3. [Quick Start](#quick-start)
4. [Architecture](#architecture)
5. [API Reference](#api-reference)
6. [Data Models](#data-models)
7. [Usage Examples](#usage-examples)
8. [Data Export](#data-export)
9. [Debug Overlay (HUD)](#debug-overlay-hud)
10. [Performance](#performance)
11. [Best Practices](#best-practices)
12. [Troubleshooting](#troubleshooting)

---

## Introduction

**SysMetrics** is a high-performance library for collecting system metrics on Android devices. The library provides comprehensive information about system state: CPU, memory, battery, temperature, storage, and network.

### Key Features

- 📊 **Comprehensive Monitoring** — CPU, RAM, battery, temperature, storage, network
- 🔄 **Reactive API** — Flow-based data streaming
- 💪 **Health Assessment** — Automatic system health calculation
- 🏗️ **Clean Architecture** — Domain/Data/Infrastructure layer separation
- 🔒 **Thread Safety** — Safe concurrent access
- ⚡ **High Performance** — <5ms latency, <5MB memory
- 🎯 **Zero Dependencies** — Only Kotlin stdlib, Coroutines, Serialization
- 📤 **Data Export** — CSV and JSON formats
- 🖥️ **Debug Overlay** — In-app HUD for real-time metrics visualization

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.sysmetrics:sysmetrics-core:1.0.0'
}
```

### Requirements

| Parameter | Value |
|-----------|-------|
| Min SDK | 21 (Android 5.0) |
| Target SDK | 34 (Android 14) |
| Kotlin | 1.9.10+ |
| Coroutines | 1.7.3+ |

---

## Quick Start

### Step 1: Initialization

Initialize the library in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize SysMetrics (call once)
        SysMetrics.initialize(this)
    }
}
```

### Step 2: Get Current Metrics

```kotlin
lifecycleScope.launch {
    SysMetrics.getCurrentMetrics()
        .onSuccess { metrics ->
            // CPU usage
            val cpuUsage = metrics.cpuMetrics.usagePercent
            
            // Memory usage
            val memoryUsage = metrics.memoryMetrics.usagePercent
            
            // Battery level
            val batteryLevel = metrics.batteryMetrics.level
            
            // CPU temperature
            val cpuTemp = metrics.thermalMetrics.cpuTemperature
            
            // Network speed
            val downloadSpeed = metrics.networkMetrics.rxBytesPerSecond
            
            Log.d("SysMetrics", "CPU: $cpuUsage%, RAM: $memoryUsage%")
        }
        .onFailure { error ->
            Log.e("SysMetrics", "Failed to get metrics", error)
        }
}
```

### Step 3: Observe Real-time Metrics

```kotlin
lifecycleScope.launch {
    SysMetrics.observeMetrics(intervalMs = 1000)
        .collect { metrics ->
            // Update UI with new data
            updateDashboard(metrics)
        }
}
```

### Step 4: Monitor System Health

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
                showNotification(recommendation)
            }
        }
}
```

### Step 5: Cleanup Resources

```kotlin
override fun onTerminate() {
    super.onTerminate()
    runBlocking {
        SysMetrics.destroy()
    }
}
```

---

## Architecture

### Project Structure

```
sysmetrics-core/
├── domain/                          # Domain layer
│   ├── model/                       # Data models
│   │   ├── SystemMetrics.kt         # Complete metrics snapshot
│   │   ├── CpuMetrics.kt            # CPU metrics
│   │   ├── MemoryMetrics.kt         # Memory metrics
│   │   ├── BatteryMetrics.kt        # Battery metrics
│   │   ├── ThermalMetrics.kt        # Thermal metrics
│   │   ├── StorageMetrics.kt        # Storage metrics
│   │   ├── NetworkMetrics.kt        # Network metrics
│   │   ├── HealthScore.kt           # Health assessment
│   │   └── Enums.kt                 # Enumerations
│   └── repository/                  # Repository interfaces
│       └── IMetricsRepository.kt
├── data/                            # Data layer
│   ├── repository/                  # Repository implementations
│   │   └── MetricsRepositoryImpl.kt
│   ├── cache/                       # Caching
│   │   └── MetricsCache.kt          # TTL cache (500ms)
│   ├── mapper/                      # Data mapping
│   │   └── MetricsMapper.kt
│   └── export/                      # Data export
│       └── MetricsExporter.kt       # CSV/JSON export
├── infrastructure/                  # Infrastructure layer
│   ├── proc/                        # /proc file reading
│   │   └── ProcFileReader.kt
│   ├── android/                     # Android APIs
│   │   ├── AndroidMetricsProvider.kt
│   │   └── NetworkMetricsProvider.kt
│   └── extension/                   # Extensions
│       └── Extensions.kt
└── SysMetrics.kt                    # Public API (Singleton)
```

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│                    (Your Application)                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Activity   │  │  ViewModel  │  │      Service        │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘  │
└─────────┼────────────────┼───────────────────┼──────────────┘
          │                │                   │
          ▼                ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                     SYSMETRICS LIBRARY                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    SysMetrics.kt                       │  │
│  │                  (Public API Singleton)                │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────▼───────────────────────────┐  │
│  │                  DOMAIN LAYER                          │  │
│  │  ┌─────────────────────┐  ┌─────────────────────────┐ │  │
│  │  │  IMetricsRepository │  │     Domain Models       │ │  │
│  │  └──────────┬──────────┘  └─────────────────────────┘ │  │
│  └─────────────┼─────────────────────────────────────────┘  │
│                │                                             │
│  ┌─────────────▼─────────────────────────────────────────┐  │
│  │                    DATA LAYER                          │  │
│  │  ┌──────────────────┐  ┌─────────────┐  ┌───────────┐ │  │
│  │  │MetricsRepository │  │MetricsCache │  │ Exporter  │ │  │
│  │  │      Impl        │  │  (500ms)    │  │ CSV/JSON  │ │  │
│  │  └────────┬─────────┘  └─────────────┘  └───────────┘ │  │
│  └───────────┼───────────────────────────────────────────┘  │
│              │                                               │
│  ┌───────────▼───────────────────────────────────────────┐  │
│  │               INFRASTRUCTURE LAYER                     │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │  │
│  │  │ProcFileReader│  │AndroidMetrics│  │NetworkMetrics│ │  │
│  │  │  /proc/stat  │  │   Provider   │  │   Provider   │ │  │
│  │  │  /proc/meminfo│ │  BatteryMgr  │  │ TrafficStats │ │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘ │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## API Reference

### SysMetrics (Singleton)

The main entry point to the library.

| Method | Description | Returns |
|--------|-------------|---------|
| `initialize(context)` | Initialize the library | `Unit` |
| `isInitialized()` | Check initialization status | `Boolean` |
| `getCurrentMetrics()` | Get current metrics | `Result<SystemMetrics>` |
| `observeMetrics(intervalMs)` | Metrics stream | `Flow<SystemMetrics>` |
| `observeHealthScore()` | Health score stream | `Flow<HealthScore>` |
| `getMetricsHistory(count)` | Metrics history | `Result<List<SystemMetrics>>` |
| `clearHistory()` | Clear history | `Result<Unit>` |
| `getRepository()` | Access repository | `IMetricsRepository` |
| `destroy()` | Release resources | `Result<Unit>` |

### IMetricsRepository

Repository interface for advanced usage.

```kotlin
public interface IMetricsRepository {
    suspend fun initialize(): Result<Unit>
    suspend fun getCurrentMetrics(): Result<SystemMetrics>
    fun observeMetrics(intervalMs: Long = 1000L): Flow<SystemMetrics>
    fun observeHealthScore(): Flow<HealthScore>
    suspend fun getMetricsHistory(count: Int = 60): Result<List<SystemMetrics>>
    suspend fun clearHistory(): Result<Unit>
    suspend fun destroy(): Result<Unit>
}
```

---

## Data Models

### SystemMetrics

Complete snapshot of all system metrics.

```kotlin
data class SystemMetrics(
    val cpuMetrics: CpuMetrics,
    val memoryMetrics: MemoryMetrics,
    val batteryMetrics: BatteryMetrics,
    val thermalMetrics: ThermalMetrics,
    val storageMetrics: StorageMetrics,
    val networkMetrics: NetworkMetrics,
    val timestamp: Long,
    val uptime: Long
)
```

### CpuMetrics

Processor metrics.

| Field | Type | Description |
|-------|------|-------------|
| `usagePercent` | `Float` | CPU usage (0-100%) |
| `physicalCores` | `Int` | Number of physical cores |
| `logicalCores` | `Int` | Number of logical cores |
| `maxFrequencyKHz` | `Long?` | Maximum frequency (kHz) |
| `currentFrequencyKHz` | `Long?` | Current frequency (kHz) |
| `coreFrequencies` | `List<Long>?` | Per-core frequencies |

### MemoryMetrics

RAM metrics.

| Field | Type | Description |
|-------|------|-------------|
| `totalMemoryMB` | `Long` | Total memory (MB) |
| `usedMemoryMB` | `Long` | Used memory (MB) |
| `freeMemoryMB` | `Long` | Free memory (MB) |
| `availableMemoryMB` | `Long` | Available memory (MB) |
| `usagePercent` | `Float` | Usage (0-100%) |
| `buffersMB` | `Long?` | Buffers (MB) |
| `cachedMB` | `Long?` | Cached (MB) |
| `swapTotalMB` | `Long?` | Total swap (MB) |
| `swapFreeMB` | `Long?` | Free swap (MB) |

### BatteryMetrics

Battery metrics.

| Field | Type | Description |
|-------|------|-------------|
| `level` | `Int` | Charge level (0-100%) |
| `temperature` | `Float` | Temperature (°C) |
| `status` | `BatteryStatus` | Charging status |
| `health` | `BatteryHealth` | Battery health |
| `plugged` | `Boolean` | Connected to power |
| `chargingSpeed` | `Int?` | Charging speed |

### ThermalMetrics

Thermal metrics.

| Field | Type | Description |
|-------|------|-------------|
| `cpuTemperature` | `Float` | CPU temperature (°C) |
| `batteryTemperature` | `Float` | Battery temperature (°C) |
| `otherTemperatures` | `Map<String, Float>` | Other sensors |
| `thermalThrottling` | `Boolean` | Thermal throttling active |

### StorageMetrics

Storage metrics.

| Field | Type | Description |
|-------|------|-------------|
| `totalStorageMB` | `Long` | Total capacity (MB) |
| `freeStorageMB` | `Long` | Free space (MB) |
| `usedStorageMB` | `Long` | Used space (MB) |
| `usagePercent` | `Float` | Usage (0-100%) |

### NetworkMetrics

Network metrics.

| Field | Type | Description |
|-------|------|-------------|
| `rxBytes` | `Long` | Total bytes received |
| `txBytes` | `Long` | Total bytes transmitted |
| `rxBytesPerSecond` | `Long` | Download speed (B/s) |
| `txBytesPerSecond` | `Long` | Upload speed (B/s) |
| `isConnected` | `Boolean` | Network connected |
| `connectionType` | `NetworkType` | Connection type |
| `networkName` | `String?` | Network name (SSID) |
| `signalStrength` | `Int?` | Signal strength (dBm) |

### HealthScore

System health assessment.

| Field | Type | Description |
|-------|------|-------------|
| `score` | `Float` | Score (0-100) |
| `status` | `HealthStatus` | Health status |
| `issues` | `List<HealthIssue>` | Detected issues |
| `recommendations` | `List<String>` | Recommendations |
| `timestamp` | `Long` | Assessment time |

### Enumerations

```kotlin
enum class HealthStatus {
    EXCELLENT,  // Excellent (80-100)
    GOOD,       // Good (60-79)
    WARNING,    // Warning (40-59)
    CRITICAL    // Critical (0-39)
}

enum class BatteryStatus {
    UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL
}

enum class BatteryHealth {
    UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, 
    UNSPECIFIED_FAILURE, COLD
}

enum class HealthIssue {
    HIGH_CPU_USAGE,      // CPU > 85%
    HIGH_MEMORY_USAGE,   // RAM > 85%
    HIGH_TEMPERATURE,    // High temperature
    LOW_BATTERY,         // Battery < 15%
    THERMAL_THROTTLING,  // Thermal throttling
    LOW_STORAGE,         // Storage > 90%
    POOR_PERFORMANCE     // Poor performance
}

enum class NetworkType {
    NONE, WIFI, MOBILE, ETHERNET, BLUETOOTH, VPN, UNKNOWN
}
```

---

## Usage Examples

### Example 1: Monitoring in Activity

```kotlin
class MonitorActivity : AppCompatActivity() {
    
    private var metricsJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        
        // Ensure SysMetrics is initialized
        if (!SysMetrics.isInitialized()) {
            SysMetrics.initialize(applicationContext)
        }
    }
    
    override fun onResume() {
        super.onResume()
        startMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        stopMonitoring()
    }
    
    private fun startMonitoring() {
        metricsJob = lifecycleScope.launch {
            SysMetrics.observeMetrics(intervalMs = 1000)
                .collect { metrics ->
                    updateUI(metrics)
                }
        }
    }
    
    private fun stopMonitoring() {
        metricsJob?.cancel()
        metricsJob = null
    }
    
    private fun updateUI(metrics: SystemMetrics) {
        binding.apply {
            cpuProgress.progress = metrics.cpuMetrics.usagePercent.toInt()
            cpuText.text = "${metrics.cpuMetrics.usagePercent.toInt()}%"
            
            memoryProgress.progress = metrics.memoryMetrics.usagePercent.toInt()
            memoryText.text = "${metrics.memoryMetrics.usedMemoryMB}/${metrics.memoryMetrics.totalMemoryMB} MB"
            
            batteryProgress.progress = metrics.batteryMetrics.level
            batteryText.text = "${metrics.batteryMetrics.level}%"
            
            tempText.text = "${metrics.thermalMetrics.cpuTemperature}°C"
            
            networkText.text = metrics.networkMetrics.getFormattedDownloadSpeed()
        }
    }
}
```

### Example 2: Background Service

```kotlin
class MetricsService : Service() {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        SysMetrics.initialize(applicationContext)
        startMetricsCollection()
    }
    
    private fun startMetricsCollection() {
        scope.launch {
            SysMetrics.observeHealthScore()
                .collect { healthScore ->
                    if (healthScore.status == HealthStatus.CRITICAL) {
                        showWarningNotification(healthScore)
                    }
                }
        }
    }
    
    private fun showWarningNotification(healthScore: HealthScore) {
        val issues = healthScore.issues.joinToString(", ")
        // Show notification about issues
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
```

### Example 3: ViewModel with StateFlow

```kotlin
class MetricsViewModel : ViewModel() {
    
    private val _metrics = MutableStateFlow<SystemMetrics?>(null)
    val metrics: StateFlow<SystemMetrics?> = _metrics.asStateFlow()
    
    private val _healthScore = MutableStateFlow<HealthScore?>(null)
    val healthScore: StateFlow<HealthScore?> = _healthScore.asStateFlow()
    
    init {
        observeMetrics()
        observeHealthScore()
    }
    
    private fun observeMetrics() {
        viewModelScope.launch {
            SysMetrics.observeMetrics(intervalMs = 1000)
                .collect { _metrics.value = it }
        }
    }
    
    private fun observeHealthScore() {
        viewModelScope.launch {
            SysMetrics.observeHealthScore()
                .collect { _healthScore.value = it }
        }
    }
    
    fun refreshMetrics() {
        viewModelScope.launch {
            SysMetrics.getCurrentMetrics()
                .onSuccess { _metrics.value = it }
        }
    }
}
```

---

## Data Export

### Export to CSV

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 100)
    
    historyResult.onSuccess { history ->
        MetricsExporter.exportToCsv(history)
            .onSuccess { csvContent ->
                // Save to file or share
                saveToFile("metrics.csv", csvContent)
            }
    }
}
```

### Export to JSON

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 100)
    
    historyResult.onSuccess { history ->
        MetricsExporter.exportToJson(history)
            .onSuccess { jsonContent ->
                // Save to file or upload to server
                uploadToServer(jsonContent)
            }
    }
}
```

### Generate Summary Report

```kotlin
lifecycleScope.launch {
    val historyResult = SysMetrics.getMetricsHistory(count = 300)
    
    historyResult.onSuccess { history ->
        MetricsExporter.generateSummaryReport(history)
            .onSuccess { report ->
                // Display or save report
                showReportDialog(report)
            }
    }
}
```

---

## Performance

### Target Metrics

| Metric | Target | Achieved |
|--------|--------|----------|
| Startup time | <100ms | ✅ |
| Collection latency | <5ms (p99) | ✅ |
| Memory usage | <5MB | ✅ |
| CPU usage | <5% (24h) | ✅ |
| Cache TTL | 500ms | ✅ |
| History size | 300 entries | ✅ |

### Health Score Formula

```
score = (1 - cpu/100) × 0.30 + 
        (1 - memory/100) × 0.35 + 
        (1 - temp/80) × 0.20 + 
        (battery/100) × 0.15
```

| Component | Weight | Description |
|-----------|--------|-------------|
| CPU | 30% | Lower usage = higher score |
| Memory | 35% | Lower usage = higher score |
| Temperature | 20% | Lower temp = higher score (max 80°C) |
| Battery | 15% | Higher charge = higher score |

---

## Best Practices

### ✅ Recommended

1. **Initialize once** in `Application.onCreate()`
2. **Use Flow** for reactive UI updates
3. **Cancel coroutines** when component is destroyed
4. **Use Result<T>** for error handling
5. **Limit interval** to minimum 100ms
6. **Call destroy()** when finished

### ❌ Not Recommended

1. Don't initialize in every Activity
2. Don't block the main thread
3. Don't ignore errors in Result
4. Don't set interval < 100ms
5. Don't store Context in long-lived objects

### Correct Lifecycle Handling Example

```kotlin
class MyActivity : AppCompatActivity() {
    
    private var metricsJob: Job? = null
    
    override fun onStart() {
        super.onStart()
        metricsJob = lifecycleScope.launch {
            SysMetrics.observeMetrics()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { updateUI(it) }
        }
    }
    
    override fun onStop() {
        super.onStop()
        metricsJob?.cancel()
    }
}
```

---

## Debug Overlay (HUD)

The `sysmetrics-overlay` module provides an in-app overlay for real-time metrics visualization.

### Installation

```kotlin
dependencies {
    implementation("com.sysmetrics:sysmetrics-core:1.0.0")
    implementation("com.sysmetrics:sysmetrics-overlay:1.0.0")
}
```

### Basic Usage

```kotlin
class MainActivity : AppCompatActivity() {
    private var overlayHandle: OverlayHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Attach overlay (debug builds only by default)
        overlayHandle = SysMetricsOverlay.attach(this)
    }

    override fun onDestroy() {
        overlayHandle?.detach()
        super.onDestroy()
    }
}
```

### Features

- **Collapsed mode**: FPS, CPU%, RAM%, Network speed
- **Expanded mode**: Full metrics (tap "▼ More")
- **Drag & drop**: Reposition overlay anywhere
- **Color coding**: Green/Yellow/Red status indicators

### Configuration

```kotlin
val config = OverlayConfig(
    updateIntervalMs = 500L,
    showFps = true,
    showNetworkSpeed = true,
    draggable = true,
    enableInRelease = false  // Safe for production
)
val handle = SysMetricsOverlay.attach(activity, config)
```

📖 **Full documentation:** [OVERLAY_GUIDE.md](OVERLAY_GUIDE.md)

---

## Troubleshooting

### Issue: IllegalStateException when calling methods

**Cause:** SysMetrics is not initialized.

**Solution:**
```kotlin
if (!SysMetrics.isInitialized()) {
    SysMetrics.initialize(applicationContext)
}
```

### Issue: CPU shows 0% on first call

**Cause:** CPU usage calculation requires two measurements.

**Solution:** Use `observeMetrics()` instead of a single call, or call `getCurrentMetrics()` twice with a delay.

### Issue: Temperature equals 0

**Cause:** Device doesn't expose temperature data.

**Solution:** Check the value before using:
```kotlin
if (metrics.thermalMetrics.cpuTemperature > 0) {
    showTemperature(metrics.thermalMetrics.cpuTemperature)
}
```

### Issue: Memory leak

**Cause:** Flow subscription not cancelled.

**Solution:** Use `lifecycleScope` or cancel Job manually.

---

## License

This project is licensed under the **MIT License** — you can freely use it for personal and commercial purposes.

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

## Author

**Yhtyyar Kadyrow**
- Email: kadyrow1506@gmail.com
- GitHub: [@yhtyyar](https://github.com/yhtyyar)

## Support

- **GitHub Issues:** [github.com/yhtyyar/sysmetrics-android/issues](https://github.com/yhtyyar/sysmetrics-android/issues)
- **Email:** kadyrow1506@gmail.com

---

*Documentation version 1.0.0 | © 2025 Yhtyyar Kadyrow | MIT License*

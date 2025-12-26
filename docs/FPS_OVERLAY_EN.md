# FPS Overlay System

## Overview

The FPS Overlay System provides real-time frame rate monitoring and app-specific resource tracking for Android applications. It displays performance metrics as an overlay directly on top of your app's UI.

## Features

- **Real-time FPS Monitoring** - Captures frame timings using Choreographer API
- **App-Specific Metrics** - CPU, memory, heap, and thread monitoring for your app only
- **Visual Overlay** - Color-coded FPS display with history graph
- **Peak Detection** - Automatic detection of FPS drops, high performance, and jank
- **Toast Notifications** - Alerts for significant performance events
- **Data Persistence** - Room database for historical analysis
- **Lifecycle-Aware** - Automatic start/stop based on activity lifecycle

## Quick Start

### 1. Basic Integration

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var overlayManager: FpsOverlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Create and attach overlay
        overlayManager = FpsOverlayManager.create(this)
        overlayManager.attachToActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.detachFromActivity()
    }
}
```

### 2. Custom Configuration

```kotlin
val config = FpsOverlayManager.OverlayConfig(
    gravity = Gravity.TOP or Gravity.END,  // Top-right corner
    marginTop = 100,  // Below status bar
    marginRight = 16,
    showToasts = true,  // Enable toast notifications
    recordToDatabase = true  // Enable data persistence
)

overlayManager = FpsOverlayManager.create(
    context = this,
    logger = AndroidMetricsLogger.forDevelopment(),
    config = config
)
```

### 3. Observing Metrics Programmatically

```kotlin
lifecycleScope.launch {
    // Observe FPS metrics
    overlayManager.fpsCollector.fpsFlow.collect { metrics ->
        when {
            metrics.isCritical -> showPerformanceWarning()
            metrics.isSmooth -> hidePerformanceWarning()
        }
    }
}

lifecycleScope.launch {
    // Observe peak events
    overlayManager.fpsCollector.peakEventFlow.collect { event ->
        when (event) {
            is FpsPeakEvent.FrameDrop -> logFrameDrop(event)
            is FpsPeakEvent.CriticalJank -> reportJank(event)
            is FpsPeakEvent.HighPerformance -> { /* good! */ }
        }
    }
}
```

## Components

### FpsMetricsCollector

Collects FPS metrics using Android's Choreographer API.

```kotlin
val collector = FpsMetricsCollector()

// Start collection
collector.startCollection()

// Get current statistics
val stats = collector.getCurrentStats()
println("Average FPS: ${stats.averageFps}")
println("Min FPS: ${stats.minFps}")
println("Jank frames: ${stats.jankFrames}")

// Stop collection
collector.stopCollection()
```

**Key Properties:**
- `fpsFlow: StateFlow<FpsMetrics>` - Current FPS metrics
- `peakEventFlow: SharedFlow<FpsPeakEvent>` - Peak events
- `isActive: Boolean` - Collection state

### AppMetricsCollector

Collects app-specific resource consumption metrics.

```kotlin
val appCollector = AppMetricsCollector(context)

// Start with 500ms interval
appCollector.startCollection(intervalMs = 500)

// Observe metrics
appCollector.metricsFlow.collect { metrics ->
    println("App CPU: ${metrics.cpuUsagePercent}%")
    println("Heap: ${metrics.heapUsageMb} MB / ${metrics.heapMaxMb} MB")
    println("Threads: ${metrics.threadCount}")
}
```

**Collected Metrics:**
- CPU usage percentage (app-specific)
- Memory usage (PSS)
- Heap usage and max heap
- Native heap usage
- Thread count
- Network I/O (if available)
- Open file descriptors

### FpsOverlayView

Custom View for rendering the FPS overlay.

```kotlin
val overlay = FpsOverlayView(context)

// Update with metrics
overlay.updateMetrics(overlayMetrics)

// Or update individually
overlay.updateFps(fpsMetrics)
overlay.updateAppMetrics(appMetrics)

// Clear history
overlay.clearHistory()
```

**Display Elements:**
- Current FPS (color-coded)
- Average FPS
- App CPU usage
- App memory usage
- FPS history graph (60 frames)

**Color Coding:**
- 🟢 Green: FPS ≥ 55 (smooth)
- 🟡 Yellow: FPS 30-54 (warning)
- 🔴 Red: FPS < 30 (critical)

### FpsRepository

Persists metrics to Room database.

```kotlin
val repository = FpsRepository.getInstance(context)

// Record FPS
repository.recordFps(fpsMetrics)

// Record peak event
repository.recordPeakEvent(peakEvent)

// Get statistics for last 24 hours
val stats = repository.getStatisticsForPeriod(days = 1)

// Cleanup old data
repository.cleanupOldRecords(daysToKeep = 7)
```

## Data Models

### FpsMetrics

```kotlin
data class FpsMetrics(
    val timestamp: Long,
    val currentFps: Int,        // Instantaneous FPS
    val averageFps: Float,      // Average over window
    val minFps: Int,            // Minimum in window
    val maxFps: Int,            // Maximum in window
    val frameCount: Int,        // Frames in window
    val frameTimeMs: Float,     // Average frame time
    val jankCount: Int          // Janky frames
) {
    val isSmooth: Boolean       // FPS ≥ 55
    val isWarning: Boolean      // FPS 30-54
    val isCritical: Boolean     // FPS < 30
    val status: FpsStatus       // SMOOTH, WARNING, CRITICAL
    val jankPercentage: Float   // Jank rate
}
```

### AppMetrics

```kotlin
data class AppMetrics(
    val timestamp: Long,
    val packageName: String,
    val cpuUsagePercent: Float,     // App CPU usage
    val memoryUsageMb: Float,       // Total memory (PSS)
    val heapUsageMb: Float,         // Java heap
    val heapMaxMb: Float,           // Max heap
    val nativeHeapMb: Float,        // Native heap
    val threadCount: Int,           // Active threads
    val networkRxBytes: Long,       // Received bytes
    val networkTxBytes: Long,       // Transmitted bytes
    val openFileDescriptors: Int    // Open FDs
) {
    val heapUsagePercent: Float     // Heap usage %
    val isHeapWarning: Boolean      // Heap ≥ 80%
    val isHeapCritical: Boolean     // Heap ≥ 95%
    val totalMemoryMb: Float        // Heap + native
    val memoryStatus: MemoryStatus  // HEALTHY, MODERATE, WARNING, CRITICAL
}
```

### FpsPeakEvent

```kotlin
sealed class FpsPeakEvent {
    data class FrameDrop(
        val timestamp: Long,
        val fps: Int,
        val delta: Int,         // FPS decrease amount
        val previousFps: Int
    ) : FpsPeakEvent() {
        val severity: Severity  // LOW, MEDIUM, HIGH
    }

    data class HighPerformance(
        val timestamp: Long,
        val fps: Int
    ) : FpsPeakEvent()

    data class CriticalJank(
        val timestamp: Long,
        val fps: Int,
        val duration: Long      // Jank duration in ms
    ) : FpsPeakEvent()
}
```

## Performance Targets

| Metric | Target | Description |
|--------|--------|-------------|
| Frame Recording | <1ms | Per-frame overhead |
| FPS Calculation | <2ms | Statistics computation |
| Memory | <600KB | Total overlay footprint |
| CPU Overhead | <2% | Collection overhead |
| Render Time | <3ms | Overlay rendering |
| Accuracy | >99% | FPS detection accuracy |

## Best Practices

### 1. Debug Builds Only

```kotlin
if (BuildConfig.DEBUG) {
    overlayManager = FpsOverlayManager.create(this)
    overlayManager.attachToActivity(this)
}
```

### 2. Conditional Visibility

```kotlin
// Toggle with shake gesture or button
overlayManager.toggleVisibility()

// Or control directly
overlayManager.hide()
overlayManager.show()
```

### 3. Database Cleanup

```kotlin
// Schedule periodic cleanup
lifecycleScope.launch {
    while (isActive) {
        delay(24.hours)
        repository.cleanupOldRecords(daysToKeep = 7)
    }
}
```

### 4. Battery Conservation

The overlay automatically pauses collection when the activity is stopped (ON_STOP) and resumes when started (ON_START).

## Troubleshooting

### Overlay Not Visible

1. Check if `attachToActivity()` was called
2. Verify the activity has a DecorView
3. Check if `hide()` was called

### High CPU Usage

1. Reduce collection interval
2. Disable database recording
3. Use simpler overlay configuration

### Memory Leaks

1. Always call `detachFromActivity()` in `onDestroy()`
2. Use `WeakReference` for activity references
3. Cancel coroutine scopes properly

## API Reference

See the KDoc comments in source files for detailed API documentation:

- `FpsMetricsCollector.kt`
- `AppMetricsCollector.kt`
- `FpsOverlayView.kt`
- `FpsOverlayManager.kt`
- `FpsRepository.kt`

# SysMetrics - Best Practices & Real-World Implementation Guide
## Production Patterns for Android Developers

**Version:** 1.0 Professional  
**Target:** Senior Android Developers  
**Date:** December 25, 2025  

---

## TABLE OF CONTENTS

1. [Architecture Patterns](#architecture-patterns)
2. [Integration Patterns](#integration-patterns)
3. [Real-World Use Cases](#real-world-use-cases)
4. [Performance Optimization](#performance-optimization)
5. [Advanced Patterns](#advanced-patterns)
6. [Monitoring & Analytics](#monitoring--analytics)
7. [Scaling Considerations](#scaling-considerations)

---

## ARCHITECTURE PATTERNS

### Pattern 1: MVVM + Reactive Architecture

```kotlin
// ViewModel with comprehensive metrics monitoring
class SystemHealthViewModel(
    private val repository: IMetricsRepository = SysMetrics.getRepository()
) : ViewModel() {
    
    // Public state flows
    private val _metricsState = MutableStateFlow<SystemMetrics?>(null)
    val metricsState: StateFlow<SystemMetrics?> = _metricsState.asStateFlow()
    
    private val _healthState = MutableStateFlow<HealthScore?>(null)
    val healthState: StateFlow<HealthScore?> = _healthState.asStateFlow()
    
    private val _alertsState = MutableStateFlow<List<HealthAlert>>(emptyList())
    val alertsState: StateFlow<List<HealthAlert>> = _alertsState.asStateFlow()
    
    // Configuration
    private val metricsInterval = MutableStateFlow(1000L)
    
    fun startMonitoring(intervalMs: Long = 1000) {
        metricsInterval.value = intervalMs
        
        // Metrics monitoring
        viewModelScope.launch {
            metricsInterval
                .distinctUntilChanged()
                .flatMapLatest { interval ->
                    repository.observeMetrics(interval)
                }
                .collect { metrics ->
                    _metricsState.value = metrics
                }
        }
        
        // Health monitoring
        viewModelScope.launch {
            repository.observeHealthScore()
                .collect { health ->
                    _healthState.value = health
                    processHealthAlerts(health)
                }
        }
    }
    
    private fun processHealthAlerts(health: HealthScore) {
        val alerts = health.issues.map { issue ->
            HealthAlert(
                severity = when (health.status) {
                    HealthStatus.CRITICAL -> AlertSeverity.CRITICAL
                    HealthStatus.WARNING -> AlertSeverity.WARNING
                    else -> AlertSeverity.INFO
                },
                message = when (issue) {
                    HealthIssue.HIGH_CPU_USAGE -> "CPU usage is high (${health.score}%)"
                    HealthIssue.HIGH_MEMORY_USAGE -> "Memory usage is high"
                    HealthIssue.HIGH_TEMPERATURE -> "Device is overheating"
                    HealthIssue.LOW_BATTERY -> "Battery level is low"
                    HealthIssue.LOW_STORAGE -> "Storage space is low"
                    else -> "System issue detected"
                },
                timestamp = System.currentTimeMillis()
            )
        }
        _alertsState.value = alerts
    }
    
    fun adjustMonitoringInterval(newInterval: Long) {
        require(newInterval >= 100) { "Interval must be at least 100ms" }
        metricsInterval.value = newInterval
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cleanup happens automatically when viewModelScope is cancelled
    }
}

// Data class for alerts
data class HealthAlert(
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }
```

### Pattern 2: Dependency Injection (Manual)

```kotlin
// Service locator pattern for DI (no framework overhead)
object MetricsContainer {
    
    private var instance: MetricsServices? = null
    
    fun initialize(context: Context) {
        if (instance != null) return
        
        val procReader = ProcFileReader(Dispatchers.IO)
        val androidProvider = AndroidMetricsProvider(context)
        val cache = MetricsCache(ttlMs = 500)
        
        val repository = MetricsRepositoryImpl(
            procReader = procReader,
            androidProvider = androidProvider,
            cache = cache,
            ioDispatcher = Dispatchers.IO
        )
        
        instance = MetricsServices(repository)
        SysMetrics.initialize(context) // Also initialize public API
    }
    
    fun getRepository(): IMetricsRepository {
        return instance?.repository 
            ?: throw IllegalStateException("MetricsContainer not initialized")
    }
    
    fun destroy() {
        instance = null
    }
    
    private data class MetricsServices(
        val repository: IMetricsRepository
    )
}

// Usage in Application
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MetricsContainer.initialize(this)
        SysMetrics.initialize(this)
    }
    
    override fun onTerminate() {
        super.onTerminate()
        runBlocking {
            SysMetrics.destroy()
            MetricsContainer.destroy()
        }
    }
}
```

### Pattern 3: Repository Pattern with Testing

```kotlin
// Testable repository wrapper
interface IMetricsManager {
    fun observeMetrics(): Flow<SystemMetrics>
    fun observeHealth(): Flow<HealthScore>
    suspend fun collectSnapshot(): Result<SystemMetrics>
}

class MetricsManager(
    private val repository: IMetricsRepository
) : IMetricsManager {
    
    override fun observeMetrics(): Flow<SystemMetrics> {
        return repository.observeMetrics(intervalMs = 1000)
            .retry(maxRetries = 3) { cause ->
                Log.w("MetricsManager", "Retry on: ${cause.message}")
                true
            }
            .catch { cause ->
                Log.e("MetricsManager", "Fatal error in metrics stream", cause)
                emit(SystemMetrics.empty()) // Fallback
            }
    }
    
    override fun observeHealth(): Flow<HealthScore> {
        return repository.observeHealthScore()
            .distinctUntilChanged { old, new -> old.score == new.score }
    }
    
    override suspend fun collectSnapshot(): Result<SystemMetrics> {
        return repository.getCurrentMetrics()
    }
}

// Mock for testing
class MockMetricsRepository : IMetricsRepository {
    
    private val testMetrics = SystemMetrics(
        cpuMetrics = CpuMetrics(50f, 4, 8),
        memoryMetrics = MemoryMetrics(4096, 2048, 2048, 2048, 50f),
        batteryMetrics = BatteryMetrics(50, 30f, BatteryStatus.DISCHARGING, 
                                       BatteryHealth.GOOD, false),
        thermalMetrics = ThermalMetrics(40f, 30f),
        storageMetrics = StorageMetrics(128000, 64000, 64000, 50f)
    )
    
    override suspend fun initialize(): Result<Unit> = Result.success(Unit)
    
    override suspend fun getCurrentMetrics(): Result<SystemMetrics> {
        return Result.success(testMetrics)
    }
    
    override fun observeMetrics(intervalMs: Long): Flow<SystemMetrics> {
        return flowOf(testMetrics)
    }
    
    override fun observeHealthScore(): Flow<HealthScore> {
        return flowOf(HealthScore(score = 75f, status = HealthStatus.GOOD))
    }
    
    override suspend fun getMetricsHistory(count: Int): Result<List<SystemMetrics>> {
        return Result.success(listOf(testMetrics))
    }
    
    override suspend fun clearHistory(): Result<Unit> = Result.success(Unit)
    
    override suspend fun destroy(): Result<Unit> = Result.success(Unit)
}
```

---

## INTEGRATION PATTERNS

### Pattern 1: Adaptive Streaming (IPTV Use Case)

```kotlin
// Adaptive bitrate selection based on metrics
class AdaptiveStreamingController(
    private val repository: IMetricsRepository = SysMetrics.getRepository()
) {
    
    private val bitrateSelector = BitrateSelector()
    
    fun startAdaptiveMonitoring() {
        // Monitor metrics and adjust streaming quality
        viewModelScope.launch {
            repository.observeMetrics(intervalMs = 2000)
                .debounce(1000) // Avoid too frequent changes
                .collect { metrics ->
                    adjustQuality(metrics)
                }
        }
    }
    
    private fun adjustQuality(metrics: SystemMetrics) {
        val cpu = metrics.cpuMetrics.usagePercent
        val memory = metrics.memoryMetrics.usagePercent
        val temperature = metrics.thermalMetrics.cpuTemperature
        
        val targetBitrate = when {
            cpu > 80 || memory > 85 || temperature > 60 -> {
                // Critical: reduce to 480p
                BitrateProfile.LOW_480P
            }
            cpu > 60 || memory > 70 || temperature > 50 -> {
                // Warning: reduce to 720p
                BitrateProfile.MEDIUM_720P
            }
            cpu < 40 && memory < 50 -> {
                // Good: can use full quality
                BitrateProfile.HIGH_1080P
            }
            else -> {
                // Normal: standard quality
                BitrateProfile.MEDIUM_720P
            }
        }
        
        bitrateSelector.selectBitrate(targetBitrate)
    }
    
    private class BitrateSelector {
        fun selectBitrate(profile: BitrateProfile) {
            // Switch streaming profile
        }
    }
    
    enum class BitrateProfile {
        LOW_480P,
        MEDIUM_720P,
        HIGH_1080P
    }
}
```

### Pattern 2: Battery-Aware Task Scheduling

```kotlin
// Schedule tasks based on device health
class HealthAwareTaskScheduler(
    private val repository: IMetricsRepository = SysMetrics.getRepository()
) {
    
    private val taskQueue = ArrayDeque<ScheduledTask>()
    
    fun scheduleTask(task: ScheduledTask) {
        taskQueue.add(task)
        processTaskQueue()
    }
    
    private fun processTaskQueue() {
        viewModelScope.launch {
            repository.observeHealthScore()
                .collect { health ->
                    when (health.status) {
                        HealthStatus.EXCELLENT -> {
                            // Execute all tasks
                            executeAllTasks()
                        }
                        HealthStatus.GOOD -> {
                            // Execute high-priority tasks only
                            executeHighPriorityTasks()
                        }
                        HealthStatus.WARNING -> {
                            // Execute critical tasks only
                            executeCriticalTasks()
                        }
                        HealthStatus.CRITICAL -> {
                            // Pause all non-critical tasks
                            pauseNonCritical()
                        }
                    }
                }
        }
    }
    
    private fun executeAllTasks() {
        taskQueue.forEach { task -> task.execute() }
    }
    
    private fun executeHighPriorityTasks() {
        taskQueue.filter { it.priority >= TaskPriority.HIGH }
            .forEach { task -> task.execute() }
    }
    
    private fun executeCriticalTasks() {
        taskQueue.filter { it.priority == TaskPriority.CRITICAL }
            .forEach { task -> task.execute() }
    }
    
    private fun pauseNonCritical() {
        // Pause background work
    }
    
    data class ScheduledTask(
        val id: String,
        val priority: TaskPriority,
        val execute: suspend () -> Unit
    )
    
    enum class TaskPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
}
```

### Pattern 3: Caching with Fallback

```kotlin
// Robust caching pattern with fallback metrics
class CachingMetricsProvider(
    private val repository: IMetricsRepository
) {
    
    private var cachedMetrics: SystemMetrics? = null
    private var lastError: Exception? = null
    
    suspend fun getMetrics(): SystemMetrics {
        return try {
            repository.getCurrentMetrics()
                .onSuccess { metrics -> cachedMetrics = metrics }
                .onFailure { error -> lastError = error }
                .getOrElse { fallbackMetrics() }
        } catch (e: Exception) {
            fallbackMetrics()
        }
    }
    
    private fun fallbackMetrics(): SystemMetrics {
        // Return last cached metrics or create empty metrics
        return cachedMetrics ?: SystemMetrics.createEmpty()
    }
}
```

---

## REAL-WORLD USE CASES

### Use Case 1: IPTV Application with QoS Monitoring

```kotlin
class IPTVQualityOfServiceMonitor(
    private val viewModel: SystemHealthViewModel
) {
    
    fun setupQoSMonitoring() {
        // Monitor service quality based on system metrics
        lifecycleOwner.lifecycleScope.launch {
            viewModel.healthState
                .collect { health ->
                    if (health != null) {
                        reportQoS(health)
                    }
                }
        }
    }
    
    private fun reportQoS(health: HealthScore) {
        val qosMetrics = QoSMetrics(
            videoQuality = calculateVideoQuality(health),
            audioQuality = calculateAudioQuality(health),
            latency = calculateLatency(health),
            bufferingRate = calculateBuffering(health),
            overallScore = health.score
        )
        
        // Send to analytics
        analyticsService.logQoS(qosMetrics)
    }
    
    private fun calculateVideoQuality(health: HealthScore): QualityLevel {
        return when {
            health.score >= 85 -> QualityLevel.EXCELLENT
            health.score >= 70 -> QualityLevel.GOOD
            health.score >= 50 -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
    }
    
    private fun calculateAudioQuality(health: HealthScore): Boolean {
        return health.score >= 60 // Audio is stable
    }
    
    private fun calculateLatency(health: HealthScore): Long {
        // Estimate latency from health score
        return when {
            health.score >= 80 -> 50L
            health.score >= 60 -> 150L
            else -> 500L
        }
    }
    
    private fun calculateBuffering(health: HealthScore): Float {
        // Calculate buffering probability
        return (100 - health.score) * 0.01f
    }
    
    data class QoSMetrics(
        val videoQuality: QualityLevel,
        val audioQuality: Boolean,
        val latency: Long,
        val bufferingRate: Float,
        val overallScore: Float
    )
    
    enum class QualityLevel { EXCELLENT, GOOD, FAIR, POOR }
}
```

### Use Case 2: Battery-Aware Notification System

```kotlin
class BatteryAwareNotificationManager(
    private val viewModel: SystemHealthViewModel
) {
    
    fun setupBatteryMonitoring() {
        lifecycleOwner.lifecycleScope.launch {
            viewModel.metricsState
                .collect { metrics ->
                    if (metrics != null) {
                        handleBatteryState(metrics.batteryMetrics)
                    }
                }
        }
    }
    
    private fun handleBatteryState(battery: BatteryMetrics) {
        when {
            battery.level <= 5 -> {
                showCriticalBatteryAlert()
            }
            battery.level <= 15 && !battery.plugged -> {
                showLowBatteryWarning()
            }
            battery.temperature > 45 && battery.plugged -> {
                showChargerTempWarning()
            }
            battery.health == BatteryHealth.OVERHEAT -> {
                showOverheatAlert()
            }
        }
    }
    
    private fun showCriticalBatteryAlert() {
        notificationManager.notify(
            NotificationCompat.Builder(context, CRITICAL_CHANNEL)
                .setSmallIcon(R.drawable.ic_battery_empty)
                .setContentTitle("Critical Battery Level")
                .setContentText("Battery will die soon. Save your work!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()
        )
    }
    
    private fun showLowBatteryWarning() {
        // Show less aggressive warning
    }
    
    private fun showChargerTempWarning() {
        // Warn about temperature while charging
    }
    
    private fun showOverheatAlert() {
        // Emergency alert for overheating
    }
}
```

### Use Case 3: Performance Profiling Dashboard

```kotlin
class PerformanceProfilingDashboard(
    private val repository: IMetricsRepository
) {
    
    private val metricsHistory = mutableListOf<SystemMetrics>()
    private val maxHistorySize = 300
    
    suspend fun startProfiling() {
        repository.observeMetrics(intervalMs = 500)
            .collect { metrics ->
                metricsHistory.add(metrics)
                if (metricsHistory.size > maxHistorySize) {
                    metricsHistory.removeAt(0)
                }
                updateDashboard()
            }
    }
    
    private fun updateDashboard() {
        val stats = calculateStatistics()
        displayStats(stats)
    }
    
    private fun calculateStatistics(): PerformanceStats {
        val cpuValues = metricsHistory.map { it.cpuMetrics.usagePercent }
        val memoryValues = metricsHistory.map { it.memoryMetrics.usagePercent }
        val temperatureValues = metricsHistory.map { it.thermalMetrics.cpuTemperature }
        
        return PerformanceStats(
            cpuAvg = cpuValues.average().toFloat(),
            cpuMax = cpuValues.maxOrNull() ?: 0f,
            cpuMin = cpuValues.minOrNull() ?: 0f,
            cpuP95 = cpuValues.sorted().getOrNull((cpuValues.size * 0.95).toInt()) ?: 0f,
            
            memoryAvg = memoryValues.average().toFloat(),
            memoryMax = memoryValues.maxOrNull() ?: 0f,
            memoryMin = memoryValues.minOrNull() ?: 0f,
            
            temperatureAvg = temperatureValues.average().toFloat(),
            temperatureMax = temperatureValues.maxOrNull() ?: 0f,
            
            sampleCount = metricsHistory.size
        )
    }
    
    private fun displayStats(stats: PerformanceStats) {
        // Update UI with statistics
    }
    
    data class PerformanceStats(
        val cpuAvg: Float,
        val cpuMax: Float,
        val cpuMin: Float,
        val cpuP95: Float,
        val memoryAvg: Float,
        val memoryMax: Float,
        val memoryMin: Float,
        val temperatureAvg: Float,
        val temperatureMax: Float,
        val sampleCount: Int
    )
}
```

---

## PERFORMANCE OPTIMIZATION

### Optimization 1: Efficient Flow Filtering

```kotlin
// Avoid collecting every emission
fun efficientMetricsMonitoring() {
    repository.observeMetrics()
        .filter { metrics ->
            // Only process if values changed significantly
            metrics.cpuMetrics.usagePercent > previousCpuUsage + 5 ||
            metrics.memoryMetrics.usagePercent > previousMemoryUsage + 5
        }
        .collect { metrics ->
            updateUI(metrics)
        }
}

// Sample every Nth emission
fun sampledMonitoring() {
    repository.observeMetrics()
        .sample(5000) // Emit latest every 5 seconds
        .collect { metrics ->
            updateUI(metrics)
        }
}

// Debounce rapid changes
fun debouncedMonitoring() {
    repository.observeMetrics()
        .debounce(1000) // Wait 1s after last emission
        .collect { metrics ->
            updateUI(metrics)
        }
}

// Batch emissions
fun batchedMonitoring() {
    repository.observeMetrics()
        .buffer(capacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .collect { metrics ->
            updateUI(metrics)
        }
}
```

### Optimization 2: Memory-Efficient History Management

```kotlin
// Circular buffer pattern for history
class CircularMetricsHistory(private val capacity: Int = 300) {
    
    private val metrics = Array<SystemMetrics?>(capacity) { null }
    private var writeIndex = 0
    
    fun add(metric: SystemMetrics) {
        metrics[writeIndex] = metric
        writeIndex = (writeIndex + 1) % capacity
    }
    
    fun getLastN(count: Int): List<SystemMetrics> {
        val result = mutableListOf<SystemMetrics>()
        var index = writeIndex - 1
        repeat(minOf(count, capacity)) {
            if (index < 0) index = capacity - 1
            metrics[index]?.let { result.add(it) }
            index--
        }
        return result.reversed()
    }
}

// Usage
val history = CircularMetricsHistory(capacity = 300)

repository.observeMetrics()
    .collect { metrics ->
        history.add(metrics)
    }
```

### Optimization 3: Lazy Initialization

```kotlin
// Lazy repository initialization
object LazyMetricsRepository {
    
    private var _repository: IMetricsRepository? = null
    
    val repository: IMetricsRepository by lazy {
        _repository ?: throw IllegalStateException("Not initialized")
    }
    
    fun initialize(context: Context) {
        if (_repository == null) {
            _repository = SysMetrics.getRepository()
        }
    }
}

// Use only when needed
if (shouldMonitorMetrics) {
    LazyMetricsRepository.initialize(context)
    val metrics = LazyMetricsRepository.repository.getCurrentMetrics()
}
```

---

## ADVANCED PATTERNS

### Pattern 1: Composite Metrics with Custom Scoring

```kotlin
// Create custom health scoring algorithm
class CustomHealthScorer {
    
    fun calculateCompositScore(metrics: SystemMetrics): Float {
        val weights = ScoringWeights(
            cpuWeight = 0.25,
            memoryWeight = 0.30,
            thermalWeight = 0.25,
            batteryWeight = 0.15,
            storageWeight = 0.05
        )
        
        val scores = listOf(
            metrics.cpuMetrics.usagePercent.toScore() * weights.cpuWeight,
            metrics.memoryMetrics.usagePercent.toScore() * weights.memoryWeight,
            metrics.thermalMetrics.cpuTemperature.toThermalScore() * weights.thermalWeight,
            metrics.batteryMetrics.level.toScore() * weights.batteryWeight,
            metrics.storageMetrics.usagePercent.toScore() * weights.storageWeight
        )
        
        return scores.sum()
    }
    
    private fun Float.toScore(): Float {
        // Convert percentage (0-100) to score (100-0)
        return (100 - this).coerceIn(0f, 100f)
    }
    
    private fun Float.toThermalScore(): Float {
        // Convert temperature to score (lower is better)
        return when {
            this < 30 -> 100f
            this < 45 -> 80f
            this < 60 -> 50f
            else -> 10f
        }
    }
    
    data class ScoringWeights(
        val cpuWeight: Float,
        val memoryWeight: Float,
        val thermalWeight: Float,
        val batteryWeight: Float,
        val storageWeight: Float
    )
}
```

### Pattern 2: Metrics Export and Reporting

```kotlin
// Export metrics for analytics
class MetricsExporter(
    private val repository: IMetricsRepository
) {
    
    suspend fun exportAsJSON(count: Int = 300): String {
        val metrics = repository.getMetricsHistory(count).getOrNull() ?: emptyList()
        return Json.encodeToString(metrics)
    }
    
    suspend fun exportAsCSV(count: Int = 300): String {
        val metrics = repository.getMetricsHistory(count).getOrNull() ?: emptyList()
        
        val header = "timestamp,cpu_usage,memory_usage,battery_level,temperature,health_score\n"
        val rows = metrics.map { metric ->
            "${metric.timestamp},${metric.cpuMetrics.usagePercent}," +
            "${metric.memoryMetrics.usagePercent},${metric.batteryMetrics.level}," +
            "${metric.thermalMetrics.cpuTemperature},${metric.getHealthScore()}"
        }
        
        return header + rows.joinToString("\n")
    }
    
    suspend fun uploadMetrics(endpoint: String) {
        val json = exportAsJSON()
        // Upload to server
    }
}
```

### Pattern 3: Anomaly Detection

```kotlin
// Detect anomalies in metrics
class AnomalyDetector(
    private val windowSize: Int = 10
) {
    
    private val metrics = CircularBuffer<Float>(windowSize)
    
    fun detectAnomaly(value: Float): Boolean {
        metrics.add(value)
        
        if (metrics.size < windowSize) return false
        
        val mean = metrics.average()
        val stdDev = metrics.standardDeviation()
        
        // Z-score > 2 indicates anomaly
        return kotlin.math.abs((value - mean) / (stdDev + 0.001f)) > 2
    }
    
    private class CircularBuffer<T>(private val capacity: Int) {
        private val buffer = mutableListOf<T>()
        
        fun add(item: T) {
            buffer.add(item)
            if (buffer.size > capacity) {
                buffer.removeAt(0)
            }
        }
        
        fun average(): Float where T : Number {
            return buffer.map { (it as Number).toFloat() }.average().toFloat()
        }
        
        fun standardDeviation(): Float where T : Number {
            val mean = average()
            val variance = buffer.map { ((it as Number).toFloat() - mean) * ((it as Number).toFloat() - mean) }
                .average()
            return kotlin.math.sqrt(variance)
        }
        
        val size: Int get() = buffer.size
    }
}
```

---

## MONITORING & ANALYTICS

### Setup Analytics Tracking

```kotlin
class MetricsAnalytics(
    private val analyticsService: AnalyticsService,
    private val viewModel: SystemHealthViewModel
) {
    
    fun trackHealthMetrics() {
        viewModel.healthState.collect { health ->
            if (health != null) {
                analyticsService.logEvent(
                    name = "system_health_check",
                    params = mapOf(
                        "score" to health.score,
                        "status" to health.status.name,
                        "issues_count" to health.issues.size,
                        "has_critical_issues" to (health.status == HealthStatus.CRITICAL)
                    )
                )
            }
        }
    }
    
    fun trackPerformanceIssues() {
        viewModel.alertsState.collect { alerts ->
            alerts.forEach { alert ->
                analyticsService.logEvent(
                    name = "performance_issue",
                    params = mapOf(
                        "issue" to alert.message,
                        "severity" to alert.severity.name
                    )
                )
            }
        }
    }
    
    fun trackSessionMetrics() {
        // Collect metrics for entire session
        val sessionStart = System.currentTimeMillis()
        
        viewModel.metricsState.collect { metrics ->
            if (metrics != null) {
                val sessionDuration = System.currentTimeMillis() - sessionStart
                
                analyticsService.logEvent(
                    name = "session_metrics",
                    params = mapOf(
                        "cpu_avg" to metrics.cpuMetrics.usagePercent,
                        "memory_avg" to metrics.memoryMetrics.usagePercent,
                        "session_duration_ms" to sessionDuration
                    )
                )
            }
        }
    }
}
```

---

## SCALING CONSIDERATIONS

### For High-Traffic Applications

```kotlin
// Optimize for high-traffic apps
class ScalableMetricsManager(
    private val repository: IMetricsRepository
) {
    
    // Use sampling for high-traffic scenarios
    fun observeMetricsScalable(
        baseInterval: Long = 1000,
        trafficMultiplier: Float = 1f
    ): Flow<SystemMetrics> {
        val adjustedInterval = (baseInterval * trafficMultiplier).toLong()
        
        return repository.observeMetrics(adjustedInterval)
            .sample(adjustedInterval) // Emit only latest
    }
    
    // Limit history for memory constrained scenarios
    fun getHistoryScalable(
        requestedCount: Int = 60
    ): Flow<List<SystemMetrics>> = flow {
        val maxCount = minOf(requestedCount, 100) // Cap at 100
        val result = repository.getMetricsHistory(maxCount)
        emit(result.getOrElse { emptyList() })
    }
    
    // Batch operations for efficiency
    suspend fun collectBatchMetrics(samples: Int = 10): List<SystemMetrics> {
        val metrics = mutableListOf<SystemMetrics>()
        repeat(samples) {
            repository.getCurrentMetrics()
                .onSuccess { metrics.add(it) }
            delay(100) // Small delay between samples
        }
        return metrics
    }
}
```

---

## CONCLUSION

This guide provides **production-ready patterns** for integrating SysMetrics into enterprise Android applications.

**Key Takeaways:**

1. **Architecture First** - Use clean architecture patterns
2. **Performance Matters** - Optimize Flow operations for your use case
3. **Test Everything** - Use repository pattern for testability
4. **Monitor Production** - Implement analytics and alerting
5. **Scale Smartly** - Adjust monitoring for application load

**Next Steps:**

1. Choose patterns that fit your architecture
2. Implement in phases (start with basic monitoring)
3. Add analytics layer for insights
4. Optimize based on production metrics
5. Share learnings with team

---

**Version:** 1.0 Professional  
**Status:** Production Ready ✅  
**Last Updated:** December 25, 2025  

---

*Enterprise patterns for production systems.* 🚀

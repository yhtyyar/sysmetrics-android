package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a complete snapshot of all system metrics.
 *
 * This is the primary data class for collecting and analyzing system performance.
 * It aggregates CPU, memory, battery, thermal, and storage metrics along with
 * timing information.
 *
 * @property cpuMetrics Current CPU metrics
 * @property memoryMetrics Current memory metrics
 * @property batteryMetrics Current battery metrics
 * @property thermalMetrics Current thermal metrics
 * @property storageMetrics Current storage metrics
 * @property timestamp Unix timestamp in milliseconds when these metrics were collected
 * @property uptime System uptime in milliseconds
 */
@Serializable
public data class SystemMetrics(
    val cpuMetrics: CpuMetrics,
    val memoryMetrics: MemoryMetrics,
    val batteryMetrics: BatteryMetrics,
    val thermalMetrics: ThermalMetrics,
    val storageMetrics: StorageMetrics,
    val timestamp: Long,
    val uptime: Long
) {
    init {
        require(timestamp >= 0) { "timestamp must be non-negative" }
        require(uptime >= 0) { "uptime must be non-negative" }
    }

    /**
     * Calculates the overall health score for this metrics snapshot.
     *
     * The score is calculated using a weighted formula:
     * - CPU usage: 30% weight (lower is better)
     * - Memory usage: 35% weight (lower is better)
     * - Temperature: 20% weight (lower is better, normalized to 80°C max)
     * - Battery level: 15% weight (higher is better)
     *
     * @return A health score from 0 (critical) to 100 (excellent)
     */
    public fun getHealthScore(): Float {
        val cpuScore = (1f - cpuMetrics.usagePercent / 100f) * 0.30f
        val memoryScore = (1f - memoryMetrics.usagePercent / 100f) * 0.35f
        val tempScore = (1f - (thermalMetrics.cpuTemperature.coerceIn(0f, 80f) / 80f)) * 0.20f
        val batteryScore = (batteryMetrics.level / 100f) * 0.15f

        val rawScore = (cpuScore + memoryScore + tempScore + batteryScore) * 100f
        return rawScore.coerceIn(0f, 100f)
    }

    /**
     * Detects health issues based on current metrics values.
     *
     * @return List of detected health issues
     */
    public fun detectIssues(): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()

        if (cpuMetrics.usagePercent > 85f) {
            issues.add(HealthIssue.HIGH_CPU_USAGE)
        }
        if (memoryMetrics.usagePercent > 85f) {
            issues.add(HealthIssue.HIGH_MEMORY_USAGE)
        }
        if (thermalMetrics.cpuTemperature > 70f || thermalMetrics.batteryTemperature > 45f) {
            issues.add(HealthIssue.HIGH_TEMPERATURE)
        }
        if (batteryMetrics.level < 15) {
            issues.add(HealthIssue.LOW_BATTERY)
        }
        if (thermalMetrics.thermalThrottling) {
            issues.add(HealthIssue.THERMAL_THROTTLING)
        }
        if (storageMetrics.usagePercent > 90f) {
            issues.add(HealthIssue.LOW_STORAGE)
        }
        if (cpuMetrics.usagePercent > 90f && memoryMetrics.usagePercent > 90f) {
            issues.add(HealthIssue.POOR_PERFORMANCE)
        }

        return issues.toList()
    }

    /**
     * Generates recommendations based on detected issues.
     *
     * @param issues List of detected health issues
     * @return List of actionable recommendations
     */
    public fun generateRecommendations(issues: List<HealthIssue>): List<String> {
        val recommendations = mutableListOf<String>()

        issues.forEach { issue ->
            when (issue) {
                HealthIssue.HIGH_CPU_USAGE -> {
                    recommendations.add("Close unused applications to reduce CPU load")
                    recommendations.add("Check for background processes consuming CPU")
                }
                HealthIssue.HIGH_MEMORY_USAGE -> {
                    recommendations.add("Clear application cache to free up memory")
                    recommendations.add("Close memory-intensive applications")
                }
                HealthIssue.HIGH_TEMPERATURE -> {
                    recommendations.add("Allow device to cool down before heavy usage")
                    recommendations.add("Remove device case if overheating persists")
                }
                HealthIssue.LOW_BATTERY -> {
                    recommendations.add("Connect device to charger")
                    recommendations.add("Enable battery saver mode")
                }
                HealthIssue.THERMAL_THROTTLING -> {
                    recommendations.add("Reduce workload to prevent thermal throttling")
                    recommendations.add("Move device to a cooler environment")
                }
                HealthIssue.LOW_STORAGE -> {
                    recommendations.add("Delete unused files and applications")
                    recommendations.add("Move media to cloud storage")
                }
                HealthIssue.POOR_PERFORMANCE -> {
                    recommendations.add("Restart device to clear system resources")
                    recommendations.add("Consider reducing concurrent application usage")
                }
            }
        }

        return recommendations.toList()
    }

    public companion object {
        /**
         * Creates an empty SystemMetrics instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): SystemMetrics = SystemMetrics(
            cpuMetrics = CpuMetrics.empty(),
            memoryMetrics = MemoryMetrics.empty(),
            batteryMetrics = BatteryMetrics.empty(),
            thermalMetrics = ThermalMetrics.empty(),
            storageMetrics = StorageMetrics.empty(),
            timestamp = System.currentTimeMillis(),
            uptime = 0
        )
    }
}

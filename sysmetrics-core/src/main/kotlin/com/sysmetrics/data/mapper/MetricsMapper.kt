package com.sysmetrics.data.mapper

import com.sysmetrics.domain.model.HealthIssue
import com.sysmetrics.domain.model.HealthScore
import com.sysmetrics.domain.model.HealthStatus
import com.sysmetrics.domain.model.SystemMetrics

/**
 * Mapper for transforming metrics data between different representations.
 *
 * Provides utility functions for calculating health scores and
 * generating health-related data from raw metrics.
 */
public object MetricsMapper {

    private const val CPU_WEIGHT = 0.30f
    private const val MEMORY_WEIGHT = 0.35f
    private const val TEMPERATURE_WEIGHT = 0.20f
    private const val BATTERY_WEIGHT = 0.15f
    private const val MAX_TEMPERATURE = 80f

    private const val HIGH_CPU_THRESHOLD = 85f
    private const val HIGH_MEMORY_THRESHOLD = 85f
    private const val HIGH_TEMP_CPU_THRESHOLD = 70f
    private const val HIGH_TEMP_BATTERY_THRESHOLD = 45f
    private const val LOW_BATTERY_THRESHOLD = 15
    private const val LOW_STORAGE_THRESHOLD = 90f
    private const val POOR_PERFORMANCE_THRESHOLD = 90f

    /**
     * Calculates a health score from system metrics.
     *
     * Uses weighted formula:
     * - CPU: 30% (lower usage = higher score)
     * - Memory: 35% (lower usage = higher score)
     * - Temperature: 20% (lower temp = higher score, max 80°C)
     * - Battery: 15% (higher level = higher score)
     *
     * @param metrics The system metrics to evaluate
     * @return Calculated health score (0-100)
     */
    public fun calculateHealthScore(metrics: SystemMetrics): Float {
        val cpuScore = (1f - metrics.cpuMetrics.usagePercent / 100f) * CPU_WEIGHT
        val memoryScore = (1f - metrics.memoryMetrics.usagePercent / 100f) * MEMORY_WEIGHT
        val tempNormalized = metrics.thermalMetrics.cpuTemperature.coerceIn(0f, MAX_TEMPERATURE) / MAX_TEMPERATURE
        val tempScore = (1f - tempNormalized) * TEMPERATURE_WEIGHT
        val batteryScore = (metrics.batteryMetrics.level / 100f) * BATTERY_WEIGHT

        val rawScore = (cpuScore + memoryScore + tempScore + batteryScore) * 100f
        return rawScore.coerceIn(0f, 100f)
    }

    /**
     * Determines health status from a numerical score.
     *
     * @param score The health score (0-100)
     * @return Corresponding [HealthStatus]
     */
    public fun getStatusFromScore(score: Float): HealthStatus = when {
        score >= 80f -> HealthStatus.EXCELLENT
        score >= 60f -> HealthStatus.GOOD
        score >= 40f -> HealthStatus.WARNING
        else -> HealthStatus.CRITICAL
    }

    /**
     * Detects health issues from system metrics.
     *
     * Checks various thresholds and returns a list of detected issues.
     *
     * @param metrics The system metrics to analyze
     * @return List of detected [HealthIssue]s
     */
    public fun detectIssues(metrics: SystemMetrics): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()

        if (metrics.cpuMetrics.usagePercent > HIGH_CPU_THRESHOLD) {
            issues.add(HealthIssue.HIGH_CPU_USAGE)
        }

        if (metrics.memoryMetrics.usagePercent > HIGH_MEMORY_THRESHOLD) {
            issues.add(HealthIssue.HIGH_MEMORY_USAGE)
        }

        if (metrics.thermalMetrics.cpuTemperature > HIGH_TEMP_CPU_THRESHOLD ||
            metrics.thermalMetrics.batteryTemperature > HIGH_TEMP_BATTERY_THRESHOLD) {
            issues.add(HealthIssue.HIGH_TEMPERATURE)
        }

        if (metrics.batteryMetrics.level < LOW_BATTERY_THRESHOLD) {
            issues.add(HealthIssue.LOW_BATTERY)
        }

        if (metrics.thermalMetrics.thermalThrottling) {
            issues.add(HealthIssue.THERMAL_THROTTLING)
        }

        if (metrics.storageMetrics.usagePercent > LOW_STORAGE_THRESHOLD) {
            issues.add(HealthIssue.LOW_STORAGE)
        }

        if (metrics.cpuMetrics.usagePercent > POOR_PERFORMANCE_THRESHOLD &&
            metrics.memoryMetrics.usagePercent > POOR_PERFORMANCE_THRESHOLD) {
            issues.add(HealthIssue.POOR_PERFORMANCE)
        }

        return issues.toList()
    }

    /**
     * Generates recommendations based on detected issues.
     *
     * @param issues List of detected health issues
     * @return List of actionable recommendation strings
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

    /**
     * Creates a complete [HealthScore] from system metrics.
     *
     * @param metrics The system metrics to evaluate
     * @return Complete [HealthScore] with score, status, issues, and recommendations
     */
    public fun toHealthScore(metrics: SystemMetrics): HealthScore {
        val score = calculateHealthScore(metrics)
        val status = getStatusFromScore(score)
        val issues = detectIssues(metrics)
        val recommendations = generateRecommendations(issues)

        return HealthScore(
            score = score,
            status = status,
            issues = issues,
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
    }
}

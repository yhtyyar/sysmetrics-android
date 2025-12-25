package com.sysmetrics.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the overall health assessment of the system.
 *
 * Combines a numerical score with categorized status, detected issues,
 * and actionable recommendations for system optimization.
 *
 * @property score Numerical health score from 0 (critical) to 100 (excellent)
 * @property status Categorized health status based on the score
 * @property issues List of detected health issues requiring attention
 * @property recommendations List of actionable recommendations to improve system health
 * @property timestamp Unix timestamp in milliseconds when this score was calculated
 */
@Serializable
public data class HealthScore(
    val score: Float,
    val status: HealthStatus,
    val issues: List<HealthIssue>,
    val recommendations: List<String>,
    val timestamp: Long
) {
    init {
        require(score in 0f..100f) { "score must be between 0 and 100" }
        require(timestamp >= 0) { "timestamp must be non-negative" }
    }

    public companion object {
        /**
         * Creates an empty HealthScore instance with default values.
         * Useful for error fallback scenarios.
         */
        public fun empty(): HealthScore = HealthScore(
            score = 0f,
            status = HealthStatus.CRITICAL,
            issues = emptyList(),
            recommendations = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        /**
         * Determines the HealthStatus based on a numerical score.
         *
         * @param score The numerical health score (0-100)
         * @return The corresponding HealthStatus
         */
        public fun statusFromScore(score: Float): HealthStatus = when {
            score >= 80f -> HealthStatus.EXCELLENT
            score >= 60f -> HealthStatus.GOOD
            score >= 40f -> HealthStatus.WARNING
            else -> HealthStatus.CRITICAL
        }
    }
}

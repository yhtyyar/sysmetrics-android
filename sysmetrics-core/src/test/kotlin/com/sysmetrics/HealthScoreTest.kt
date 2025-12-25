package com.sysmetrics

import com.sysmetrics.domain.model.HealthIssue
import com.sysmetrics.domain.model.HealthScore
import com.sysmetrics.domain.model.HealthStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [HealthScore] data class.
 */
class HealthScoreTest {

    @Test
    fun `HealthScore creation with valid values succeeds`() {
        val healthScore = HealthScore(
            score = 75f,
            status = HealthStatus.GOOD,
            issues = listOf(HealthIssue.HIGH_CPU_USAGE),
            recommendations = listOf("Reduce CPU usage"),
            timestamp = System.currentTimeMillis()
        )
        
        assertEquals(75f, healthScore.score, 0.01f)
        assertEquals(HealthStatus.GOOD, healthScore.status)
        assertEquals(1, healthScore.issues.size)
        assertEquals(1, healthScore.recommendations.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HealthScore creation with score below 0 throws exception`() {
        HealthScore(
            score = -1f,
            status = HealthStatus.CRITICAL,
            issues = emptyList(),
            recommendations = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HealthScore creation with score above 100 throws exception`() {
        HealthScore(
            score = 101f,
            status = HealthStatus.EXCELLENT,
            issues = emptyList(),
            recommendations = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    @Test
    fun `statusFromScore returns EXCELLENT for 80 and above`() {
        assertEquals(HealthStatus.EXCELLENT, HealthScore.statusFromScore(80f))
        assertEquals(HealthStatus.EXCELLENT, HealthScore.statusFromScore(100f))
        assertEquals(HealthStatus.EXCELLENT, HealthScore.statusFromScore(85f))
    }

    @Test
    fun `statusFromScore returns GOOD for 60 to 79`() {
        assertEquals(HealthStatus.GOOD, HealthScore.statusFromScore(60f))
        assertEquals(HealthStatus.GOOD, HealthScore.statusFromScore(79.9f))
        assertEquals(HealthStatus.GOOD, HealthScore.statusFromScore(70f))
    }

    @Test
    fun `statusFromScore returns WARNING for 40 to 59`() {
        assertEquals(HealthStatus.WARNING, HealthScore.statusFromScore(40f))
        assertEquals(HealthStatus.WARNING, HealthScore.statusFromScore(59.9f))
        assertEquals(HealthStatus.WARNING, HealthScore.statusFromScore(50f))
    }

    @Test
    fun `statusFromScore returns CRITICAL for below 40`() {
        assertEquals(HealthStatus.CRITICAL, HealthScore.statusFromScore(39.9f))
        assertEquals(HealthStatus.CRITICAL, HealthScore.statusFromScore(0f))
        assertEquals(HealthStatus.CRITICAL, HealthScore.statusFromScore(20f))
    }

    @Test
    fun `empty creates valid default instance`() {
        val empty = HealthScore.empty()
        
        assertEquals(0f, empty.score, 0.01f)
        assertEquals(HealthStatus.CRITICAL, empty.status)
        assertTrue(empty.issues.isEmpty())
        assertTrue(empty.recommendations.isEmpty())
        assertTrue(empty.timestamp > 0)
    }

    @Test
    fun `HealthScore is immutable`() {
        val issues = mutableListOf(HealthIssue.HIGH_CPU_USAGE)
        val recommendations = mutableListOf("Test recommendation")
        
        val healthScore = HealthScore(
            score = 50f,
            status = HealthStatus.WARNING,
            issues = issues,
            recommendations = recommendations,
            timestamp = System.currentTimeMillis()
        )
        
        // Modify original lists
        issues.add(HealthIssue.LOW_BATTERY)
        recommendations.add("Another recommendation")
        
        // HealthScore should still have original values
        assertEquals(1, healthScore.issues.size)
        assertEquals(1, healthScore.recommendations.size)
    }

    @Test
    fun `HealthScore equality works correctly`() {
        val timestamp = System.currentTimeMillis()
        
        val score1 = HealthScore(
            score = 75f,
            status = HealthStatus.GOOD,
            issues = listOf(HealthIssue.HIGH_CPU_USAGE),
            recommendations = listOf("Test"),
            timestamp = timestamp
        )
        
        val score2 = HealthScore(
            score = 75f,
            status = HealthStatus.GOOD,
            issues = listOf(HealthIssue.HIGH_CPU_USAGE),
            recommendations = listOf("Test"),
            timestamp = timestamp
        )
        
        assertEquals(score1, score2)
        assertEquals(score1.hashCode(), score2.hashCode())
    }

    @Test
    fun `HealthScore copy works correctly`() {
        val original = HealthScore(
            score = 75f,
            status = HealthStatus.GOOD,
            issues = listOf(HealthIssue.HIGH_CPU_USAGE),
            recommendations = listOf("Test"),
            timestamp = System.currentTimeMillis()
        )
        
        val copied = original.copy(score = 90f, status = HealthStatus.EXCELLENT)
        
        assertEquals(90f, copied.score, 0.01f)
        assertEquals(HealthStatus.EXCELLENT, copied.status)
        assertEquals(original.issues, copied.issues)
        assertEquals(original.recommendations, copied.recommendations)
    }
}

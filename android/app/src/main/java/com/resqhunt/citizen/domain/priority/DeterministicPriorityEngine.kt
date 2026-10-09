package com.resqhunt.citizen.domain.priority

import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.SeverityLevel
import kotlin.math.max
import kotlin.math.min

data class PriorityEvaluation(
    val priorityScore: Float,
    val priorityCategory: SeverityLevel,
    val explanation: String,
    val ruleVersion: String = "1.0.0"
)

object DeterministicPriorityEngine {
    fun evaluate(
        category: EmergencyCategory,
        severity: SeverityLevel,
        affectedCount: Int,
        createdAtTimestampMs: Long,
        currentTimestampMs: Long = System.currentTimeMillis()
    ): PriorityEvaluation {
        // 1. Base Severity (Max 40)
        val severityScore = when (severity) {
            SeverityLevel.CRITICAL -> 40
            SeverityLevel.HIGH -> 30
            SeverityLevel.MEDIUM -> 20
            SeverityLevel.LOW -> 10
        }

        // 2. Category Urgency (Max 35)
        val categoryScore = when (category) {
            EmergencyCategory.MEDICAL -> 35
            EmergencyCategory.RESCUE -> 30
            EmergencyCategory.WATER -> 18
            EmergencyCategory.SHELTER -> 15
            EmergencyCategory.FOOD -> 12
            EmergencyCategory.OTHER -> 8
        }

        // 3. Affected Count Scale (Max 15)
        val clampedCount = max(1, affectedCount)
        val affectedScore = when {
            clampedCount >= 7 -> 15
            clampedCount >= 4 -> 11
            clampedCount >= 2 -> 7
            else -> 3
        }

        // 4. Waiting Time (Max 10)
        val elapsedMinutes = max(0L, (currentTimestampMs - createdAtTimestampMs) / (1000 * 60))
        val waitingScore = min(10, (elapsedMinutes / 30).toInt())

        var total = severityScore + categoryScore + affectedScore + waitingScore

        // Guaranteed floor for critical medical and rescue
        if (category == EmergencyCategory.MEDICAL && severity == SeverityLevel.CRITICAL) {
            total = max(80, total)
        }
        if (category == EmergencyCategory.RESCUE && (severity == SeverityLevel.CRITICAL || severity == SeverityLevel.HIGH)) {
            total = max(70, total)
        }

        val clampedScore = min(100, max(0, total)).toFloat()

        val prioCat = when {
            clampedScore >= 80f -> SeverityLevel.CRITICAL
            clampedScore >= 60f -> SeverityLevel.HIGH
            clampedScore >= 40f -> SeverityLevel.MEDIUM
            else -> SeverityLevel.LOW
        }

        val explanation = "Score $clampedScore/100: [Severity: $severity ($severityScore)] + [Category: $category ($categoryScore)] + [Victims: $clampedCount ($affectedScore)] + [Wait: ${elapsedMinutes}m ($waitingScore)]"

        return PriorityEvaluation(
            priorityScore = clampedScore,
            priorityCategory = prioCat,
            explanation = explanation
        )
    }
}

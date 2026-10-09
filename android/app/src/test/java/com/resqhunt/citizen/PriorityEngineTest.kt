package com.resqhunt.citizen

import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.SeverityLevel
import com.resqhunt.citizen.domain.priority.DeterministicPriorityEngine
import org.junit.Assert.*
import org.junit.Test

class PriorityEngineTest {

    @Test
    fun testCriticalMedicalFloorGuarantee() {
        val now = System.currentTimeMillis()
        val result = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.MEDICAL,
            severity = SeverityLevel.CRITICAL,
            affectedCount = 1,
            createdAtTimestampMs = now
        )

        assertTrue("Critical medical emergencies must maintain >= 80 priority score", result.priorityScore >= 80f)
        assertEquals(SeverityLevel.CRITICAL, result.priorityCategory)
        assertEquals("1.0.0", result.ruleVersion)
    }

    @Test
    fun testLowSeverityCategoryEvaluation() {
        val now = System.currentTimeMillis()
        val result = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.OTHER,
            severity = SeverityLevel.LOW,
            affectedCount = 1,
            createdAtTimestampMs = now
        )

        assertTrue(result.priorityScore < 40f)
        assertEquals(SeverityLevel.LOW, result.priorityCategory)
    }

    @Test
    fun testAffectedPersonsScaling() {
        val now = System.currentTimeMillis()
        val single = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.RESCUE,
            severity = SeverityLevel.MEDIUM,
            affectedCount = 1,
            createdAtTimestampMs = now
        )
        val group = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.RESCUE,
            severity = SeverityLevel.MEDIUM,
            affectedCount = 8,
            createdAtTimestampMs = now
        )

        assertTrue(group.priorityScore > single.priorityScore)
    }

    @Test
    fun testWaitingTimeElevation() {
        val now = System.currentTimeMillis()
        val twoHoursAgo = now - (120 * 60 * 1000L) // 120 mins = 4 wait pts

        val recent = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.WATER,
            severity = SeverityLevel.HIGH,
            affectedCount = 2,
            createdAtTimestampMs = now,
            currentTimestampMs = now
        )
        val delayed = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.WATER,
            severity = SeverityLevel.HIGH,
            affectedCount = 2,
            createdAtTimestampMs = twoHoursAgo,
            currentTimestampMs = now
        )

        assertEquals(4f, delayed.priorityScore - recent.priorityScore, 0.01f)
    }
}

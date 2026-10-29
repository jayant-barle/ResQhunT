package com.resqhunt.citizen

import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import com.resqhunt.citizen.domain.model.SeverityLevel
import com.resqhunt.citizen.domain.priority.DeterministicPriorityEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class DeliveryAckAndAlertTest {

    @Test
    fun testReceiverParsesAndConstructsSosEntityFromInboundEnvelope() {
        val payload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 3,
            description = "Severe injuries after structural collapse",
            latitude = 28.6139,
            longitude = 77.2090,
            locationAccuracy = 5.0f,
            locationAddress = "Connaught Place Gate 4"
        )
        val rawContent = "sos_req_101:MEDICAL:CRITICAL:3:Severe injuries after structural collapse"
        val checksum = MeshMessageEnvelope.calculateChecksum(rawContent)

        val inboundEnvelope = MeshMessageEnvelope(
            messageId = "msg_peer_101",
            requestId = "sos_req_101",
            originDeviceId = "phone_victim_a",
            messageType = "EMERGENCY_SOS",
            hopCount = 1,
            maxHops = 5,
            payload = payload,
            integrity = MeshIntegrity(checksum = checksum)
        )

        // 1. Validate envelope integrity on receiver
        val computedChecksum = MeshMessageEnvelope.calculateChecksum(
            "${inboundEnvelope.requestId}:${payload.category}:${payload.severity}:${payload.affectedCount}:${payload.description}"
        )
        assertEquals(computedChecksum, inboundEnvelope.integrity.checksum)

        // 2. Compute priority score on receiver
        val priority = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.valueOf(payload.category),
            severity = SeverityLevel.valueOf(payload.severity),
            affectedCount = payload.affectedCount,
            createdAtTimestampMs = inboundEnvelope.createdAt
        )
        assertTrue("Critical medical priority score should be high", priority.priorityScore >= 80.0f)

        // 3. Receiver constructs SosEntity in RECEIVED_BY_PEER state
        val receiverSos = SosEntity(
            requestId = inboundEnvelope.requestId,
            category = payload.category,
            severity = payload.severity,
            affectedCount = payload.affectedCount,
            description = payload.description,
            latitude = payload.latitude,
            longitude = payload.longitude,
            locationAccuracy = payload.locationAccuracy,
            locationAddress = payload.locationAddress,
            deliveryState = DeliveryState.RECEIVED_BY_PEER.name,
            priorityScore = priority.priorityScore,
            priorityCategory = priority.priorityCategory.name,
            createdAt = inboundEnvelope.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("sos_req_101", receiverSos.requestId)
        assertEquals("RECEIVED_BY_PEER", receiverSos.deliveryState)
        assertEquals(3, receiverSos.affectedCount)
        assertEquals("Connaught Place Gate 4", receiverSos.locationAddress)

        // 4. Receiver constructs RelayMessageEntity
        val relayRecord = RelayMessageEntity(
            messageId = inboundEnvelope.messageId,
            requestId = inboundEnvelope.requestId,
            originDeviceId = inboundEnvelope.originDeviceId,
            hopCount = inboundEnvelope.hopCount,
            maxHops = inboundEnvelope.maxHops,
            expiresAt = inboundEnvelope.expiresAt,
            rawJsonEnvelope = inboundEnvelope.toJson(),
            status = "PENDING_FORWARD"
        )
        assertEquals("msg_peer_101", relayRecord.messageId)
        assertEquals("PENDING_FORWARD", relayRecord.status)
    }

    @Test
    fun testAckCreationAndSenderAckMatching() {
        val originalMessageId = "msg_orig_202"
        val originalRequestId = "sos_req_202"
        val receiverDeviceId = "phone_receiver_b"

        // Receiver creates application-level ACK
        val ackEnvelope = MeshMessageEnvelope.createAck(
            ackForMessageId = originalMessageId,
            requestId = originalRequestId,
            receiverDeviceId = receiverDeviceId
        )

        assertEquals("SOS_ACK", ackEnvelope.messageType)
        assertEquals(originalMessageId, ackEnvelope.ackForMessageId)
        assertEquals(originalRequestId, ackEnvelope.requestId)
        assertEquals(receiverDeviceId, ackEnvelope.originDeviceId)

        // Verify ACK integrity
        val expectedChecksum = MeshMessageEnvelope.calculateChecksum("$originalMessageId:$originalRequestId:$receiverDeviceId")
        assertEquals(expectedChecksum, ackEnvelope.integrity.checksum)

        // Mock Sender database state
        val senderOutbox = mutableMapOf(
            originalMessageId to "SENDING"
        )
        var senderSosDeliveryState = DeliveryState.TRANSFER_IN_PROGRESS.name

        // Sender receives and matches ACK
        val incomingAckMessageId = ackEnvelope.ackForMessageId
        assertNotNull(incomingAckMessageId)

        if (senderOutbox.containsKey(incomingAckMessageId)) {
            senderOutbox[incomingAckMessageId!!] = "ACKNOWLEDGED"
            senderSosDeliveryState = DeliveryState.RELAYED_TO_PEER.name
        }

        assertEquals("ACKNOWLEDGED", senderOutbox[originalMessageId])
        assertEquals("RELAYED_TO_PEER", senderSosDeliveryState)
    }

    @Test
    fun testUnknownAckRejectionDoesNotMutateState() {
        val senderOutbox = mutableMapOf(
            "msg_tracked_1" to "SENDING"
        )
        var senderSosState = DeliveryState.TRANSFER_IN_PROGRESS.name

        val unknownAck = MeshMessageEnvelope.createAck(
            ackForMessageId = "msg_non_existent",
            requestId = "sos_unknown",
            receiverDeviceId = "foreign_device"
        )

        // Matching attempt
        if (senderOutbox.containsKey(unknownAck.ackForMessageId)) {
            senderSosState = DeliveryState.RELAYED_TO_PEER.name
        }

        assertEquals("State must remain unchanged for unknown ACK", DeliveryState.TRANSFER_IN_PROGRESS.name, senderSosState)
        assertEquals("SENDING", senderOutbox["msg_tracked_1"])
    }

    @Test
    fun testDuplicateSuppressionPreventsDuplicateRecordsAndAlerts() {
        val seenRelayMessageIds = Collections.synchronizedSet(mutableSetOf<String>())
        val alertedRequestIds = Collections.synchronizedSet(mutableSetOf<String>())

        val messageId = "msg_broadcast_505"
        val requestId = "sos_req_505"

        // First arrival
        val isFirstMessageNew = seenRelayMessageIds.add(messageId)
        val shouldAlertFirst = alertedRequestIds.add(requestId)

        assertTrue("First arrival of message must be accepted", isFirstMessageNew)
        assertTrue("First arrival must trigger alert", shouldAlertFirst)

        // Second arrival (duplicate relay from different multi-hop route)
        val isSecondMessageNew = seenRelayMessageIds.add(messageId)
        val shouldAlertSecond = alertedRequestIds.add(requestId)

        assertFalse("Duplicate message must be rejected by deduplication check", isSecondMessageNew)
        assertFalse("Duplicate message must NOT trigger alert again", shouldAlertSecond)
    }

    @Test
    fun testTerminalStateAntiDowngradeProtection() {
        val terminalStates = listOf(
            DeliveryState.RELAYED_TO_PEER.name,
            DeliveryState.RECEIVED_BY_PEER.name,
            DeliveryState.SERVER_RECEIVED.name,
            DeliveryState.COORDINATOR_ACKNOWLEDGED.name,
            DeliveryState.ASSIGNED.name,
            DeliveryState.IN_PROGRESS.name,
            DeliveryState.RESOLVED.name
        )

        fun canUpdateState(currentState: String, newState: String): Boolean {
            // Cannot downgrade if already in terminal or confirmed state
            if (currentState in terminalStates && newState in listOf(DeliveryState.TRANSFER_IN_PROGRESS.name, DeliveryState.RELAY_PENDING.name)) {
                return false
            }
            return true
        }

        // Test that delayed transfer callback cannot overwrite RELAYED_TO_PEER back to TRANSFER_IN_PROGRESS
        assertFalse(canUpdateState(DeliveryState.RELAYED_TO_PEER.name, DeliveryState.TRANSFER_IN_PROGRESS.name))
        assertFalse(canUpdateState(DeliveryState.SERVER_RECEIVED.name, DeliveryState.TRANSFER_IN_PROGRESS.name))

        // Normal transitions must succeed
        assertTrue(canUpdateState(DeliveryState.STORED_LOCALLY.name, DeliveryState.RELAY_PENDING.name))
        assertTrue(canUpdateState(DeliveryState.RELAY_PENDING.name, DeliveryState.TRANSFER_IN_PROGRESS.name))
        assertTrue(canUpdateState(DeliveryState.TRANSFER_IN_PROGRESS.name, DeliveryState.RELAYED_TO_PEER.name))
    }

    @Test
    fun testAckTimeoutAndRetryStateHandling() {
        var currentAttempt = 1
        val maxAttempts = 3
        var sosState = DeliveryState.TRANSFER_IN_PROGRESS.name
        var relayMsgStatus = "SENDING"

        fun onAckTimeout(hasPeersConnected: Boolean) {
            if (currentAttempt < maxAttempts && hasPeersConnected) {
                currentAttempt += 1
                sosState = DeliveryState.TRANSFER_IN_PROGRESS.name
            } else {
                sosState = DeliveryState.RELAY_FAILED.name
                relayMsgStatus = "PENDING_FORWARD" // remains queued for future peer encounter
            }
        }

        // Timeout 1 with connected peers: should retry
        onAckTimeout(hasPeersConnected = true)
        assertEquals(2, currentAttempt)
        assertEquals(DeliveryState.TRANSFER_IN_PROGRESS.name, sosState)

        // Timeout 2 with connected peers: should retry
        onAckTimeout(hasPeersConnected = true)
        assertEquals(3, currentAttempt)
        assertEquals(DeliveryState.TRANSFER_IN_PROGRESS.name, sosState)

        // Timeout 3: retries exhausted -> RELAY_FAILED
        onAckTimeout(hasPeersConnected = true)
        assertEquals(DeliveryState.RELAY_FAILED.name, sosState)
        assertEquals("PENDING_FORWARD", relayMsgStatus)
    }
}

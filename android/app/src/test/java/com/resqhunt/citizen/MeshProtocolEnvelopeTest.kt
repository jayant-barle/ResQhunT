package com.resqhunt.citizen

import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import org.junit.Assert.*
import org.junit.Test

class MeshProtocolEnvelopeTest {

    @Test
    fun testSerializationAndDeserialization() {
        val payload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 3,
            description = "Test severe trauma victims trapped",
            latitude = 28.6139,
            longitude = 77.2090
        )
        val rawContent = "sos_test_123:MEDICAL:CRITICAL:3:Test severe trauma victims trapped"
        val checksum = MeshMessageEnvelope.calculateChecksum(rawContent)

        val envelope = MeshMessageEnvelope(
            messageId = "msg_test_001",
            requestId = "sos_test_123",
            originDeviceId = "dev_alpha",
            messageType = "EMERGENCY_SOS",
            protocolVersion = 1,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
            hopCount = 1,
            maxHops = 5,
            payload = payload,
            integrity = MeshIntegrity(checksum = checksum)
        )

        val json = envelope.toJson()
        assertNotNull(json)
        assertTrue(json.contains("msg_test_001"))
        assertTrue(json.contains("sos_test_123"))

        val deserialized = MeshMessageEnvelope.fromJson(json)
        assertEquals("msg_test_001", deserialized.messageId)
        assertEquals("sos_test_123", deserialized.requestId)
        assertEquals("MEDICAL", deserialized.payload?.category)
        assertEquals(3, deserialized.payload?.affectedCount)
        assertEquals(checksum, deserialized.integrity.checksum)
    }

    @Test
    fun testChecksumCalculationConsistency() {
        val input = "sos_1:MEDICAL:CRITICAL:2:Debris collapse"
        val checksum1 = MeshMessageEnvelope.calculateChecksum(input)
        val checksum2 = MeshMessageEnvelope.calculateChecksum(input)

        assertEquals(checksum1, checksum2)
        assertEquals(64, checksum1.length) // SHA-256 produces 64 hex characters
    }

    @Test
    fun testTtlExpirationDetection() {
        val now = System.currentTimeMillis()
        val payload = MeshPayload("FOOD", "MEDIUM", 1, "Rations needed")
        val expiredEnvelope = MeshMessageEnvelope(
            messageId = "msg_expired",
            requestId = "sos_001",
            originDeviceId = "dev_x",
            expiresAt = now - 10000L, // expired 10 seconds ago
            payload = payload,
            integrity = MeshIntegrity("dummy")
        )
        assertTrue(expiredEnvelope.isExpired(now))

        val activeEnvelope = MeshMessageEnvelope(
            messageId = "msg_active",
            requestId = "sos_002",
            originDeviceId = "dev_x",
            expiresAt = now + 60000L, // active for 1 min
            payload = payload,
            integrity = MeshIntegrity("dummy")
        )
        assertFalse(activeEnvelope.isExpired(now))
    }

    @Test
    fun testHopIncrementAndLimit() {
        val payload = MeshPayload("RESCUE", "HIGH", 4, "Flood rescue")
        val env = MeshMessageEnvelope(
            messageId = "msg_hop",
            requestId = "sos_hop",
            originDeviceId = "dev_origin",
            hopCount = 1,
            maxHops = 5,
            payload = payload,
            integrity = MeshIntegrity("dummy")
        )

        assertFalse(env.hasExceededHops())
        val hop2 = env.nextHopEnvelope()
        assertEquals(2, hop2.hopCount)

        val hop5 = env.copy(hopCount = 5)
        assertTrue(hop5.hasExceededHops())
    }

    @Test
    fun testAckEnvelopeCreationAndValidation() {
        val originalMsgId = "msg_emergency_999"
        val requestId = "sos_emergency_999"
        val receiverId = "dev_receiver_node"

        val ack = MeshMessageEnvelope.createAck(
            ackForMessageId = originalMsgId,
            requestId = requestId,
            receiverDeviceId = receiverId
        )

        assertEquals("SOS_ACK", ack.messageType)
        assertEquals(originalMsgId, ack.ackForMessageId)
        assertEquals(requestId, ack.requestId)
        assertEquals(receiverId, ack.ackSenderDeviceId)
        assertEquals(1, ack.protocolVersion)
        assertNull(ack.payload)

        // Verify JSON roundtrip
        val json = ack.toJson()
        val parsedAck = MeshMessageEnvelope.fromJson(json)
        assertEquals("SOS_ACK", parsedAck.messageType)
        assertEquals(originalMsgId, parsedAck.ackForMessageId)
        assertEquals(requestId, parsedAck.requestId)
        assertEquals(receiverId, parsedAck.ackSenderDeviceId)
    }
}

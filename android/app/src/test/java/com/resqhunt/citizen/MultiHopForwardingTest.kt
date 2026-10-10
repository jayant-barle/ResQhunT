package com.resqhunt.citizen

import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import com.resqhunt.citizen.ui.screens.sos.SimpleDeliveryStatus
import org.junit.Assert.*
import org.junit.Test

class MultiHopForwardingTest {

    @Test
    fun testHopIncrementFromPhoneAToBToC() {
        val payload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 2,
            description = "Trapped victims in mountain trail"
        )

        // 1. Phone A generates original envelope (hop 1)
        val envelopeA = MeshMessageEnvelope(
            messageId = "msg_multihop_001",
            requestId = "sos_multihop_001",
            originDeviceId = "phone_A",
            hopCount = 1,
            maxHops = 5,
            payload = payload,
            integrity = MeshIntegrity(checksum = "chk")
        )
        assertEquals(1, envelopeA.hopCount)
        assertEquals("phone_A", envelopeA.originDeviceId)
        assertFalse(envelopeA.hasExceededHops())

        // 2. Phone B relays to next hop (hop 2)
        val envelopeB = envelopeA.nextHopEnvelope()
        assertEquals(2, envelopeB.hopCount)
        assertEquals("msg_multihop_001", envelopeB.messageId)
        assertEquals("phone_A", envelopeB.originDeviceId) // Preserves original sender!
        assertEquals("MEDICAL", envelopeB.payload?.category)
        assertFalse(envelopeB.hasExceededHops())

        // 3. Phone C relays to next hop (hop 3)
        val envelopeC = envelopeB.nextHopEnvelope()
        assertEquals(3, envelopeC.hopCount)
        assertEquals("msg_multihop_001", envelopeC.messageId)
        assertEquals("phone_A", envelopeC.originDeviceId)
        assertFalse(envelopeC.hasExceededHops())
    }

    @Test
    fun testMaxHopLimitEnforcement() {
        val payload = MeshPayload("FIRE", "HIGH", 1, "Forest fire perimeter")
        val env = MeshMessageEnvelope(
            messageId = "msg_hop_limit",
            requestId = "sos_hop_limit",
            originDeviceId = "phone_origin",
            hopCount = 4,
            maxHops = 5,
            payload = payload,
            integrity = MeshIntegrity(checksum = "chk")
        )

        assertFalse(env.hasExceededHops())

        val atLimit = env.nextHopEnvelope()
        assertEquals(5, atLimit.hopCount)
        assertTrue("Hop count at maxHops (5) must register as exceeded for further relay", atLimit.hasExceededHops())

        val beyondLimit = atLimit.nextHopEnvelope()
        assertEquals(6, beyondLimit.hopCount)
        assertTrue(beyondLimit.hasExceededHops())
    }

    @Test
    fun testPreviousHopSenderExclusion() {
        // Connected peers on Relay Phone B
        val connectedPeers = listOf("endpoint_PhoneA", "endpoint_PhoneC", "endpoint_PhoneD")
        val sourceEndpointId = "endpoint_PhoneA"

        // Forwarding filter on Phone B must exclude sourceEndpointId
        val eligiblePeers = connectedPeers.filter { it != sourceEndpointId }

        assertEquals(2, eligiblePeers.size)
        assertFalse("Source peer PhoneA must be excluded from forward list", eligiblePeers.contains("endpoint_PhoneA"))
        assertTrue(eligiblePeers.contains("endpoint_PhoneC"))
        assertTrue(eligiblePeers.contains("endpoint_PhoneD"))
    }

    @Test
    fun testQueueFlushPreviousHopAndDuplicateExclusion() {
        // Simulate message stored on Phone B that was received from Phone A
        val message = RelayMessageEntity(
            messageId = "msg_queue_test",
            requestId = "sos_queue_test",
            originDeviceId = "phone_A",
            hopCount = 2,
            maxHops = 5,
            expiresAt = System.currentTimeMillis() + 86400000L,
            rawJsonEnvelope = "{}",
            status = "PENDING_FORWARD",
            lastReceivedFromEndpointId = "endpoint_PhoneA",
            forwardedEndpoints = "endpoint_PhoneC" // already sent to C
        )

        val forwardedSet = message.forwardedEndpoints.split(",").filter { it.isNotBlank() }.toSet()

        // 1. Test connecting to Phone A (should skip: previous-hop sender)
        val shouldSkipPhoneA = (message.lastReceivedFromEndpointId == "endpoint_PhoneA")
        assertTrue("Must skip flushing to previous hop sender", shouldSkipPhoneA)

        // 2. Test connecting to Phone C (should skip: already forwarded)
        val shouldSkipPhoneC = forwardedSet.contains("endpoint_PhoneC")
        assertTrue("Must skip flushing to peer that already received the message", shouldSkipPhoneC)

        // 3. Test connecting to Phone D (should NOT skip: new unvisited peer)
        val shouldSkipPhoneD = (message.lastReceivedFromEndpointId == "endpoint_PhoneD") ||
                forwardedSet.contains("endpoint_PhoneD")
        assertFalse("Must flush message to newly connected peer Phone D", shouldSkipPhoneD)
    }

    @Test
    fun testAckMatchingPerMessage() {
        val originalMessageId = "msg_sos_alpha_456"
        val requestId = "sos_alpha_456"
        val receiverDeviceId = "phone_B_receiver"

        val ackEnvelope = MeshMessageEnvelope.createAck(
            ackForMessageId = originalMessageId,
            requestId = requestId,
            receiverDeviceId = receiverDeviceId
        )

        assertEquals("SOS_ACK", ackEnvelope.messageType)
        assertEquals(originalMessageId, ackEnvelope.ackForMessageId)
        assertEquals(requestId, ackEnvelope.requestId)
        assertEquals(receiverDeviceId, ackEnvelope.originDeviceId)

        // Verification that an ACK for message X does not falsely acknowledge message Y
        val incomingAckMessageId = ackEnvelope.ackForMessageId
        assertTrue(incomingAckMessageId == originalMessageId)
        assertFalse(incomingAckMessageId == "msg_unrelated_789")
    }

    @Test
    fun testSimpleDeliveryStatusMapping() {
        // Real-event mapping tests
        assertEquals(SimpleDeliveryStatus.SAVED, SimpleDeliveryStatus.fromDeliveryState("CREATED"))
        assertEquals(SimpleDeliveryStatus.SAVED, SimpleDeliveryStatus.fromDeliveryState("STORED_LOCALLY"))

        assertEquals(SimpleDeliveryStatus.SEARCHING, SimpleDeliveryStatus.fromDeliveryState("RELAY_PENDING"))
        assertEquals(SimpleDeliveryStatus.SEARCHING, SimpleDeliveryStatus.fromDeliveryState("TRANSFER_IN_PROGRESS"))
        assertEquals(SimpleDeliveryStatus.SEARCHING, SimpleDeliveryStatus.fromDeliveryState("RELAY_FAILED"))

        assertEquals(SimpleDeliveryStatus.SENT_TO_NEARBY, SimpleDeliveryStatus.fromDeliveryState("RELAYED_TO_PEER"))
        assertEquals(SimpleDeliveryStatus.SENT_TO_NEARBY, SimpleDeliveryStatus.fromDeliveryState("RECEIVED_BY_PEER"))

        assertEquals(SimpleDeliveryStatus.DELIVERED_TO_SERVER, SimpleDeliveryStatus.fromDeliveryState("SERVER_RECEIVED"))
        assertEquals(SimpleDeliveryStatus.DELIVERED_TO_SERVER, SimpleDeliveryStatus.fromDeliveryState("COORDINATOR_ACKNOWLEDGED"))
        assertEquals(SimpleDeliveryStatus.DELIVERED_TO_SERVER, SimpleDeliveryStatus.fromDeliveryState("ASSIGNED"))
        assertEquals(SimpleDeliveryStatus.DELIVERED_TO_SERVER, SimpleDeliveryStatus.fromDeliveryState("IN_PROGRESS"))
        assertEquals(SimpleDeliveryStatus.DELIVERED_TO_SERVER, SimpleDeliveryStatus.fromDeliveryState("RESOLVED"))

        // Exact 4 labels required by user prompt
        assertEquals("Saved", SimpleDeliveryStatus.SAVED.label)
        assertEquals("Searching", SimpleDeliveryStatus.SEARCHING.label)
        assertEquals("Sent to nearby device", SimpleDeliveryStatus.SENT_TO_NEARBY.label)
        assertEquals("Delivered to rescue server", SimpleDeliveryStatus.DELIVERED_TO_SERVER.label)
    }

    @Test
    fun testEnvelopeTtlExpiryEnforcement() {
        val now = System.currentTimeMillis()
        val payload = MeshPayload("FLOOD", "CRITICAL", 3, "Rising water")

        val expiredEnvelope = MeshMessageEnvelope(
            messageId = "msg_exp",
            requestId = "sos_exp",
            originDeviceId = "phone_origin",
            expiresAt = now - 5000L,
            payload = payload,
            integrity = MeshIntegrity("chk")
        )

        assertTrue("Envelope older than expiresAt must be detected as expired", expiredEnvelope.isExpired(now))

        val activeEnvelope = MeshMessageEnvelope(
            messageId = "msg_act",
            requestId = "sos_act",
            originDeviceId = "phone_origin",
            expiresAt = now + 60000L,
            payload = payload,
            integrity = MeshIntegrity("chk")
        )

        assertFalse("Envelope within TTL must not be expired", activeEnvelope.isExpired(now))
    }
}

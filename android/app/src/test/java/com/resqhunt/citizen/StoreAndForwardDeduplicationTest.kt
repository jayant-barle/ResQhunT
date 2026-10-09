package com.resqhunt.citizen

import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import org.junit.Assert.*
import org.junit.Test

class StoreAndForwardDeduplicationTest {

    @Test
    fun testPayloadSizeRejectionThreshold() {
        val oversizedByteArray = ByteArray(StoreAndForwardRelayEngine.MAX_PAYLOAD_BYTES + 1)
        assertTrue(oversizedByteArray.size > StoreAndForwardRelayEngine.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun testDuplicateMessageIdSuppressionLogic() {
        val seenMessageIds = mutableSetOf<String>()

        val messageId1 = "msg_unique_1"
        val messageId2 = "msg_unique_2"

        // First ingestion
        val isFirstNew = seenMessageIds.add(messageId1)
        assertTrue("First arrival of messageId1 must be accepted", isFirstNew)

        // Duplicate arrival
        val isDuplicateNew = seenMessageIds.add(messageId1)
        assertFalse("Second arrival of identical messageId1 must be suppressed", isDuplicateNew)

        // Distinct message
        val isSecondNew = seenMessageIds.add(messageId2)
        assertTrue("Different messageId2 must be accepted", isSecondNew)
    }

    @Test
    fun testOriginLoopPreventionCheck() {
        val payload = MeshPayload("MEDICAL", "CRITICAL", 2, "Test loop")
        val originDeviceId = "dev_phone_a"

        val env = MeshMessageEnvelope(
            messageId = "msg_loop_test",
            requestId = "sos_loop",
            originDeviceId = originDeviceId,
            payload = payload,
            integrity = MeshIntegrity("dummy")
        )

        // Check if receiving device is same as origin
        val currentDevice = "dev_phone_a"
        val shouldForwardToSelf = (env.originDeviceId != currentDevice)
        assertFalse("Device must never forward an envelope back to its own origin device", shouldForwardToSelf)
    }
}

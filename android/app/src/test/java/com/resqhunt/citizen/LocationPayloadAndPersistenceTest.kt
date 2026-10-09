package com.resqhunt.citizen

import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import org.junit.Assert.*
import org.junit.Test

class LocationPayloadAndPersistenceTest {

    @Test
    fun testPayloadWithFreshGpsSerialization() {
        val fixTime = 1728560000000L
        val payload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 2,
            description = "Earthquake collapse, need urgent evacuation",
            latitude = 28.613939,
            longitude = 77.209021,
            locationAccuracy = 4.5f,
            locationAddress = "Connaught Place, New Delhi",
            locationTimestamp = fixTime,
            locationSource = "FRESH_GPS"
        )

        val envelope = MeshMessageEnvelope(
            messageId = "msg_fresh_001",
            requestId = "sos_req_001",
            originDeviceId = "phone_alpha",
            payload = payload,
            integrity = MeshIntegrity(checksum = "dummy_checksum")
        )

        val json = envelope.toJson()
        assertNotNull(json)
        assertTrue(json.contains("FRESH_GPS"))
        assertTrue(json.contains("28.613939"))
        assertTrue(json.contains("77.209021"))
        assertTrue(json.contains("1728560000000"))

        val deserialized = MeshMessageEnvelope.fromJson(json)
        assertNotNull(deserialized.payload)
        assertEquals(28.613939, deserialized.payload?.latitude ?: 0.0, 0.000001)
        assertEquals(77.209021, deserialized.payload?.longitude ?: 0.0, 0.000001)
        assertEquals(4.5f, deserialized.payload?.locationAccuracy ?: 0.0f, 0.01f)
        assertEquals("FRESH_GPS", deserialized.payload?.locationSource)
        assertEquals(fixTime, deserialized.payload?.locationTimestamp)
        assertEquals("Connaught Place, New Delhi", deserialized.payload?.locationAddress)
    }

    @Test
    fun testPayloadWithLastKnownLocationSerialization() {
        val cachedFixTime = 1728500000000L // 60,000s earlier
        val payload = MeshPayload(
            category = "TRAPPED",
            severity = "HIGH",
            affectedCount = 1,
            description = "Basement shelter, satellite signal lost",
            latitude = 19.076090,
            longitude = 72.877426,
            locationAccuracy = 25.0f,
            locationAddress = "Bandra Kurla Complex",
            locationTimestamp = cachedFixTime,
            locationSource = "LAST_KNOWN"
        )

        val envelope = MeshMessageEnvelope(
            messageId = "msg_last_known_002",
            requestId = "sos_req_002",
            originDeviceId = "phone_beta",
            payload = payload,
            integrity = MeshIntegrity(checksum = "dummy_checksum")
        )

        val json = envelope.toJson()
        val deserialized = MeshMessageEnvelope.fromJson(json)

        assertNotNull(deserialized.payload)
        assertEquals("LAST_KNOWN", deserialized.payload?.locationSource)
        assertEquals(cachedFixTime, deserialized.payload?.locationTimestamp)
        assertEquals(19.076090, deserialized.payload?.latitude ?: 0.0, 0.000001)
        assertEquals(72.877426, deserialized.payload?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun testPayloadWithUnavailableLocationDoesNotFabricateCoordinates() {
        val payload = MeshPayload(
            category = "FIRE",
            severity = "CRITICAL",
            affectedCount = 5,
            description = "Subway tunnel emergency, zero GPS visibility",
            latitude = null,
            longitude = null,
            locationAccuracy = null,
            locationAddress = "Underground Station Gate 4",
            locationTimestamp = null,
            locationSource = "UNAVAILABLE"
        )

        val envelope = MeshMessageEnvelope(
            messageId = "msg_no_gps_003",
            requestId = "sos_req_003",
            originDeviceId = "phone_gamma",
            payload = payload,
            integrity = MeshIntegrity(checksum = "dummy_checksum")
        )

        val json = envelope.toJson()
        val deserialized = MeshMessageEnvelope.fromJson(json)

        assertNotNull(deserialized.payload)
        assertNull("Latitude must be null when unavailable, NEVER 0.0", deserialized.payload?.latitude)
        assertNull("Longitude must be null when unavailable, NEVER 0.0", deserialized.payload?.longitude)
        assertEquals("UNAVAILABLE", deserialized.payload?.locationSource)
        assertNull(deserialized.payload?.locationTimestamp)
    }

    @Test
    fun testRelayNextHopPreservesLocationMetadata() {
        val originalPayload = MeshPayload(
            category = "MEDICAL",
            severity = "HIGH",
            affectedCount = 1,
            description = "Injured climber",
            latitude = 34.083656,
            longitude = 74.797279,
            locationAccuracy = 12.0f,
            locationAddress = "Pahalgam Trail Marker 4",
            locationTimestamp = 1728555555000L,
            locationSource = "FRESH_GPS"
        )

        val hop1 = MeshMessageEnvelope(
            messageId = "msg_hop_loc",
            requestId = "sos_hop_loc",
            originDeviceId = "phone_origin",
            hopCount = 1,
            maxHops = 5,
            payload = originalPayload,
            integrity = MeshIntegrity(checksum = "chk")
        )

        val hop2 = hop1.nextHopEnvelope()
        assertEquals(2, hop2.hopCount)
        assertNotNull(hop2.payload)
        assertEquals(hop1.payload?.latitude, hop2.payload?.latitude)
        assertEquals(hop1.payload?.longitude, hop2.payload?.longitude)
        assertEquals(hop1.payload?.locationTimestamp, hop2.payload?.locationTimestamp)
        assertEquals(hop1.payload?.locationSource, hop2.payload?.locationSource)
        assertEquals("FRESH_GPS", hop2.payload?.locationSource)

        val hop3 = hop2.nextHopEnvelope()
        assertEquals(3, hop3.hopCount)
        assertEquals(hop1.payload?.locationTimestamp, hop3.payload?.locationTimestamp)
        assertEquals(hop1.payload?.locationSource, hop3.payload?.locationSource)
    }

    @Test
    fun testSosEntityLocationPersistenceFields() {
        val now = System.currentTimeMillis()
        val entity = SosEntity(
            requestId = "sos_entity_test",
            category = "RESCUE",
            severity = "CRITICAL",
            affectedCount = 3,
            description = "Trapped in flood waters",
            latitude = 25.594095,
            longitude = 85.137566,
            locationAddress = "Patna Bypass",
            locationTimestamp = now - 5000L,
            locationSource = "LAST_KNOWN",
            deliveryState = "STORED_LOCALLY",
            priorityScore = 85.0f,
            priorityCategory = "CRITICAL",
            createdAt = now
        )

        assertEquals("sos_entity_test", entity.requestId)
        assertEquals(25.594095, entity.latitude ?: 0.0, 0.000001)
        assertEquals(85.137566, entity.longitude ?: 0.0, 0.000001)
        assertEquals(now - 5000L, entity.locationTimestamp)
        assertEquals("LAST_KNOWN", entity.locationSource)
    }

    @Test
    fun testCoordinateValidationLogic() {
        // Validation check for 0.0, 0.0 (Null Island)
        fun isValid(lat: Double?, lon: Double?): Boolean {
            if (lat == null || lon == null) return false
            if (lat == 0.0 && lon == 0.0) return false
            if (lat.isNaN() || lon.isNaN()) return false
            if (lat < -90.0 || lat > 90.0) return false
            if (lon < -180.0 || lon > 180.0) return false
            return true
        }

        assertFalse("Null Island (0,0) must be rejected", isValid(0.0, 0.0))
        assertFalse("Null coords must be rejected", isValid(null, null))
        assertFalse("Out of range latitude must be rejected", isValid(95.0, 77.0))
        assertFalse("Out of range longitude must be rejected", isValid(28.0, 190.0))
        assertTrue("Valid coordinates must pass", isValid(28.6139, 77.2090))
    }
}

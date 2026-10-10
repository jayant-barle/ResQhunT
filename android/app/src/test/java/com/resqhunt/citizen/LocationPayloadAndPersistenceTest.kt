package com.resqhunt.citizen

import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import com.resqhunt.citizen.location.EmergencyLocationManager
import com.resqhunt.citizen.location.LocationFix
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
            latitude = 28.6139391234,
            longitude = 77.2090215678,
            locationAccuracy = 4.5f,
            locationAddress = "Sector 4 Emergency Zone",
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
        assertTrue(json.contains("28.6139391234"))
        assertTrue(json.contains("77.2090215678"))
        assertTrue(json.contains("1728560000000"))

        val deserialized = MeshMessageEnvelope.fromJson(json)
        assertNotNull(deserialized.payload)
        assertEquals(28.6139391234, deserialized.payload?.latitude ?: 0.0, 0.0000000001)
        assertEquals(77.2090215678, deserialized.payload?.longitude ?: 0.0, 0.0000000001)
        assertEquals(4.5f, deserialized.payload?.locationAccuracy ?: 0.0f, 0.01f)
        assertEquals("FRESH_GPS", deserialized.payload?.locationSource)
        assertEquals(fixTime, deserialized.payload?.locationTimestamp)
        assertEquals("Sector 4 Emergency Zone", deserialized.payload?.locationAddress)
    }

    @Test
    fun testPayloadWithLastKnownLocationSerialization() {
        val cachedFixTime = 1728500000000L
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
            locationAddress = "Location unavailable",
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
        assertNull("Accuracy must be null when unavailable", deserialized.payload?.locationAccuracy)
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
        assertEquals(hop1.payload?.locationAccuracy, hop2.payload?.locationAccuracy)
        assertEquals(hop1.payload?.locationTimestamp, hop2.payload?.locationTimestamp)
        assertEquals(hop1.payload?.locationSource, hop2.payload?.locationSource)
        assertEquals("phone_origin", hop2.originDeviceId)

        val hop3 = hop2.nextHopEnvelope()
        assertEquals(3, hop3.hopCount)
        assertEquals(hop1.payload?.latitude, hop3.payload?.latitude)
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
            locationAccuracy = 8.2f,
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
        assertEquals(8.2f, entity.locationAccuracy ?: 0.0f, 0.01f)
        assertEquals(now - 5000L, entity.locationTimestamp)
        assertEquals("LAST_KNOWN", entity.locationSource)
    }

    @Test
    fun testCoordinateValidationLogic() {
        assertFalse("Null Island (0,0) must be rejected", EmergencyLocationManager.isValidCoordinates(0.0, 0.0))
        assertFalse("Null coords must be rejected", EmergencyLocationManager.isValidCoordinates(null, null))
        assertFalse("Null latitude must be rejected", EmergencyLocationManager.isValidCoordinates(null, 77.0))
        assertFalse("Null longitude must be rejected", EmergencyLocationManager.isValidCoordinates(28.0, null))
        assertFalse("Out of range latitude (>90) must be rejected", EmergencyLocationManager.isValidCoordinates(95.0, 77.0))
        assertFalse("Out of range latitude (<-90) must be rejected", EmergencyLocationManager.isValidCoordinates(-91.0, 77.0))
        assertFalse("Out of range longitude (>180) must be rejected", EmergencyLocationManager.isValidCoordinates(28.0, 190.0))
        assertFalse("Out of range longitude (<-180) must be rejected", EmergencyLocationManager.isValidCoordinates(28.0, -181.0))
        assertFalse("NaN latitude must be rejected", EmergencyLocationManager.isValidCoordinates(Double.NaN, 77.0))
        assertFalse("NaN longitude must be rejected", EmergencyLocationManager.isValidCoordinates(28.0, Double.NaN))
        assertTrue("Valid coordinates must pass", EmergencyLocationManager.isValidCoordinates(28.613939, 77.209021))
        assertTrue("Equator coordinate must pass", EmergencyLocationManager.isValidCoordinates(0.0001, 0.0001))
    }

    @Test
    fun testNewerLocationUpdateReplacesStaleLocation() {
        val initialTime = 1000L
        val existingSos = SosEntity(
            requestId = "sos_req_dyn",
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "Rapid trigger with cached location",
            latitude = 12.9716,
            longitude = 77.5946,
            locationAccuracy = 45.0f,
            locationAddress = "Coordinates Locked (LAST_KNOWN)",
            locationTimestamp = initialTime,
            locationSource = "LAST_KNOWN",
            deliveryState = "STORED_LOCALLY",
            priorityScore = 90.0f,
            priorityCategory = "CRITICAL",
            createdAt = initialTime
        )

        val freshFixTime = 5000L // 4 seconds later
        val incomingPayload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "Rapid trigger with fresh satellite fix",
            latitude = 12.972156,
            longitude = 77.595123,
            locationAccuracy = 3.8f,
            locationAddress = "GPS Coordinates Locked (FRESH_GPS)",
            locationTimestamp = freshFixTime,
            locationSource = "FRESH_GPS"
        )

        // Evaluate update condition matching StoreAndForwardRelayEngine logic
        val incomingTimestamp = incomingPayload.locationTimestamp ?: 0L
        val existingTimestamp = existingSos.locationTimestamp ?: 0L
        val hasValidIncomingCoords = EmergencyLocationManager.isValidCoordinates(incomingPayload.latitude, incomingPayload.longitude)
        val isNewer = hasValidIncomingCoords && (
            existingSos.latitude == null ||
            (incomingTimestamp >= existingTimestamp && incomingPayload.locationSource != "UNAVAILABLE")
        )

        assertTrue("Newer valid GPS fix from sender must be accepted", isNewer)

        val updatedSos = existingSos.copy(
            latitude = incomingPayload.latitude,
            longitude = incomingPayload.longitude,
            locationAccuracy = incomingPayload.locationAccuracy,
            locationTimestamp = incomingTimestamp,
            locationSource = incomingPayload.locationSource ?: "FRESH_GPS",
            updatedAt = System.currentTimeMillis()
        )

        assertEquals(12.972156, updatedSos.latitude ?: 0.0, 0.000001)
        assertEquals(77.595123, updatedSos.longitude ?: 0.0, 0.000001)
        assertEquals(3.8f, updatedSos.locationAccuracy ?: 0.0f, 0.01f)
        assertEquals("FRESH_GPS", updatedSos.locationSource)
        assertEquals(freshFixTime, updatedSos.locationTimestamp)
    }

    @Test
    fun testOlderLocationUpdateDoesNotOverwriteFreshLocation() {
        val existingSos = SosEntity(
            requestId = "sos_req_fresh",
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "High accuracy lock already held",
            latitude = 12.972156,
            longitude = 77.595123,
            locationAccuracy = 3.5f,
            locationAddress = "GPS Coordinates Locked (FRESH_GPS)",
            locationTimestamp = 10000L,
            locationSource = "FRESH_GPS",
            deliveryState = "STORED_LOCALLY",
            priorityScore = 90.0f,
            priorityCategory = "CRITICAL",
            createdAt = 8000L
        )

        // Older out-of-order packet arriving with stale timestamp
        val stalePayload = MeshPayload(
            category = "MEDICAL",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "Stale packet",
            latitude = 12.970000,
            longitude = 77.590000,
            locationAccuracy = 50.0f,
            locationTimestamp = 5000L, // Older than existing 10000L
            locationSource = "LAST_KNOWN"
        )

        val incomingTimestamp = stalePayload.locationTimestamp ?: 0L
        val existingTimestamp = existingSos.locationTimestamp ?: 0L
        val hasValidIncomingCoords = EmergencyLocationManager.isValidCoordinates(stalePayload.latitude, stalePayload.longitude)
        val shouldUpdate = hasValidIncomingCoords && (
            existingSos.latitude == null ||
            (incomingTimestamp >= existingTimestamp && stalePayload.locationSource != "UNAVAILABLE")
        )

        assertFalse("Stale out-of-order location packet must NOT overwrite fresh location", shouldUpdate)
    }

    @Test
    fun testLocationFixFreshAndApproximateFlags() {
        val now = System.currentTimeMillis()
        val freshFix = LocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracy = 4.2f,
            timestamp = now - 5000L, // 5s old
            source = "FRESH_GPS",
            provider = "fused"
        )
        assertTrue("Fix < 60s old with FRESH_GPS must be considered fresh", freshFix.isFresh)
        assertFalse("Fix with 4.2m accuracy must not be approximate", freshFix.isApproximate)

        val staleFix = LocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracy = 15.0f,
            timestamp = now - 120_000L, // 2 mins old
            source = "LAST_KNOWN",
            provider = "fused"
        )
        assertFalse("Fix > 60s old or marked LAST_KNOWN must not be fresh", staleFix.isFresh)

        val approximateFix = LocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracy = 1500.0f, // 1.5km coarse radius
            timestamp = now,
            source = "APPROXIMATE_COARSE",
            provider = "network"
        )
        assertTrue("Coarse source or >100m accuracy must be approximate", approximateFix.isApproximate)
    }
}

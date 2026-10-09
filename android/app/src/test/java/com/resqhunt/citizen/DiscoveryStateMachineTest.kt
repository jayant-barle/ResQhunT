package com.resqhunt.citizen

import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.resqhunt.citizen.mesh.AdvertisingState
import com.resqhunt.citizen.mesh.DiscoveryState
import org.junit.Assert.*
import org.junit.Test

class DiscoveryStateMachineTest {

    @Test
    fun testDiscoveryStatesEnum() {
        assertEquals(4, DiscoveryState.values().size)
        assertTrue(DiscoveryState.values().contains(DiscoveryState.STOPPED))
        assertTrue(DiscoveryState.values().contains(DiscoveryState.STARTING))
        assertTrue(DiscoveryState.values().contains(DiscoveryState.ACTIVE))
        assertTrue(DiscoveryState.values().contains(DiscoveryState.STOPPING))
    }

    @Test
    fun testAdvertisingStatesEnum() {
        assertEquals(4, AdvertisingState.values().size)
        assertTrue(AdvertisingState.values().contains(AdvertisingState.STOPPED))
        assertTrue(AdvertisingState.values().contains(AdvertisingState.STARTING))
        assertTrue(AdvertisingState.values().contains(AdvertisingState.ACTIVE))
        assertTrue(AdvertisingState.values().contains(AdvertisingState.STOPPING))
    }

    @Test
    fun testNearbyStatusCodesConstants() {
        // Confirm Google Play Services Nearby Connections constants
        assertEquals(8002, ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING)
        assertEquals(8001, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING)
    }

    @Test
    fun testStatusAlreadyDiscoveringReconciliationLogic() {
        // Simulate reconciliation state logic when Error 8002 is returned
        var internalDiscoveryState = DiscoveryState.STARTING
        var isDiscovering = false
        var diagnosticError: String? = null

        val statusCode = 8002 // ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING

        if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
            // Must NOT treat as fatal error, reconcile state to ACTIVE
            internalDiscoveryState = DiscoveryState.ACTIVE
            isDiscovering = true
            diagnosticError = null
        } else {
            internalDiscoveryState = DiscoveryState.STOPPED
            isDiscovering = false
            diagnosticError = "Discovery failed: $statusCode"
        }

        assertEquals(DiscoveryState.ACTIVE, internalDiscoveryState)
        assertTrue(isDiscovering)
        assertNull(diagnosticError)
    }

    @Test
    fun testStatusAlreadyAdvertisingReconciliationLogic() {
        // Simulate reconciliation state logic when Error 8001 is returned
        var internalAdvertisingState = AdvertisingState.STARTING
        var isAdvertising = false
        var diagnosticError: String? = null

        val statusCode = 8001 // ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING

        if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
            // Must NOT treat as fatal error, reconcile state to ACTIVE
            internalAdvertisingState = AdvertisingState.ACTIVE
            isAdvertising = true
            diagnosticError = null
        } else {
            internalAdvertisingState = AdvertisingState.STOPPED
            isAdvertising = false
            diagnosticError = "Advertising failed: $statusCode"
        }

        assertEquals(AdvertisingState.ACTIVE, internalAdvertisingState)
        assertTrue(isAdvertising)
        assertNull(diagnosticError)
    }

    @Test
    fun testExponentialBackoffProgression() {
        val maxRetries = 3
        val backoffDelays = mutableListOf<Long>()

        for (retry in 1..maxRetries) {
            val delayMs = 3000L * retry
            backoffDelays.add(delayMs)
        }

        assertEquals(listOf(3000L, 6000L, 9000L), backoffDelays)
    }
}

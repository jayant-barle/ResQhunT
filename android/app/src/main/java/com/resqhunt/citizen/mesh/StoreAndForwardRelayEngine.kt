package com.resqhunt.citizen.mesh

import android.content.Context
import android.util.Log
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.PeerEntity
import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class StoreAndForwardRelayEngine(
    private val context: Context,
    private val nearbyManager: NearbyConnectionsManager,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    companion object {
        private const val TAG = "ResQhunT_RelayEngine"
        const val MAX_ALLOWED_HOPS = 5
        const val SUPPORTED_PROTOCOL_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 100 * 1024 // 100 KB limit for mesh envelopes
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val deviceId = "dev_" + UUID.randomUUID().toString().take(8)

    init {
        // Wire nearby connection listeners
        nearbyManager.onPayloadReceived = { bytes, sourceEndpointId ->
            scope.launch {
                handleInboundPayload(bytes, sourceEndpointId)
            }
        }

        nearbyManager.onPeerDiscovered = { endpointId, deviceName ->
            scope.launch {
                database.peerDao().upsertPeer(
                    PeerEntity(
                        endpointId = endpointId,
                        deviceName = deviceName,
                        isConnected = false
                    )
                )
            }
        }

        nearbyManager.onPeerDisconnected = { endpointId ->
            scope.launch {
                database.peerDao().updateConnectionState(endpointId, false)
            }
        }
    }

    /**
     * Creates and broadcasts an SOS request from this citizen device.
     */
    suspend fun createAndBroadcastSos(sos: SosEntity): MeshMessageEnvelope {
        // 1. Generate envelope
        val messageId = "msg_" + UUID.randomUUID().toString()
        val payload = MeshPayload(
            category = sos.category,
            severity = sos.severity,
            affectedCount = sos.affectedCount,
            description = sos.description,
            latitude = sos.latitude,
            longitude = sos.longitude,
            locationAccuracy = sos.locationAccuracy,
            locationAddress = sos.locationAddress
        )

        val rawContentForChecksum = "${sos.requestId}:${sos.category}:${sos.severity}:${sos.affectedCount}:${sos.description}"
        val checksum = MeshMessageEnvelope.calculateChecksum(rawContentForChecksum)

        val envelope = MeshMessageEnvelope(
            messageId = messageId,
            requestId = sos.requestId,
            originDeviceId = deviceId,
            messageType = "EMERGENCY_SOS",
            protocolVersion = SUPPORTED_PROTOCOL_VERSION,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 3600 * 1000L),
            hopCount = 1,
            maxHops = MAX_ALLOWED_HOPS,
            payload = payload,
            integrity = MeshIntegrity(checksum = checksum)
        )

        val json = envelope.toJson()

        // 2. Persist to Room local database first (Offline-first guarantee)
        val relayRecord = RelayMessageEntity(
            messageId = messageId,
            requestId = sos.requestId,
            originDeviceId = deviceId,
            hopCount = 1,
            maxHops = MAX_ALLOWED_HOPS,
            expiresAt = envelope.expiresAt,
            rawJsonEnvelope = json,
            status = "PENDING_FORWARD"
        )
        database.relayMessageDao().insertMessage(relayRecord)

        // 3. Update SOS delivery status to RELAY_PENDING
        database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.RELAY_PENDING.name)

        // 4. Broadcast to any connected nearby peers
        val bytes = json.toByteArray(Charsets.UTF_8)
        val peerCount = nearbyManager.broadcastPayload(bytes)
        if (peerCount > 0) {
            database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.RELAYED_TO_PEER.name)
            database.relayMessageDao().updateStatus(messageId, "FORWARDED")
            Log.i(TAG, "Emergency ${sos.requestId} broadcast to $peerCount connected peer(s)")
        } else {
            Log.i(TAG, "No peers currently connected. Message ${sos.requestId} safely queued in Room.")
        }

        return envelope
    }

    /**
     * Ingests, validates, deduplicates, and store-and-forwards inbound payloads from peers.
     */
    suspend fun handleInboundPayload(bytes: ByteArray, sourceEndpointId: String): Boolean {
        // 1. Size guard
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            Log.w(TAG, "Rejected oversized mesh payload (${bytes.size} bytes) from $sourceEndpointId")
            return false
        }

        // 2. Parse envelope
        val json = String(bytes, Charsets.UTF_8)
        val envelope: MeshMessageEnvelope = try {
            MeshMessageEnvelope.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Malformed mesh envelope JSON from $sourceEndpointId", e)
            return false
        }

        // 3. Protocol Version check
        if (envelope.protocolVersion != SUPPORTED_PROTOCOL_VERSION) {
            Log.w(TAG, "Incompatible protocol version ${envelope.protocolVersion} (expected $SUPPORTED_PROTOCOL_VERSION)")
            return false
        }

        // 4. Expiration check
        if (envelope.isExpired()) {
            Log.w(TAG, "Message ${envelope.messageId} is expired. Dropping.")
            return false
        }

        // 5. Hop limit check
        if (envelope.hasExceededHops()) {
            Log.w(TAG, "Message ${envelope.messageId} reached maximum hops (${envelope.hopCount}/${envelope.maxHops}). Dropping to prevent broadcast storms.")
            return false
        }

        // 6. Deduplication Check (Loop & Replay prevention)
        val alreadySeen = database.relayMessageDao().hasMessage(envelope.messageId)
        if (alreadySeen > 0) {
            Log.d(TAG, "Duplicate message ${envelope.messageId} already recorded. Dropping transmission.")
            return false
        }

        // 7. Verify Integrity Hash
        val payload = envelope.payload
        val rawContentForChecksum = "${envelope.requestId}:${payload.category}:${payload.severity}:${payload.affectedCount}:${payload.description}"
        val expectedChecksum = MeshMessageEnvelope.calculateChecksum(rawContentForChecksum)
        if (envelope.integrity.checksum != expectedChecksum) {
            Log.w(TAG, "Checksum mismatch for message ${envelope.messageId}. Possible corruption or tampering.")
            return false
        }

        // 8. Persist to local Room database
        val relayRecord = RelayMessageEntity(
            messageId = envelope.messageId,
            requestId = envelope.requestId,
            originDeviceId = envelope.originDeviceId,
            hopCount = envelope.hopCount,
            maxHops = envelope.maxHops,
            expiresAt = envelope.expiresAt,
            rawJsonEnvelope = json,
            status = "PENDING_FORWARD"
        )
        database.relayMessageDao().insertMessage(relayRecord)

        // 9. If we also have this request in our local SOS table, update its state
        val existingSos = database.sosDao().getSosById(envelope.requestId)
        if (existingSos != null) {
            database.sosDao().updateDeliveryState(envelope.requestId, DeliveryState.RELAYED_TO_PEER.name)
        }

        // 10. Forward to connected peers (Store-and-forward hopping: Phone A -> Phone B -> Phone C)
        // Increment hop counter
        val nextHopEnvelope = envelope.nextHopEnvelope()
        val nextHopBytes = nextHopEnvelope.toJson().toByteArray(Charsets.UTF_8)

        val connectedPeers = nearbyManager.connectedEndpoints.value
        var forwardedCount = 0
        for (peer in connectedPeers) {
            if (peer != sourceEndpointId) { // Never bounce back to the sender
                nearbyManager.sendPayload(peer, nextHopBytes)
                forwardedCount++
            }
        }

        if (forwardedCount > 0) {
            database.relayMessageDao().updateStatus(envelope.messageId, "FORWARDED")
            Log.i(TAG, "Forwarded message ${envelope.messageId} (hop ${nextHopEnvelope.hopCount}) to $forwardedCount peer(s)")
        } else {
            Log.d(TAG, "Message ${envelope.messageId} buffered in Room pending new peer encounter.")
        }

        return true
    }

    /**
     * Retries forwarding queued offline messages when new peers connect.
     */
    suspend fun flushPendingOutbox() {
        val pending = database.relayMessageDao().getPendingForwardMessages()
        if (pending.isEmpty()) return

        for (msg in pending) {
            val bytes = msg.rawJsonEnvelope.toByteArray(Charsets.UTF_8)
            val sent = nearbyManager.broadcastPayload(bytes)
            if (sent > 0) {
                database.relayMessageDao().updateStatus(msg.messageId, "FORWARDED")
            }
        }
    }
}

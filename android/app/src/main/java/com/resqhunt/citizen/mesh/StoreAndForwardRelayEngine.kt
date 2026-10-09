package com.resqhunt.citizen.mesh

import android.content.Context
import android.util.Log
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.PeerEntity
import com.resqhunt.citizen.data.local.entity.RelayMessageEntity
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.MeshIntegrity
import com.resqhunt.citizen.domain.model.MeshMessageEnvelope
import com.resqhunt.citizen.domain.model.MeshPayload
import com.resqhunt.citizen.domain.model.SeverityLevel
import com.resqhunt.citizen.domain.priority.DeterministicPriorityEngine
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
    val deviceId: String get() = nearbyManager.localDeviceId

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

        nearbyManager.onPeerConnected = { endpointId ->
            scope.launch {
                Log.i(TAG, "Peer connected: $endpointId. Updating database and flushing eligible pending messages...")
                database.peerDao().updateConnectionState(endpointId, true)
                flushPendingOutboxToPeer(endpointId)
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

        // 3. Update SOS delivery status to RELAY_PENDING initially (Searching)
        database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.RELAY_PENDING.name)

        // 4. Broadcast to any connected nearby peers
        val connectedPeers = nearbyManager.connectedEndpoints.value
        if (connectedPeers.isNotEmpty()) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            val peerCount = nearbyManager.broadcastPayload(bytes)
            if (peerCount > 0) {
                // Mark transfer in progress; DO NOT mark RELAYED_TO_PEER until ACK received
                database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)
                database.relayMessageDao().updateStatus(messageId, "SENDING")
                Log.i(TAG, "Emergency ${sos.requestId} dispatched to $peerCount peer(s), awaiting ACK")
            }
        } else {
            Log.i(TAG, "No peers currently connected. Message ${sos.requestId} buffered in Room pending peer discovery.")
        }

        return envelope
    }

    /**
     * Ingests, validates, deduplicates, persists, and store-and-forwards inbound payloads from peers.
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

        // Handle Application-Level ACK
        if (envelope.messageType == "SOS_ACK") {
            val ackedMessageId = envelope.ackForMessageId
            if (ackedMessageId != null) {
                Log.i(TAG, "Received SOS_ACK for message $ackedMessageId (request ${envelope.requestId}) from endpoint $sourceEndpointId (node ${envelope.originDeviceId})")
                database.relayMessageDao().updateStatus(ackedMessageId, "ACKNOWLEDGED")
                database.sosDao().updateDeliveryState(envelope.requestId, DeliveryState.RELAYED_TO_PEER.name)
                return true
            }
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

        val payload = envelope.payload
        if (payload == null) {
            Log.w(TAG, "Received EMERGENCY_SOS message ${envelope.messageId} with missing payload. Dropping.")
            return false
        }

        // 6. Verify Integrity Hash
        val rawContentForChecksum = "${envelope.requestId}:${payload.category}:${payload.severity}:${payload.affectedCount}:${payload.description}"
        val expectedChecksum = MeshMessageEnvelope.calculateChecksum(rawContentForChecksum)
        if (envelope.integrity.checksum != expectedChecksum) {
            Log.w(TAG, "Checksum mismatch for message ${envelope.messageId}. Possible corruption or tampering.")
            return false
        }

        // 7. Deduplication Check (Loop & Replay prevention)
        val alreadySeen = database.relayMessageDao().hasMessage(envelope.messageId)
        if (alreadySeen > 0) {
            Log.d(TAG, "Duplicate message ${envelope.messageId} already recorded. Re-sending ACK to $sourceEndpointId in case prior ACK was lost.")
            sendAck(envelope.messageId, envelope.requestId, sourceEndpointId)
            return true
        }

        // 8. Persist to local Room database: Relay record
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

        // 9. Persist to local Room database: SOS record so receiver's UI and local database displays the emergency!
        val categoryEnum = try { EmergencyCategory.valueOf(payload.category) } catch (e: Exception) { EmergencyCategory.OTHER }
        val severityEnum = try { SeverityLevel.valueOf(payload.severity) } catch (e: Exception) { SeverityLevel.MEDIUM }
        val evaluation = DeterministicPriorityEngine.evaluate(
            category = categoryEnum,
            severity = severityEnum,
            affectedCount = payload.affectedCount,
            createdAtTimestampMs = envelope.createdAt
        )

        val existingSos = database.sosDao().getSosById(envelope.requestId)
        if (existingSos == null) {
            val receivedSos = SosEntity(
                requestId = envelope.requestId,
                category = payload.category,
                severity = payload.severity,
                affectedCount = payload.affectedCount,
                description = payload.description,
                latitude = payload.latitude,
                longitude = payload.longitude,
                locationAccuracy = payload.locationAccuracy,
                locationAddress = payload.locationAddress,
                deliveryState = DeliveryState.RECEIVED_BY_PEER.name,
                priorityScore = evaluation.priorityScore,
                priorityCategory = evaluation.priorityCategory.name,
                createdAt = envelope.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            database.sosDao().insertSos(receivedSos)
            Log.i(TAG, "Saved incoming mesh emergency ${envelope.requestId} to Room database (state: RECEIVED_BY_PEER)")
        } else {
            // Already present, ensure state reflects that it was relayed
            if (existingSos.deliveryState == DeliveryState.RELAY_PENDING.name ||
                existingSos.deliveryState == DeliveryState.TRANSFER_IN_PROGRESS.name) {
                database.sosDao().updateDeliveryState(envelope.requestId, DeliveryState.RELAYED_TO_PEER.name)
            }
        }

        // 10. Send Application-Level ACK back to the sender
        sendAck(envelope.messageId, envelope.requestId, sourceEndpointId)

        // 11. Multi-hop Store-and-Forward: Forward to other connected peers (excluding the sender)
        if (envelope.hopCount < envelope.maxHops) {
            val nextHopEnvelope = envelope.nextHopEnvelope()
            val nextHopBytes = nextHopEnvelope.toJson().toByteArray(Charsets.UTF_8)
            val connectedPeers = nearbyManager.connectedEndpoints.value

            var forwardedCount = 0
            for (peer in connectedPeers) {
                if (peer != sourceEndpointId) {
                    nearbyManager.sendPayload(peer, nextHopBytes)
                    forwardedCount++
                }
            }

            if (forwardedCount > 0) {
                database.relayMessageDao().updateStatus(envelope.messageId, "FORWARDED")
                Log.i(TAG, "Multi-hop forwarded message ${envelope.messageId} (hop ${nextHopEnvelope.hopCount}) to $forwardedCount peer(s)")
            } else {
                Log.d(TAG, "Message ${envelope.messageId} queued in Room for future peer encounters.")
            }
        }

        return true
    }

    private fun sendAck(originalMessageId: String, requestId: String, targetEndpointId: String) {
        val ackEnvelope = MeshMessageEnvelope.createAck(
            ackForMessageId = originalMessageId,
            requestId = requestId,
            receiverDeviceId = deviceId
        )
        val ackBytes = ackEnvelope.toJson().toByteArray(Charsets.UTF_8)
        nearbyManager.sendPayload(
            targetEndpointId,
            ackBytes,
            onSuccess = {
                Log.d(TAG, "Sent application ACK for $originalMessageId to endpoint $targetEndpointId")
            },
            onFailure = { e ->
                Log.w(TAG, "Failed to send application ACK to endpoint $targetEndpointId: ${e.message}")
            }
        )
    }

    /**
     * Retries forwarding queued offline messages when a specific peer connects.
     */
    suspend fun flushPendingOutboxToPeer(endpointId: String) {
        val pending = database.relayMessageDao().getEligibleForwardMessages()
        if (pending.isEmpty()) return

        Log.i(TAG, "Flushing ${pending.size} eligible queued messages to newly connected peer $endpointId")
        for (msg in pending) {
            val bytes = msg.rawJsonEnvelope.toByteArray(Charsets.UTF_8)
            nearbyManager.sendPayload(
                endpointId,
                bytes,
                onSuccess = {
                    scope.launch {
                        database.relayMessageDao().updateStatus(msg.messageId, "SENDING")
                        database.sosDao().updateDeliveryState(msg.requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)
                    }
                },
                onFailure = {
                    Log.w(TAG, "Failed flushing message ${msg.messageId} to $endpointId")
                }
            )
        }
    }

    /**
     * Retries forwarding queued offline messages across all currently connected peers.
     */
    suspend fun flushPendingOutbox() {
        val connected = nearbyManager.connectedEndpoints.value
        if (connected.isEmpty()) {
            Log.d(TAG, "Cannot flush outbox: No connected peers.")
            return
        }
        for (peer in connected) {
            flushPendingOutboxToPeer(peer)
        }
    }
}

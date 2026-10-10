package com.resqhunt.citizen.mesh

import android.content.Context
import android.util.Log
import com.resqhunt.citizen.alert.EmergencyAlertManager
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
import com.resqhunt.citizen.location.EmergencyLocationManager
import com.resqhunt.citizen.location.LocationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
        const val ACK_TIMEOUT_MS = 14000L // 14 seconds ACK timeout per attempt
        const val MAX_SEND_ATTEMPTS = 3
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    val deviceId: String get() = nearbyManager.localDeviceId

    data class OutgoingSosSession(
        val messageId: String,
        val requestId: String,
        val rawEnvelopeJson: String,
        var attempt: Int = 1,
        var timeoutJob: Job? = null
    )

    private val activeOutgoingSessions = ConcurrentHashMap<String, OutgoingSosSession>()

    init {
        // Wire nearby connection callbacks
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
                Log.i(TAG, "Peer disconnected: $endpointId. Updating database...")
                database.peerDao().updateConnectionState(endpointId, false)
            }
        }
    }

    /**
     * Checks if a delivery state is terminal or already confirmed by peer/server.
     * Prevents race conditions from downgrading an acknowledged state.
     */
    fun isTerminalOrRelayed(state: String): Boolean {
        return state in listOf(
            DeliveryState.RELAYED_TO_PEER.name,
            DeliveryState.RECEIVED_BY_PEER.name,
            DeliveryState.SERVER_RECEIVED.name,
            DeliveryState.COORDINATOR_ACKNOWLEDGED.name,
            DeliveryState.ASSIGNED.name,
            DeliveryState.IN_PROGRESS.name,
            DeliveryState.RESOLVED.name
        )
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
            locationAddress = sos.locationAddress,
            locationTimestamp = sos.locationTimestamp,
            locationSource = sos.locationSource
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

        val connectedPeers = nearbyManager.connectedEndpoints.value
        val forwardedStr = connectedPeers.joinToString(",")

        // 2. Persist to Room local database first (Offline-first guarantee)
        val relayRecord = RelayMessageEntity(
            messageId = messageId,
            requestId = sos.requestId,
            originDeviceId = deviceId,
            hopCount = 1,
            maxHops = MAX_ALLOWED_HOPS,
            expiresAt = envelope.expiresAt,
            rawJsonEnvelope = json,
            status = if (connectedPeers.isNotEmpty()) "SENDING" else "PENDING_FORWARD",
            lastReceivedFromEndpointId = null,
            forwardedEndpoints = forwardedStr
        )
        database.relayMessageDao().insertMessage(relayRecord)

        // 3. Update SOS delivery status to RELAY_PENDING initially
        database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.RELAY_PENDING.name)

        // 4. Setup outgoing session with ACK timeout
        val session = OutgoingSosSession(
            messageId = messageId,
            requestId = sos.requestId,
            rawEnvelopeJson = json,
            attempt = 1
        )
        activeOutgoingSessions[messageId] = session

        // 5. Broadcast to connected peers if any
        if (connectedPeers.isNotEmpty()) {
            val bytes = json.toByteArray(Charsets.UTF_8)
            database.sosDao().updateDeliveryState(sos.requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)

            startAckTimeout(session)

            for (peer in connectedPeers) {
                nearbyManager.sendTrackedPayload(
                    endpointId = peer,
                    bytes = bytes,
                    onTransferSuccess = {
                        Log.i(TAG, "Payload bytes physically transferred to $peer for message $messageId. Awaiting application ACK...")
                    },
                    onTransferFailure = { status ->
                        Log.w(TAG, "Physical byte transfer failed to peer $peer (status $status) for message $messageId")
                    }
                )
            }
            Log.i(TAG, "Emergency ${sos.requestId} dispatched to ${connectedPeers.size} peer(s), awaiting application ACK.")
        } else {
            Log.i(TAG, "No peers currently connected. Message ${sos.requestId} buffered in Room pending peer discovery.")
        }

        return envelope
    }

    /**
     * Updates an existing SOS entity with a fresh location lock acquired asynchronously,
     * and re-broadcasts the updated location to all connected mesh peers.
     */
    suspend fun updateSosLocationAndBroadcast(requestId: String, fix: LocationFix) {
        val existingSos = database.sosDao().getSosById(requestId) ?: return
        val updatedSos = existingSos.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            locationAccuracy = fix.accuracy,
            locationTimestamp = fix.timestamp,
            locationSource = fix.source,
            locationAddress = if (existingSos.locationAddress.isNullOrBlank() ||
                existingSos.locationAddress?.startsWith("Coordinates Pending") == true ||
                existingSos.locationAddress == "Location unavailable"
            ) {
                "GPS Coordinates Locked (${fix.source})"
            } else {
                existingSos.locationAddress
            },
            updatedAt = System.currentTimeMillis()
        )
        database.sosDao().updateSos(updatedSos)

        val messageId = "msg_loc_upd_" + UUID.randomUUID().toString()
        val payload = MeshPayload(
            category = updatedSos.category,
            severity = updatedSos.severity,
            affectedCount = updatedSos.affectedCount,
            description = updatedSos.description,
            latitude = updatedSos.latitude,
            longitude = updatedSos.longitude,
            locationAccuracy = updatedSos.locationAccuracy,
            locationAddress = updatedSos.locationAddress,
            locationTimestamp = updatedSos.locationTimestamp,
            locationSource = updatedSos.locationSource
        )

        val rawContentForChecksum = "${updatedSos.requestId}:${updatedSos.category}:${updatedSos.severity}:${updatedSos.affectedCount}:${updatedSos.description}"
        val checksum = MeshMessageEnvelope.calculateChecksum(rawContentForChecksum)

        val envelope = MeshMessageEnvelope(
            messageId = messageId,
            requestId = updatedSos.requestId,
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
        val bytes = json.toByteArray(Charsets.UTF_8)

        database.relayMessageDao().insertMessage(
            RelayMessageEntity(
                messageId = messageId,
                requestId = updatedSos.requestId,
                originDeviceId = deviceId,
                hopCount = 1,
                maxHops = MAX_ALLOWED_HOPS,
                expiresAt = envelope.expiresAt,
                rawJsonEnvelope = json,
                status = "PENDING_FORWARD",
                lastReceivedFromEndpointId = null,
                forwardedEndpoints = ""
            )
        )

        val connectedPeers = nearbyManager.connectedEndpoints.value
        if (connectedPeers.isNotEmpty()) {
            val session = OutgoingSosSession(
                messageId = messageId,
                requestId = updatedSos.requestId,
                rawEnvelopeJson = json,
                attempt = 1
            )
            activeOutgoingSessions[messageId] = session
            startAckTimeout(session)

            for (endpointId in connectedPeers) {
                nearbyManager.sendTrackedPayload(
                    endpointId = endpointId,
                    bytes = bytes,
                    onTransferSuccess = {
                        Log.i(TAG, "Location update envelope $messageId sent to peer $endpointId")
                    },
                    onTransferFailure = { status ->
                        Log.w(TAG, "Location update transfer to $endpointId failed ($status)")
                    }
                )
            }
        }
        Log.i(TAG, "Updated and broadcast fresh location for $requestId: (${fix.latitude}, ${fix.longitude}) ±${fix.accuracy}m")
    }

    /**
     * Manages ACK timeouts and retries for an outgoing SOS session.
     */
    private fun startAckTimeout(session: OutgoingSosSession) {
        session.timeoutJob?.cancel()
        session.timeoutJob = scope.launch {
            delay(ACK_TIMEOUT_MS)

            // Check if already acknowledged in Room
            val currentRelayMsg = database.relayMessageDao().getMessageById(session.messageId)
            if (currentRelayMsg?.status == "ACKNOWLEDGED") {
                Log.d(TAG, "Message ${session.messageId} already ACKNOWLEDGED. Cancelling timeout.")
                activeOutgoingSessions.remove(session.messageId)
                return@launch
            }

            val currentSos = database.sosDao().getSosById(session.requestId)
            if (currentSos != null && isTerminalOrRelayed(currentSos.deliveryState)) {
                Log.d(TAG, "Request ${session.requestId} is in state ${currentSos.deliveryState}. No retry needed.")
                activeOutgoingSessions.remove(session.messageId)
                return@launch
            }

            Log.w(TAG, "ACK timeout expired for message ${session.messageId} (attempt ${session.attempt}/$MAX_SEND_ATTEMPTS)")
            val connectedPeers = nearbyManager.connectedEndpoints.value

            if (session.attempt < MAX_SEND_ATTEMPTS && connectedPeers.isNotEmpty()) {
                session.attempt += 1
                Log.i(TAG, "Retrying SOS broadcast for ${session.requestId} (attempt ${session.attempt})...")
                database.sosDao().updateDeliveryState(session.requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)
                val bytes = session.rawEnvelopeJson.toByteArray(Charsets.UTF_8)
                startAckTimeout(session)

                for (peer in connectedPeers) {
                    nearbyManager.sendTrackedPayload(peer, bytes)
                }
            } else {
                Log.w(TAG, "ACK not received for message ${session.messageId}. Moving to RELAY_FAILED while keeping buffered.")
                if (currentSos != null && !isTerminalOrRelayed(currentSos.deliveryState)) {
                    database.sosDao().updateDeliveryState(session.requestId, DeliveryState.RELAY_FAILED.name)
                    database.relayMessageDao().updateStatus(session.messageId, "PENDING_FORWARD")
                }
                activeOutgoingSessions.remove(session.messageId)
            }
        }
    }

    /**
     * Ingests, validates, deduplicates, persists, sends ACK, and store-and-forwards inbound payloads.
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

        // 4. Handle Application-Level ACK
        if (envelope.messageType == "SOS_ACK") {
            val ackedMessageId = envelope.ackForMessageId
            if (ackedMessageId != null) {
                Log.i(TAG, "Received SOS_ACK for message $ackedMessageId (request ${envelope.requestId}) from endpoint $sourceEndpointId (node ${envelope.originDeviceId})")

                // Match against local message database
                val localMsg = database.relayMessageDao().getMessageById(ackedMessageId)
                if (localMsg != null) {
                    // Cancel active timeout job
                    val session = activeOutgoingSessions.remove(ackedMessageId)
                    session?.timeoutJob?.cancel()

                    database.relayMessageDao().updateStatus(ackedMessageId, "ACKNOWLEDGED")
                    database.sosDao().updateDeliveryState(envelope.requestId, DeliveryState.RELAYED_TO_PEER.name)
                    Log.i(TAG, "Successfully matched ACK for message $ackedMessageId! Updated request ${envelope.requestId} to RELAYED_TO_PEER.")
                    return true
                } else {
                    Log.w(TAG, "Received ACK for unknown or untracked messageId: $ackedMessageId. Dropping.")
                    return false
                }
            }
            return false
        }

        // 5. Expiration check
        if (envelope.isExpired()) {
            Log.w(TAG, "Message ${envelope.messageId} is expired. Dropping.")
            return false
        }

        // 6. Hop limit check
        if (envelope.hasExceededHops()) {
            Log.w(TAG, "Message ${envelope.messageId} reached maximum hops (${envelope.hopCount}/${envelope.maxHops}). Dropping to prevent broadcast storms.")
            return false
        }

        val payload = envelope.payload
        if (payload == null) {
            Log.w(TAG, "Received EMERGENCY_SOS message ${envelope.messageId} with missing payload. Dropping.")
            return false
        }

        // 7. Verify Integrity Hash
        val rawContentForChecksum = "${envelope.requestId}:${payload.category}:${payload.severity}:${payload.affectedCount}:${payload.description}"
        val expectedChecksum = MeshMessageEnvelope.calculateChecksum(rawContentForChecksum)
        if (envelope.integrity.checksum != expectedChecksum) {
            Log.w(TAG, "Checksum mismatch for message ${envelope.messageId}. Possible corruption or tampering.")
            return false
        }

        // 8. Deduplication Check (Loop & Replay prevention)
        val alreadySeen = database.relayMessageDao().hasMessage(envelope.messageId)
        if (alreadySeen > 0) {
            Log.d(TAG, "Duplicate message ${envelope.messageId} already recorded. Re-sending ACK to $sourceEndpointId without duplicate storage or alert.")
            sendAck(envelope.messageId, envelope.requestId, sourceEndpointId)
            return true
        }

        // 9. Multi-hop Envelope Preparation: Increment hop count for subsequent relaying
        val nextHopEnvelope = envelope.nextHopEnvelope()
        val nextHopJson = nextHopEnvelope.toJson()
        val nextHopBytes = nextHopJson.toByteArray(Charsets.UTF_8)

        // 10. Persist to local Room database: Relay record with next-hop envelope and previous-hop source
        val relayRecord = RelayMessageEntity(
            messageId = envelope.messageId,
            requestId = envelope.requestId,
            originDeviceId = envelope.originDeviceId,
            hopCount = nextHopEnvelope.hopCount,
            maxHops = envelope.maxHops,
            expiresAt = envelope.expiresAt,
            rawJsonEnvelope = nextHopJson,
            status = "PENDING_FORWARD",
            lastReceivedFromEndpointId = sourceEndpointId,
            forwardedEndpoints = ""
        )
        database.relayMessageDao().insertMessage(relayRecord)

        // 11. Persist to local Room database: SOS record so receiver's UI displays the emergency
        val categoryEnum = try { EmergencyCategory.valueOf(payload.category) } catch (e: Exception) { EmergencyCategory.OTHER }
        val severityEnum = try { SeverityLevel.valueOf(payload.severity) } catch (e: Exception) { SeverityLevel.MEDIUM }
        val evaluation = DeterministicPriorityEngine.evaluate(
            category = categoryEnum,
            severity = severityEnum,
            affectedCount = payload.affectedCount,
            createdAtTimestampMs = envelope.createdAt
        )

        val existingSos = database.sosDao().getSosById(envelope.requestId)
        val sosEntityToAlert: SosEntity = if (existingSos == null) {
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
                locationTimestamp = payload.locationTimestamp ?: envelope.createdAt,
                locationSource = payload.locationSource ?: if (payload.latitude != null) "LAST_KNOWN" else "UNAVAILABLE",
                deliveryState = DeliveryState.RECEIVED_BY_PEER.name,
                priorityScore = evaluation.priorityScore,
                priorityCategory = evaluation.priorityCategory.name,
                createdAt = envelope.createdAt,
                updatedAt = System.currentTimeMillis()
            )
            database.sosDao().insertSos(receivedSos)
            Log.i(TAG, "Saved incoming mesh emergency ${envelope.requestId} to Room database (state: RECEIVED_BY_PEER)")
            receivedSos
        } else {
            // Check if incoming payload has a newer valid location from the original sender
            val incomingTimestamp = payload.locationTimestamp ?: envelope.createdAt
            val existingTimestamp = existingSos.locationTimestamp ?: 0L
            val hasValidIncomingCoords = EmergencyLocationManager.isValidCoordinates(payload.latitude, payload.longitude)
            val isNewerLocation = hasValidIncomingCoords && (
                existingSos.latitude == null ||
                (incomingTimestamp >= existingTimestamp && payload.locationSource != "UNAVAILABLE")
            )

            if (isNewerLocation) {
                val updatedSos = existingSos.copy(
                    latitude = payload.latitude,
                    longitude = payload.longitude,
                    locationAccuracy = payload.locationAccuracy,
                    locationAddress = payload.locationAddress ?: existingSos.locationAddress,
                    locationTimestamp = incomingTimestamp,
                    locationSource = payload.locationSource ?: "FRESH_GPS",
                    updatedAt = System.currentTimeMillis()
                )
                database.sosDao().updateSos(updatedSos)
                Log.i(TAG, "Updated existing incident ${envelope.requestId} with newer valid location from original sender: ${payload.latitude}, ${payload.longitude} (±${payload.locationAccuracy}m, time=$incomingTimestamp, source=${payload.locationSource})")
            }

            if (!isTerminalOrRelayed(existingSos.deliveryState)) {
                database.sosDao().updateDeliveryState(envelope.requestId, DeliveryState.RELAYED_TO_PEER.name)
            }
            existingSos
        }

        // 12. Send Application-Level ACK back to the immediate sender ONLY AFTER database persistence
        sendAck(envelope.messageId, envelope.requestId, sourceEndpointId)

        // 13. Trigger Emergency Audio & Vibration Alert on Receiver
        scope.launch(Dispatchers.Main) {
            EmergencyAlertManager.triggerSosAlert(context, sosEntityToAlert, envelope.originDeviceId)
        }

        // 14. Multi-hop Store-and-Forward: Forward nextHopEnvelope to other connected peers (excluding sourceEndpointId)
        if (nextHopEnvelope.hopCount <= nextHopEnvelope.maxHops) {
            val connectedPeers = nearbyManager.connectedEndpoints.value
            val eligiblePeers = connectedPeers.filter { it != sourceEndpointId }

            if (eligiblePeers.isNotEmpty()) {
                val session = OutgoingSosSession(
                    messageId = envelope.messageId,
                    requestId = envelope.requestId,
                    rawEnvelopeJson = nextHopJson,
                    attempt = 1
                )
                activeOutgoingSessions[envelope.messageId] = session
                startAckTimeout(session)

                val forwardedPeersList = mutableListOf<String>()
                for (peer in eligiblePeers) {
                    nearbyManager.sendTrackedPayload(
                        endpointId = peer,
                        bytes = nextHopBytes,
                        onTransferSuccess = {
                            Log.i(TAG, "Multi-hop envelope ${envelope.messageId} (hop ${nextHopEnvelope.hopCount}) transferred to peer $peer")
                        },
                        onTransferFailure = { status ->
                            Log.w(TAG, "Multi-hop transfer to peer $peer failed (status $status) for message ${envelope.messageId}")
                        }
                    )
                    forwardedPeersList.add(peer)
                }

                val forwardedStr = forwardedPeersList.joinToString(",")
                database.relayMessageDao().updateStatusAndEndpoints(envelope.messageId, "FORWARDED", forwardedStr)
                Log.i(TAG, "Multi-hop forwarded message ${envelope.messageId} (hop ${nextHopEnvelope.hopCount}/${nextHopEnvelope.maxHops}) to ${eligiblePeers.size} peer(s), excluding sender $sourceEndpointId")
            } else {
                Log.i(TAG, "No other connected peers currently available. Message ${envelope.messageId} buffered in Room outbox (hop ${nextHopEnvelope.hopCount}) for future peer discovery.")
            }
        } else {
            Log.i(TAG, "Message ${envelope.messageId} reached maximum hops (${nextHopEnvelope.hopCount}/${nextHopEnvelope.maxHops}). Will not forward further.")
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
        nearbyManager.sendTrackedPayload(
            endpointId = targetEndpointId,
            bytes = ackBytes,
            onEnqueued = {
                Log.d(TAG, "Enqueued application ACK for $originalMessageId to endpoint $targetEndpointId")
            },
            onTransferSuccess = {
                Log.i(TAG, "Application ACK for $originalMessageId physically delivered to endpoint $targetEndpointId")
            },
            onTransferFailure = { status ->
                Log.w(TAG, "Physical transfer of application ACK for $originalMessageId to endpoint $targetEndpointId failed (status $status)")
            }
        )
    }

    /**
     * Retries forwarding queued offline messages when a specific peer connects.
     * Excludes the peer if it was the previous-hop sender or if this message was already sent to it.
     */
    suspend fun flushPendingOutboxToPeer(endpointId: String) {
        val pending = database.relayMessageDao().getEligibleForwardMessages()
        if (pending.isEmpty()) return

        val now = System.currentTimeMillis()
        for (msg in pending) {
            // Exclude previous-hop sender (prevent echo loops)
            if (msg.lastReceivedFromEndpointId == endpointId) {
                Log.d(TAG, "Skipping flush of message ${msg.messageId} to $endpointId: Endpoint was the previous-hop sender.")
                continue
            }

            // Exclude peers that have already received this message
            val forwardedSet = msg.forwardedEndpoints.split(",").filter { it.isNotBlank() }.toSet()
            if (forwardedSet.contains(endpointId)) {
                Log.d(TAG, "Skipping flush of message ${msg.messageId} to $endpointId: Already forwarded to this peer.")
                continue
            }

            // Check hop limit and expiry
            if (msg.hopCount >= msg.maxHops || msg.expiresAt <= now) {
                continue
            }

            Log.i(TAG, "Flushing queued message ${msg.messageId} (hop ${msg.hopCount}/${msg.maxHops}) to newly connected peer $endpointId")

            val currentSos = database.sosDao().getSosById(msg.requestId)
            val shouldUpdateSos = currentSos != null && !isTerminalOrRelayed(currentSos.deliveryState)

            if (shouldUpdateSos) {
                database.sosDao().updateDeliveryState(msg.requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)
            }

            val session = OutgoingSosSession(msg.messageId, msg.requestId, msg.rawJsonEnvelope, 1)
            startAckTimeout(session)
            activeOutgoingSessions[msg.messageId] = session

            val bytes = msg.rawJsonEnvelope.toByteArray(Charsets.UTF_8)
            nearbyManager.sendTrackedPayload(
                endpointId = endpointId,
                bytes = bytes,
                onTransferSuccess = {
                    Log.d(TAG, "Flushed message ${msg.messageId} bytes physically delivered to $endpointId; awaiting ACK")
                },
                onTransferFailure = { status ->
                    Log.w(TAG, "Failed flushing message ${msg.messageId} to $endpointId (status $status)")
                }
            )

            val updatedForwarded = (forwardedSet + endpointId).joinToString(",")
            database.relayMessageDao().updateStatusAndEndpoints(msg.messageId, "FORWARDED", updatedForwarded)
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

    /**
     * Explicit retry action callable from UI if an SOS transfer timed out or failed.
     */
    suspend fun retrySosTransmission(requestId: String): Boolean {
        val sos = database.sosDao().getSosById(requestId) ?: return false
        val messages = database.relayMessageDao().getMessagesForRequest(requestId)
        val msg = messages.firstOrNull()

        val connectedPeers = nearbyManager.connectedEndpoints.value
        if (connectedPeers.isEmpty()) {
            database.sosDao().updateDeliveryState(requestId, DeliveryState.RELAY_PENDING.name)
            Log.i(TAG, "No peers connected for retry. Reverted $requestId to RELAY_PENDING.")
            return false
        }

        if (msg != null) {
            val bytes = msg.rawJsonEnvelope.toByteArray(Charsets.UTF_8)
            database.sosDao().updateDeliveryState(requestId, DeliveryState.TRANSFER_IN_PROGRESS.name)
            database.relayMessageDao().updateStatus(msg.messageId, "SENDING")

            val session = OutgoingSosSession(msg.messageId, requestId, msg.rawJsonEnvelope, 1)
            startAckTimeout(session)
            activeOutgoingSessions[msg.messageId] = session

            for (peer in connectedPeers) {
                nearbyManager.sendTrackedPayload(peer, bytes)
            }
            return true
        } else {
            createAndBroadcastSos(sos)
            return true
        }
    }
}

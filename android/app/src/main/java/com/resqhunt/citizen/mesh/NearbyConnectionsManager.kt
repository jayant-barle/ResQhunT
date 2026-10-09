package com.resqhunt.citizen.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyConnectionsManager(private val context: Context) {

    companion object {
        private const val TAG = "ResQhunT_Nearby"
        const val SERVICE_ID = "com.resqhunt.mesh.sos"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    var onPayloadReceived: ((ByteArray, String) -> Unit)? = null
    var onPeerDiscovered: ((String, String) -> Unit)? = null
    var onPeerDisconnected: ((String) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                if (bytes != null) {
                    Log.d(TAG, "Received payload from $endpointId, size: ${bytes.size} bytes")
                    onPayloadReceived?.invoke(bytes, endpointId)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer status updates
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by $endpointId (${connectionInfo.endpointName}). Auto-accepting...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Connected successfully to peer: $endpointId")
                _connectedEndpoints.value = _connectedEndpoints.value + endpointId
            } else {
                Log.w(TAG, "Connection rejected or failed to $endpointId: ${resolution.status.statusMessage}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Peer disconnected: $endpointId")
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            onPeerDisconnected?.invoke(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Peer discovered: $endpointId (${info.endpointName}). Requesting connection...")
            onPeerDiscovered?.invoke(endpointId, info.endpointName)
            // Initiate automatic cluster connection
            connectionsClient.requestConnection(
                android.os.Build.MODEL,
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.e(TAG, "Request connection failed for $endpointId", e)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Peer lost: $endpointId")
            onPeerDisconnected?.invoke(endpointId)
        }
    }

    fun startAdvertising(deviceName: String = android.os.Build.MODEL) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(deviceName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Advertising started successfully as $deviceName")
                _isAdvertising.value = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Advertising failed to start", e)
                _isAdvertising.value = false
            }
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Discovery started successfully for $SERVICE_ID")
                _isDiscovering.value = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed to start", e)
                _isDiscovering.value = false
            }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
    }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptySet()
        stopAdvertising()
        stopDiscovery()
    }

    fun sendPayload(endpointId: String, bytes: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload to $endpointId", e)
            }
    }

    fun broadcastPayload(bytes: ByteArray): Int {
        val targets = _connectedEndpoints.value.toList()
        if (targets.isEmpty()) return 0

        connectionsClient.sendPayload(targets, Payload.fromBytes(bytes))
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to broadcast payload to peers", e)
            }
        return targets.size
    }
}

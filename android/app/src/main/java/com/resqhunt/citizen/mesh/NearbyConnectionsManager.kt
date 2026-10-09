package com.resqhunt.citizen.mesh

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
import java.util.Collections
import java.util.UUID

class NearbyConnectionsManager(private val context: Context) {

    companion object {
        private const val TAG = "ResQhunT_Nearby"
        const val SERVICE_ID = "com.resqhunt.mesh.sos"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
        private const val PREFS_NAME = "resqhunt_mesh_prefs"
        private const val KEY_DEVICE_ID = "mesh_local_device_id"
    }

    val localDeviceId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) {
            existing
        } else {
            val generated = "dev_" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            generated
        }
    }

    val localDeviceName: String by lazy {
        "${Build.MODEL}_${localDeviceId.takeLast(4)}"
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private val _discoveredEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredEndpoints: StateFlow<Map<String, String>> = _discoveredEndpoints.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val connectingEndpoints = Collections.synchronizedSet(mutableSetOf<String>())

    var onPayloadReceived: ((ByteArray, String) -> Unit)? = null
    var onPeerDiscovered: ((String, String) -> Unit)? = null
    var onPeerConnected: ((String) -> Unit)? = null
    var onPeerDisconnected: ((String) -> Unit)? = null
    var onPayloadTransferUpdate: ((String, Long, Int) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes()
                if (bytes != null) {
                    Log.d(TAG, "Inbound bytes payload from endpoint=$endpointId (${bytes.size} bytes)")
                    onPayloadReceived?.invoke(bytes, endpointId)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val status = update.status
            onPayloadTransferUpdate?.invoke(endpointId, update.payloadId, status)
            when (status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "Payload ${update.payloadId} transfer SUCCEEDED to $endpointId")
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.w(TAG, "Payload ${update.payloadId} transfer FAILED to $endpointId")
                    _lastError.value = "Transfer failed to peer $endpointId"
                }
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    Log.v(TAG, "Payload ${update.payloadId} in progress: ${update.bytesTransferred}/${update.totalBytes}")
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by $endpointId ('${connectionInfo.endpointName}'). Auto-accepting...")
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to connectionInfo.endpointName)
            onPeerDiscovered?.invoke(endpointId, connectionInfo.endpointName)

            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Accept connection failed for $endpointId", e)
                    connectingEndpoints.remove(endpointId)
                    _lastError.value = "Accept failed: ${e.message}"
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            val statusCode = resolution.status.statusCode

            if (statusCode == ConnectionsStatusCodes.STATUS_OK ||
                statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                Log.i(TAG, "Connected successfully to peer endpoint: $endpointId (Status $statusCode)")
                _connectedEndpoints.value = _connectedEndpoints.value + endpointId
                _lastError.value = null
                onPeerConnected?.invoke(endpointId)
            } else {
                val msg = resolution.status.statusMessage ?: ConnectionsStatusCodes.getStatusCodeString(statusCode)
                Log.w(TAG, "Connection rejected/failed to $endpointId: $msg (Code $statusCode)")
                _lastError.value = "Connection failed ($statusCode: $msg)"
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Peer disconnected: $endpointId")
            connectingEndpoints.remove(endpointId)
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            _discoveredEndpoints.value = _discoveredEndpoints.value - endpointId
            onPeerDisconnected?.invoke(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Peer discovered: $endpointId ('${info.endpointName}'). Checking connection status...")
            _discoveredEndpoints.value = _discoveredEndpoints.value + (endpointId to info.endpointName)
            onPeerDiscovered?.invoke(endpointId, info.endpointName)

            // Guard against duplicate connection requests
            if (_connectedEndpoints.value.contains(endpointId)) {
                Log.d(TAG, "Already connected to $endpointId. Skipping request.")
                return
            }

            if (!connectingEndpoints.add(endpointId)) {
                Log.d(TAG, "Connection request already in progress for $endpointId.")
                return
            }

            Log.d(TAG, "Requesting connection to $endpointId as '$localDeviceName'...")
            connectionsClient.requestConnection(
                localDeviceName,
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                connectingEndpoints.remove(endpointId)
                Log.e(TAG, "Request connection failed for $endpointId", e)
                _lastError.value = "Connect request failed: ${e.message}"
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Peer lost: $endpointId")
            connectingEndpoints.remove(endpointId)
            _discoveredEndpoints.value = _discoveredEndpoints.value - endpointId
            onPeerDisconnected?.invoke(endpointId)
        }
    }

    fun isGooglePlayServicesAvailable(): Boolean {
        val gApi = GoogleApiAvailability.getInstance()
        return gApi.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    fun startAdvertising() {
        if (!isGooglePlayServicesAvailable()) {
            _lastError.value = "Google Play Services is unavailable on this device."
            Log.e(TAG, "Cannot start advertising: Google Play Services unavailable.")
            return
        }

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(localDeviceName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Nearby advertising started successfully as '$localDeviceName'")
                _isAdvertising.value = true
                _lastError.value = null
            }
            .addOnFailureListener { e ->
                val errorMsg = "Nearby advertising failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _isAdvertising.value = false
                _lastError.value = errorMsg
            }
    }

    fun startDiscovery() {
        if (!isGooglePlayServicesAvailable()) {
            _lastError.value = "Google Play Services is unavailable on this device."
            Log.e(TAG, "Cannot start discovery: Google Play Services unavailable.")
            return
        }

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Nearby discovery started successfully for '$SERVICE_ID'")
                _isDiscovering.value = true
                _lastError.value = null
            }
            .addOnFailureListener { e ->
                val errorMsg = "Nearby discovery failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _isDiscovering.value = false
                _lastError.value = errorMsg
            }
    }

    fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising", e)
        }
        _isAdvertising.value = false
    }

    fun stopDiscovery() {
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        _isDiscovering.value = false
    }

    fun stopAll() {
        try {
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping all endpoints", e)
        }
        connectingEndpoints.clear()
        _connectedEndpoints.value = emptySet()
        _discoveredEndpoints.value = emptyMap()
        stopAdvertising()
        stopDiscovery()
    }

    fun sendPayload(endpointId: String, bytes: ByteArray, onSuccess: (() -> Unit)? = null, onFailure: ((Exception) -> Unit)? = null) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnSuccessListener {
                Log.d(TAG, "Payload dispatched to $endpointId")
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload to $endpointId", e)
                _lastError.value = "Failed to send payload to $endpointId: ${e.message}"
                onFailure?.invoke(e)
            }
    }

    fun broadcastPayload(bytes: ByteArray): Int {
        val targets = _connectedEndpoints.value.toList()
        if (targets.isEmpty()) return 0

        connectionsClient.sendPayload(targets, Payload.fromBytes(bytes))
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to broadcast payload to peers", e)
                _lastError.value = "Broadcast failed: ${e.message}"
            }
        return targets.size
    }
}

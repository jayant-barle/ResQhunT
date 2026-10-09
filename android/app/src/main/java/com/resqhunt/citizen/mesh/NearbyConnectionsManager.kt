package com.resqhunt.citizen.mesh

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

enum class DiscoveryState {
    STOPPED,
    STARTING,
    ACTIVE,
    STOPPING
}

enum class AdvertisingState {
    STOPPED,
    STARTING,
    ACTIVE,
    STOPPING
}

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

    private val _advertisingState = MutableStateFlow(AdvertisingState.STOPPED)
    val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()

    private val _discoveryState = MutableStateFlow(DiscoveryState.STOPPED)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var discoveryRecoveryJob: Job? = null
    private var discoveryRetryAttempts = 0

    private val connectingEndpoints = Collections.synchronizedSet(mutableSetOf<String>())

    data class PayloadTracker(
        val payloadId: Long,
        val endpointId: String,
        val onTransferSuccess: (() -> Unit)?,
        val onTransferFailure: ((Int) -> Unit)?
    )

    private val payloadTrackers = java.util.concurrent.ConcurrentHashMap<Long, PayloadTracker>()

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
            val tracker = payloadTrackers[update.payloadId]
            when (status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "Payload ${update.payloadId} transfer SUCCEEDED to $endpointId")
                    tracker?.onTransferSuccess?.invoke()
                    payloadTrackers.remove(update.payloadId)
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.w(TAG, "Payload ${update.payloadId} transfer FAILED to $endpointId")
                    _lastError.value = "Transfer failed to peer $endpointId"
                    tracker?.onTransferFailure?.invoke(status)
                    payloadTrackers.remove(update.payloadId)
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

                // Ensure cluster advertising and discovery remain active so additional peers can join the mesh
                if (_discoveryState.value != DiscoveryState.ACTIVE && _discoveryState.value != DiscoveryState.STARTING) {
                    startDiscovery()
                }
                if (_advertisingState.value != AdvertisingState.ACTIVE && _advertisingState.value != AdvertisingState.STARTING) {
                    startAdvertising()
                }
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

            // Terminate and fail any active transfers to this disconnected endpoint
            val toRemove = payloadTrackers.filterValues { it.endpointId == endpointId }
            for ((pId, tracker) in toRemove) {
                Log.w(TAG, "Pending payload $pId failed because peer $endpointId disconnected")
                tracker.onTransferFailure?.invoke(PayloadTransferUpdate.Status.FAILURE)
                payloadTrackers.remove(pId)
            }

            onPeerDisconnected?.invoke(endpointId)

            // Re-verify discovery is active to find reconnecting peers
            if (_discoveryState.value != DiscoveryState.ACTIVE && _discoveryState.value != DiscoveryState.STARTING) {
                startDiscovery()
            }
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
                val statusCode = (e as? ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT || statusCode == 8003) {
                    Log.d(TAG, "Bidirectional connection negotiation already active with $endpointId ($statusCode)")
                } else {
                    Log.e(TAG, "Request connection failed for $endpointId", e)
                    _lastError.value = "Connect request failed: ${e.message}"
                }
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

    @Synchronized
    fun startAdvertising() {
        if (!isGooglePlayServicesAvailable()) {
            _lastError.value = "Google Play Services is unavailable on this device."
            Log.e(TAG, "Cannot start advertising: Google Play Services unavailable.")
            return
        }

        val currentState = _advertisingState.value
        if (currentState == AdvertisingState.ACTIVE) {
            Log.d(TAG, "startAdvertising called but advertising is already ACTIVE. Preserving active beacon.")
            return
        }
        if (currentState == AdvertisingState.STARTING) {
            Log.d(TAG, "startAdvertising called while STARTING. No-op to avoid race condition.")
            return
        }

        Log.i(TAG, "Initiating Nearby advertising for '$localDeviceName' (current state: $currentState)...")
        _advertisingState.value = AdvertisingState.STARTING

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(localDeviceName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Nearby advertising started successfully as '$localDeviceName'")
                _advertisingState.value = AdvertisingState.ACTIVE
                _isAdvertising.value = true
                if (_lastError.value?.contains("advertising", ignoreCase = true) == true) {
                    _lastError.value = null
                }
            }
            .addOnFailureListener { e ->
                val statusCode = (e as? ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
                    e.message?.contains("8001") == true ||
                    e.message?.contains("STATUS_ALREADY_ADVERTISING", ignoreCase = true) == true
                ) {
                    Log.i(TAG, "Advertising session is already active in Nearby Connections (Code 8001: STATUS_ALREADY_ADVERTISING). Reconciling state to ACTIVE.")
                    _advertisingState.value = AdvertisingState.ACTIVE
                    _isAdvertising.value = true
                } else {
                    val errorMsg = "Nearby advertising failed: ${e.message} (Code $statusCode)"
                    Log.e(TAG, errorMsg, e)
                    _advertisingState.value = AdvertisingState.STOPPED
                    _isAdvertising.value = false
                    _lastError.value = errorMsg
                }
            }
    }

    @Synchronized
    fun startDiscovery() {
        if (!isGooglePlayServicesAvailable()) {
            _lastError.value = "Google Play Services is unavailable on this device."
            Log.e(TAG, "Cannot start discovery: Google Play Services unavailable.")
            return
        }

        val currentState = _discoveryState.value
        if (currentState == DiscoveryState.ACTIVE) {
            Log.d(TAG, "startDiscovery called but discovery is already ACTIVE. Preserving active session.")
            return
        }
        if (currentState == DiscoveryState.STARTING) {
            Log.d(TAG, "startDiscovery called while STARTING. No-op to avoid race condition.")
            return
        }

        discoveryRecoveryJob?.cancel()
        discoveryRecoveryJob = null

        Log.i(TAG, "Initiating Nearby discovery for '$SERVICE_ID' (current state: $currentState)...")
        _discoveryState.value = DiscoveryState.STARTING

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Nearby discovery started successfully for '$SERVICE_ID'")
                _discoveryState.value = DiscoveryState.ACTIVE
                _isDiscovering.value = true
                discoveryRetryAttempts = 0
                if (_lastError.value?.contains("discovery", ignoreCase = true) == true) {
                    _lastError.value = null
                }
            }
            .addOnFailureListener { e ->
                val statusCode = (e as? ApiException)?.statusCode ?: -1
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING ||
                    e.message?.contains("8002") == true ||
                    e.message?.contains("STATUS_ALREADY_DISCOVERING", ignoreCase = true) == true
                ) {
                    // Code 8002: Discovery is ALREADY active in the Play Services daemon
                    Log.i(TAG, "Discovery session is already active in Nearby Connections (Code 8002: STATUS_ALREADY_DISCOVERING). Reconciling state to ACTIVE.")
                    _discoveryState.value = DiscoveryState.ACTIVE
                    _isDiscovering.value = true
                    discoveryRetryAttempts = 0
                    // Do NOT treat as fatal error or set red diagnostic text!
                } else {
                    val errorMsg = "Nearby discovery failed: ${e.message} (Code $statusCode)"
                    Log.e(TAG, errorMsg, e)
                    _discoveryState.value = DiscoveryState.STOPPED
                    _isDiscovering.value = false
                    _lastError.value = errorMsg
                    scheduleDiscoveryRecovery()
                }
            }
    }

    private fun scheduleDiscoveryRecovery() {
        if (discoveryRetryAttempts >= 3) {
            Log.w(TAG, "Max discovery recovery attempts (3) reached. Manual user intervention required.")
            return
        }
        discoveryRetryAttempts++
        val backoffDelayMs = (discoveryRetryAttempts * 3000L).coerceAtMost(10000L)
        Log.i(TAG, "Scheduling controlled discovery recovery attempt #$discoveryRetryAttempts in ${backoffDelayMs / 1000}s...")

        discoveryRecoveryJob?.cancel()
        discoveryRecoveryJob = scope.launch {
            delay(backoffDelayMs)
            if (_discoveryState.value == DiscoveryState.STOPPED) {
                Log.i(TAG, "Executing controlled discovery recovery attempt #$discoveryRetryAttempts...")
                startDiscovery()
            }
        }
    }

    @Synchronized
    fun stopAdvertising() {
        val currentState = _advertisingState.value
        if (currentState == AdvertisingState.STOPPED || currentState == AdvertisingState.STOPPING) {
            Log.d(TAG, "stopAdvertising called but state is already $currentState. No-op.")
            return
        }

        Log.i(TAG, "Stopping Nearby advertising (current state: $currentState)...")
        _advertisingState.value = AdvertisingState.STOPPING
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising", e)
        }
        _advertisingState.value = AdvertisingState.STOPPED
        _isAdvertising.value = false
        Log.i(TAG, "Nearby advertising stopped.")
    }

    @Synchronized
    fun stopDiscovery() {
        val currentState = _discoveryState.value
        if (currentState == DiscoveryState.STOPPED || currentState == DiscoveryState.STOPPING) {
            Log.d(TAG, "stopDiscovery called but state is already $currentState. No-op.")
            return
        }

        discoveryRecoveryJob?.cancel()
        discoveryRecoveryJob = null

        Log.i(TAG, "Stopping Nearby discovery (current state: $currentState)...")
        _discoveryState.value = DiscoveryState.STOPPING
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery", e)
        }
        _discoveryState.value = DiscoveryState.STOPPED
        _isDiscovering.value = false
        Log.i(TAG, "Nearby discovery stopped.")
    }

    fun stopAll() {
        Log.w(TAG, "stopAll called: Disconnecting all active mesh endpoints and stopping services.")
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

    /**
     * Sends bytes to an endpoint and tracks both dispatch (enqueue) and physical transfer update (SUCCESS/FAILURE).
     */
    fun sendTrackedPayload(
        endpointId: String,
        bytes: ByteArray,
        onEnqueued: (() -> Unit)? = null,
        onTransferSuccess: (() -> Unit)? = null,
        onTransferFailure: ((Int) -> Unit)? = null,
        onEnqueueFailure: ((Exception) -> Unit)? = null
    ): Long {
        if (!_connectedEndpoints.value.contains(endpointId)) {
            val ex = IllegalStateException("Endpoint $endpointId is not currently connected.")
            Log.w(TAG, "Cannot send tracked payload: $endpointId is not connected.")
            onEnqueueFailure?.invoke(ex)
            onTransferFailure?.invoke(PayloadTransferUpdate.Status.FAILURE)
            return -1L
        }

        val payload = Payload.fromBytes(bytes)
        val payloadId = payload.id

        payloadTrackers[payloadId] = PayloadTracker(
            payloadId = payloadId,
            endpointId = endpointId,
            onTransferSuccess = onTransferSuccess,
            onTransferFailure = onTransferFailure
        )

        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Payload $payloadId enqueued successfully for endpoint $endpointId")
                onEnqueued?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to enqueue payload $payloadId to $endpointId", e)
                payloadTrackers.remove(payloadId)
                _lastError.value = "Enqueue failed to $endpointId: ${e.message}"
                onEnqueueFailure?.invoke(e)
                onTransferFailure?.invoke(PayloadTransferUpdate.Status.FAILURE)
            }

        return payloadId
    }

    fun sendPayload(
        endpointId: String,
        bytes: ByteArray,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        sendTrackedPayload(
            endpointId = endpointId,
            bytes = bytes,
            onEnqueued = onSuccess,
            onTransferSuccess = {
                Log.d(TAG, "Transfer confirmed for payload to $endpointId")
            },
            onTransferFailure = { statusCode ->
                Log.w(TAG, "Transfer failed for payload to $endpointId with code $statusCode")
                onFailure?.invoke(RuntimeException("Payload transfer failed with status $statusCode"))
            },
            onEnqueueFailure = onFailure
        )
    }

    fun broadcastPayload(bytes: ByteArray): Int {
        val targets = _connectedEndpoints.value.toList()
        if (targets.isEmpty()) return 0

        for (target in targets) {
            sendPayload(target, bytes)
        }
        return targets.size
    }

    fun broadcastTrackedPayload(
        bytes: ByteArray,
        onPeerSuccess: ((endpointId: String) -> Unit)? = null,
        onPeerFailure: ((endpointId: String, status: Int) -> Unit)? = null
    ): Int {
        val targets = _connectedEndpoints.value.toList()
        if (targets.isEmpty()) return 0

        var count = 0
        for (target in targets) {
            val pId = sendTrackedPayload(
                endpointId = target,
                bytes = bytes,
                onTransferSuccess = { onPeerSuccess?.invoke(target) },
                onTransferFailure = { status -> onPeerFailure?.invoke(target, status) }
            )
            if (pId != -1L) count++
        }
        return count
    }
}

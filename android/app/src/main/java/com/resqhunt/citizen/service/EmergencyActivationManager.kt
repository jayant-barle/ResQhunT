package com.resqhunt.citizen.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.alert.EmergencyAlertManager
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.domain.model.EmergencyCategory
import com.resqhunt.citizen.domain.model.SeverityLevel
import com.resqhunt.citizen.domain.priority.DeterministicPriorityEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class RadioCheckResult(
    val isBluetoothEnabled: Boolean,
    val hasBluetoothPermission: Boolean,
    val isLocationEnabled: Boolean,
    val hasLocationPermission: Boolean,
    val isWifiEnabled: Boolean,
    val statusSummary: String
)

sealed class ConciseActivationStatus(val label: String) {
    object Idle : ConciseActivationStatus("Ready")
    object SosSaved : ConciseActivationStatus("SOS saved")
    object SearchingDevices : ConciseActivationStatus("Searching nearby devices")
    object WaitingForConnection : ConciseActivationStatus("Waiting for connection")
    object SentToNearbyDevice : ConciseActivationStatus("Sent to nearby device")
    object DeliveredToRescueServer : ConciseActivationStatus("Delivered to rescue server")
}

/**
 * Authoritative coordinator for emergency SOS activation workflows.
 * Handles immediate Room persistence, location attachment, mesh relay dispatch,
 * radio state verification, and audible/vibrating alerts.
 */
class EmergencyActivationManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ResQhunT_EmergencyAct"

        @Volatile
        private var instance: EmergencyActivationManager? = null

        fun getInstance(context: Context): EmergencyActivationManager {
            return instance ?: synchronized(this) {
                instance ?: EmergencyActivationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val volumeKeyDetector: VolumeKeySosTriggerDetector by lazy {
        VolumeKeySosTriggerDetector.fromContext(appContext)
    }

    val triggerDetector: VolumeKeySosTriggerDetector get() = volumeKeyDetector

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _activationStatus = MutableStateFlow<ConciseActivationStatus>(ConciseActivationStatus.Idle)
    val activationStatus: StateFlow<ConciseActivationStatus> = _activationStatus.asStateFlow()

    private val _latestActivatedSosId = MutableStateFlow<String?>(null)
    val latestActivatedSosId: StateFlow<String?> = _latestActivatedSosId.asStateFlow()

    private val _lastRadioCheck = MutableStateFlow<RadioCheckResult?>(null)
    val lastRadioCheck: StateFlow<RadioCheckResult?> = _lastRadioCheck.asStateFlow()

    /**
     * Executes the emergency activation workflow triggered by foreground Volume Up + Down shortcut,
     * quick settings tile, lock-screen notification action, or in-app button.
     *
     * @param source Name of triggering component ("VOLUME_KEYS_3S", "LOCKSCREEN_NOTIFICATION", "QUICK_SETTINGS_TILE", "SIMULATION_TEST")
     * @param isTestOverride Optional flag overriding test mode
     * @return The requestId of created SOS, or null if aborted (cooldown or disabled)
     */
    suspend fun executeEmergencyActivation(
        source: String = "VOLUME_KEYS_3S",
        isTestOverride: Boolean? = null
    ): String? = withContext(Dispatchers.IO) {
        val testMode = isTestOverride ?: volumeKeyDetector.isTestMode
        val stateSummary = AppLifecycleStateTracker.logCurrentState(TAG)
        Log.i(TAG, "[TRIGGER_INVOKED] Source: '$source' | TestMode=$testMode | $stateSummary")

        if (source == "VOLUME_KEYS_3S") {
            Log.i(TAG, "[TRIGGER_NOTICE] Volume Up + Volume Down 3-second shortcut triggered in foreground window.")
        }

        // 1. Guard: Check if trigger feature is disabled
        if (!volumeKeyDetector.isEnabled && source == "VOLUME_KEYS_3S") {
            Log.w(TAG, "Emergency activation aborted: Volume shortcut feature is disabled by user.")
            return@withContext null
        }

        // 2. Guard: Cooldown check - prevent duplicate SOS creation
        if (volumeKeyDetector.isInCooldown()) {
            val remainingMs = volumeKeyDetector.remainingCooldownMs()
            Log.w(TAG, "Emergency activation suppressed: In cooldown period (${remainingMs / 1000}s remaining). Duplicate SOS prevented.")
            return@withContext null
        }

        // 3. Test Mode Guard: Execute local alarm test without dispatching distress to mesh
        if (testMode) {
            Log.i(TAG, "Test mode active: Executing audio and vibration alert test. Mesh dispatch suppressed.")
            withContext(Dispatchers.Main) {
                EmergencyAlertManager.testAlarm(appContext)
            }
            return@withContext "test_sos_simulated"
        }

        val app = appContext as? ResQhunTApp ?: ResQhunTApp.instance
        val database = app.database
        val locationManager = app.locationManager
        val relayEngine = app.relayEngine
        val syncClient = app.syncClient

        val requestId = "sos_" + UUID.randomUUID().toString()
        _latestActivatedSosId.value = requestId
        triggerDetector.markTriggered()

        // Step 1: Attach freshest cached location immediately WITHOUT blocking
        val cachedFix = locationManager.getBestCachedFix()
        val lat: Double? = cachedFix?.latitude
        val lon: Double? = cachedFix?.longitude
        val acc: Float? = cachedFix?.accuracy
        val locTimestamp: Long? = cachedFix?.timestamp
        val locSource: String = cachedFix?.source ?: "PENDING_ACQUISITION"

        if (lat != null) {
            Log.i(TAG, "[LOCATION_STATUS] Attached real cached location: Lat=$lat, Lon=$lon, Acc=${acc}m, Time=$locTimestamp, Source=$locSource. Zero delay on transmission.")
        } else {
            Log.i(TAG, "[LOCATION_STATUS] No cached fix available. Persisted SOS with null coordinates (never dummy 0,0). Initiating background GPS acquisition.")
        }

        val priorityEval = DeterministicPriorityEngine.evaluate(
            category = EmergencyCategory.RESCUE,
            severity = SeverityLevel.CRITICAL,
            affectedCount = 1,
            createdAtTimestampMs = System.currentTimeMillis()
        )

        val sosEntity = SosEntity(
            requestId = requestId,
            category = "RESCUE",
            severity = "CRITICAL",
            affectedCount = 1,
            description = "Emergency SOS activated via rapid trigger ($source). Immediate response required.",
            latitude = lat,
            longitude = lon,
            locationAccuracy = acc,
            locationAddress = if (lat != null) "Coordinates Locked ($locSource)" else "Coordinates Pending Acquisition",
            locationTimestamp = locTimestamp,
            locationSource = locSource,
            deliveryState = DeliveryState.STORED_LOCALLY.name,
            priorityScore = priorityEval.priorityScore,
            priorityCategory = priorityEval.priorityCategory.name
        )

        // Step 2: IMMEDIATE Room Persistence (Never wait for GPS or Bluetooth)
        database.sosDao().insertSos(sosEntity)
        _activationStatus.value = ConciseActivationStatus.SosSaved
        Log.i(TAG, "Immediate Room persistence complete for $requestId. DeliveryState: STORED_LOCALLY. Status: SOS saved")

        // Step 3: Start/resume Foreground Service & Mesh Advertising/Discovery
        withContext(Dispatchers.Main) {
            SosRelayForegroundService.start(appContext)
        }
        _activationStatus.value = ConciseActivationStatus.SearchingDevices
        Log.i(TAG, "Foreground service started. Status: Searching nearby devices")

        // Step 4: Check Permissions and Radio States (Bluetooth, Location, Wi-Fi)
        val radioCheck = checkAndPromptRadioStates(appContext)
        _lastRadioCheck.value = radioCheck
        Log.i(TAG, "Radio check completed: ${radioCheck.statusSummary}")

        // Step 5: Trigger Local Audio/Vibration Alert and High-Priority Notification
        withContext(Dispatchers.Main) {
            EmergencyAlertManager.triggerSosAlert(
                context = appContext,
                sos = sosEntity,
                originDeviceId = relayEngine.deviceId
            )
        }

        // Step 6: Dispatch to Peer-Relay Queue and Multi-Hop Relay
        scope.launch {
            try {
                val connectedPeersCount = app.nearbyManager.connectedEndpoints.value.size
                if (connectedPeersCount > 0) {
                    Log.i(TAG, "[MESH_DISPATCH] Actively broadcasting SOS $requestId to $connectedPeersCount connected peer(s)...")
                } else {
                    Log.i(TAG, "[MESH_QUEUE] No peers currently connected. SOS $requestId safely stored in Room outbox. Automatic relay will occur when peers connect.")
                }
                relayEngine.createAndBroadcastSos(sosEntity)

                // Monitor delivery state updates from Room to reflect concise status
                database.sosDao().getSosByIdFlow(requestId).collect { updatedSos ->
                    if (updatedSos != null) {
                        when (updatedSos.deliveryState) {
                            DeliveryState.STORED_LOCALLY.name -> {
                                _activationStatus.value = ConciseActivationStatus.SosSaved
                            }
                            DeliveryState.RELAY_PENDING.name,
                            DeliveryState.TRANSFER_IN_PROGRESS.name -> {
                                _activationStatus.value = ConciseActivationStatus.SearchingDevices
                            }
                            DeliveryState.RELAYED_TO_PEER.name,
                            DeliveryState.RECEIVED_BY_PEER.name -> {
                                _activationStatus.value = ConciseActivationStatus.SentToNearbyDevice
                            }
                            DeliveryState.SERVER_RECEIVED.name,
                            DeliveryState.COORDINATOR_ACKNOWLEDGED.name,
                            DeliveryState.ASSIGNED.name,
                            DeliveryState.IN_PROGRESS.name,
                            DeliveryState.RESOLVED.name -> {
                                _activationStatus.value = ConciseActivationStatus.DeliveredToRescueServer
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting SOS to mesh: ${e.message}", e)
            }
        }

        // Step 7: Asynchronous Fresh GPS acquisition (do not block initial broadcast)
        scope.launch {
            try {
                if (locationManager.hasLocationPermission() && locationManager.isLocationEnabled()) {
                    Log.d(TAG, "Attempting background GPS lock acquisition...")
                    val freshFix = locationManager.acquireCurrentOrLastKnownLocation(maxWaitMs = 5000L)
                    if (freshFix != null) {
                        relayEngine.updateSosLocationAndBroadcast(requestId, freshFix)
                        Log.i(TAG, "Updated and broadcast fresh location for $requestId (${freshFix.latitude}, ${freshFix.longitude})")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background GPS update exception: ${e.message}")
            }
        }

        // Step 8: Cloud Sync if Internet Available (Preserve local SOS if offline or fails)
        scope.launch {
            try {
                if (syncClient.isOnline()) {
                    Log.i(TAG, "Internet connectivity present. Syncing SOS with backend server...")
                    val syncResult = syncClient.syncPendingWithServer()
                    Log.i(TAG, "Backend sync completed: ${syncResult.message}")
                } else {
                    Log.i(TAG, "Device is offline. Local Room SOS preserved safely for mesh relay.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud sync failed. Local SOS preserved in Room: ${e.message}")
            }
        }

        return@withContext requestId
    }

    fun checkAndPromptRadioStates(context: Context): RadioCheckResult {
        // 1. Bluetooth check
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isBtEnabled = try {
            if (hasBtPermission) btAdapter?.isEnabled == true else false
        } catch (e: SecurityException) {
            false
        }

        if (hasBtPermission && !isBtEnabled) {
            Log.w(TAG, "Bluetooth is disabled. Launching supported system enable request.")
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(enableBtIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not launch Bluetooth enable dialog directly: ${e.message}")
            }
        }

        // 2. Location check
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val hasLocPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val isLocEnabled = locManager?.let {
            it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } ?: false

        if (!isLocEnabled) {
            Log.w(TAG, "System location is disabled. Prompting user to enable location settings.")
            try {
                val locSettingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(locSettingsIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not launch Location settings directly: ${e.message}")
            }
        }

        // 3. Wi-Fi check
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiEnabled = wifiManager?.isWifiEnabled ?: false

        val summary = "BT=${if (isBtEnabled) "ON" else "OFF"}, GPS=${if (isLocEnabled) "ON" else "OFF"}, Wi-Fi=${if (isWifiEnabled) "ON" else "OFF"}, BT_Perm=$hasBtPermission, Loc_Perm=$hasLocPermission"
        return RadioCheckResult(
            isBluetoothEnabled = isBtEnabled,
            hasBluetoothPermission = hasBtPermission,
            isLocationEnabled = isLocEnabled,
            hasLocationPermission = hasLocPermission,
            isWifiEnabled = isWifiEnabled,
            statusSummary = summary
        )
    }

    fun cancelEmergencyActivation(context: Context) {
        EmergencyAlertManager.silenceAlert(context)
        _activationStatus.value = ConciseActivationStatus.Idle
        Log.i(TAG, "Emergency activation cancelled by user.")
    }
}

package com.resqhunt.citizen.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.resqhunt.citizen.ResQhunTApp
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.resqhunt.citizen.service.EmergencyActivationManager
import com.resqhunt.citizen.service.VolumeKeySosTriggerDetector
import com.resqhunt.citizen.ui.components.VolumeSosCountdownDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.resqhunt.citizen.alert.EmergencyAlertManager
import com.resqhunt.citizen.alert.IncomingSosAlertDialog
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.service.SosRelayForegroundService
import com.resqhunt.citizen.ui.navigation.NavGraph
import com.resqhunt.citizen.ui.navigation.Screen
import com.resqhunt.citizen.ui.theme.ResQhunTTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ResQhunT_MainActivity"
    }

    private val app by lazy { application as ResQhunTApp }
    private val nearbyManager: NearbyConnectionsManager get() = app.nearbyManager
    private val relayEngine: StoreAndForwardRelayEngine get() = app.relayEngine
    val activationManager by lazy { EmergencyActivationManager.getInstance(applicationContext) }
    val volumeDetector get() = activationManager.volumeKeyDetector

    data class VolumeCountdownState(val remainingSeconds: Int, val progress: Float)
    val volumeCountdown = MutableStateFlow<VolumeCountdownState?>(null)
    private var countdownJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Evaluate overall permission state across all versions
        val locationGranted = hasLocationPermissions()
        val bluetoothGranted = hasBluetoothPermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) {
                Log.w(
                    TAG,
                    "POST_NOTIFICATIONS permission denied by user. System alert banners may be suppressed, but in-app emergency alerts will remain fully active."
                )
            }
        }

        if (locationGranted && bluetoothGranted) {
            Log.i(TAG, "Essential mesh permissions granted. Starting discovery, advertising, and background trigger monitor...")
            nearbyManager.startAdvertising()
            nearbyManager.startDiscovery()
            SosRelayForegroundService.start(this)
        } else {
            Log.w(TAG, "Essential mesh permissions not fully granted: location=$locationGranted, bluetooth=$bluetoothGranted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions and initialize mesh
        requestRequiredPermissions()

        setContent {
            ResQhunTTheme {
                val navController = rememberNavController()
                val activeAlert by EmergencyAlertManager.activeAlert.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    NavGraph(
                        navController = navController,
                        database = app.database,
                        nearbyManager = nearbyManager,
                        relayEngine = relayEngine
                    )

                    val currentAlert = activeAlert
                    if (currentAlert != null) {
                        IncomingSosAlertDialog(
                            alert = currentAlert,
                            onSilence = {
                                EmergencyAlertManager.silenceAlert(this@MainActivity)
                            },
                            onViewDetails = { reqId ->
                                EmergencyAlertManager.silenceAlert(this@MainActivity)
                                navController.navigate(Screen.SosDetails.createRoute(reqId))
                            }
                        )
                    }

                    val currentCountdown by volumeCountdown.collectAsState()
                    currentCountdown?.let { countdown ->
                        VolumeSosCountdownDialog(
                            remainingSeconds = countdown.remainingSeconds,
                            progress = countdown.progress,
                            onCancel = {
                                cancelVolumeCountdownJob()
                                volumeDetector.reset()
                            }
                        )
                    }
                }
            }
        }
    }

    fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10-11: BLUETOOTH and BLUETOOTH_ADMIN are granted at installation
            true
        }
    }

    fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Android 12-12L+ (API 31+) Nearby Bluetooth runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Android 13+ (API 33+) Nearby Wi-Fi and Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            nearbyManager.startAdvertising()
            nearbyManager.startDiscovery()
            SosRelayForegroundService.start(this)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        // Must work ONLY while ResQhunT is open, visible, and in the foreground
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val result = volumeDetector.onKeyDown(keyCode, System.currentTimeMillis())
            when (result) {
                is VolumeKeySosTriggerDetector.KeyActionResult.CountdownStarted -> {
                    startVolumeCountdownJob()
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.CountdownProgress -> {
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.Triggered -> {
                    cancelVolumeCountdownJob()
                    lifecycleScope.launch {
                        activationManager.executeEmergencyActivation(
                            source = "VOLUME_KEYS_3S",
                            isTestOverride = result.isTestMode
                        )
                    }
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.ConsumedHoldingAlreadyTriggered,
                is VolumeKeySosTriggerDetector.KeyActionResult.InCooldown -> {
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.NotHandled,
                is VolumeKeySosTriggerDetector.KeyActionResult.Disabled -> {
                    return super.dispatchKeyEvent(event)
                }
                else -> return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            val result = volumeDetector.onKeyUp(keyCode, System.currentTimeMillis())
            when (result) {
                is VolumeKeySosTriggerDetector.KeyActionResult.Cancelled -> {
                    cancelVolumeCountdownJob()
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.Consumed -> {
                    cancelVolumeCountdownJob()
                    return true
                }
                is VolumeKeySosTriggerDetector.KeyActionResult.NotHandled -> {
                    return super.dispatchKeyEvent(event)
                }
                else -> return super.dispatchKeyEvent(event)
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun startVolumeCountdownJob() {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            val targetMs = volumeDetector.requiredHoldDurationMs
            val start = System.currentTimeMillis()
            while (isActive && volumeDetector.isVolumeUpPressed && volumeDetector.isVolumeDownPressed) {
                val elapsed = System.currentTimeMillis() - start
                val remaining = (targetMs - elapsed).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
                val sec = kotlin.math.ceil(remaining / 1000.0).toInt().coerceIn(1, 3)
                volumeCountdown.value = VolumeCountdownState(sec, progress)

                if (elapsed >= targetMs) {
                    volumeCountdown.value = null
                    val res = volumeDetector.checkHoldProgress(System.currentTimeMillis())
                    if (res is VolumeKeySosTriggerDetector.KeyActionResult.Triggered) {
                        activationManager.executeEmergencyActivation(
                            source = "VOLUME_KEYS_3S",
                            isTestOverride = res.isTestMode
                        )
                    }
                    break
                }
                delay(40)
            }
        }
    }

    private fun cancelVolumeCountdownJob() {
        countdownJob?.cancel()
        countdownJob = null
        volumeCountdown.value = null
    }

    override fun onPause() {
        super.onPause()
        cancelVolumeCountdownJob()
        volumeDetector.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not tear down the mesh if this is a configuration change (rotation)
        if (!isChangingConfigurations) {
            // Keep active if foreground service is running, otherwise clean up
            Log.d(TAG, "MainActivity onDestroy")
        }
    }
}

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
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.service.SosRelayForegroundService
import com.resqhunt.citizen.ui.navigation.NavGraph
import com.resqhunt.citizen.ui.theme.ResQhunTTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ResQhunT_MainActivity"
    }

    private val app by lazy { application as ResQhunTApp }
    private val nearbyManager: NearbyConnectionsManager get() = app.nearbyManager
    private val relayEngine: StoreAndForwardRelayEngine get() = app.relayEngine

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check essential mesh permissions
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.BLUETOOTH_ADVERTISE] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }

        if (locationGranted && bluetoothGranted) {
            Log.i(TAG, "Essential mesh permissions granted. Starting discovery and advertising...")
            nearbyManager.startAdvertising()
            nearbyManager.startDiscovery()
        } else {
            Log.w(TAG, "Essential mesh permissions were not fully granted: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions and initialize mesh
        requestRequiredPermissions()

        setContent {
            ResQhunTTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    database = app.database,
                    nearbyManager = nearbyManager,
                    relayEngine = relayEngine
                )
            }
        }
    }

    fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

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
        }
    }

    fun isBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }

    fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
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

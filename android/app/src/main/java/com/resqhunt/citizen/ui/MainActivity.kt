package com.resqhunt.citizen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.ui.navigation.NavGraph
import com.resqhunt.citizen.ui.theme.ResQhunTTheme

class MainActivity : ComponentActivity() {

    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var relayEngine: StoreAndForwardRelayEngine

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check results and start discovery if granted
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            nearbyManager.startAdvertising()
            nearbyManager.startDiscovery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ResQhunTApp
        nearbyManager = NearbyConnectionsManager(this)
        relayEngine = StoreAndForwardRelayEngine(this, nearbyManager, app.database)

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

    private fun requestRequiredPermissions() {
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

    override fun onDestroy() {
        super.onDestroy()
        nearbyManager.stopAll()
    }
}

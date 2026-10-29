package com.resqhunt.citizen.ui.screens.mesh

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.ui.MainActivity
import com.resqhunt.citizen.ui.theme.*

@Composable
fun NearbyDeviceStatusScreen(
    database: AppDatabase,
    nearbyManager: NearbyConnectionsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val peers by database.peerDao().getAllPeersFlow().collectAsState(initial = emptyList())
    val connectedEndpoints by nearbyManager.connectedEndpoints.collectAsState()
    val discoveredEndpoints by nearbyManager.discoveredEndpoints.collectAsState()
    val isAdvertising by nearbyManager.isAdvertising.collectAsState()
    val isDiscovering by nearbyManager.isDiscovering.collectAsState()
    val advertisingState by nearbyManager.advertisingState.collectAsState()
    val discoveryState by nearbyManager.discoveryState.collectAsState()
    val lastError by nearbyManager.lastError.collectAsState()

    // Hardware status checks
    val isBtEnabled = remember(isAdvertising, isDiscovering) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        adapter != null && adapter.isEnabled
    }

    val isLocEnabled = remember(isAdvertising, isDiscovering) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    val isPlayServicesOk = remember { nearbyManager.isGooglePlayServicesAvailable() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Text("Nearby Mesh Radar", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            IconButton(onClick = {
                // Soft refresh: ensure beacon and discovery are active without dropping connected peers
                (context as? MainActivity)?.requestRequiredPermissions()
                nearbyManager.startAdvertising()
                nearbyManager.startDiscovery()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan", tint = NavyPrimary)
            }
        }

        // Hardware / Permission Diagnostic Warnings
        if (!isPlayServicesOk) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = EmergencyRed.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed)
                    Text("Google Play Services is not available. Nearby Connections requires Play Services.", color = EmergencyRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isBtEnabled) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = EmergencyRed.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed)
                        Text("Bluetooth is disabled. Please turn ON Bluetooth for device discovery.", color = EmergencyRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!isLocEnabled) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404))
                        Text("Location is disabled. Nearby Wi-Fi & BLE discovery requires Location Services.", color = Color(0xFF856404), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF856404)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Radar Mesh Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google Nearby Connections (Cluster Mode)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Local Node: ${nearbyManager.localDeviceName}", color = TealAccent, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Advertising Beacon:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    val (advText, advColor) = when (advertisingState) {
                        com.resqhunt.citizen.mesh.AdvertisingState.ACTIVE -> "ACTIVE (Beaconing)" to TealAccent
                        com.resqhunt.citizen.mesh.AdvertisingState.STARTING -> "STARTING..." to Color(0xFFFFD54F)
                        com.resqhunt.citizen.mesh.AdvertisingState.STOPPING -> "STOPPING..." to Color(0xFFFFD54F)
                        com.resqhunt.citizen.mesh.AdvertisingState.STOPPED -> "IDLE (Stopped)" to Color.LightGray
                    }
                    Text(advText, color = advColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Peer Discovery:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    val (discText, discColor) = when (discoveryState) {
                        com.resqhunt.citizen.mesh.DiscoveryState.ACTIVE -> "SCANNING (Active)" to TealAccent
                        com.resqhunt.citizen.mesh.DiscoveryState.STARTING -> "STARTING..." to Color(0xFFFFD54F)
                        com.resqhunt.citizen.mesh.DiscoveryState.STOPPING -> "STOPPING..." to Color(0xFFFFD54F)
                        com.resqhunt.citizen.mesh.DiscoveryState.STOPPED -> "IDLE (Stopped)" to Color.LightGray
                    }
                    Text(discText, color = discColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Connected Mesh Nodes:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("${connectedEndpoints.size} node(s)", color = if (connectedEndpoints.isNotEmpty()) TealAccent else Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (!lastError.isNullOrBlank()) {
                    Text(
                        text = "Diagnostics: $lastError",
                        color = EmergencyLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Lifecycle State & Trigger Test Diagnostics Card
        val lifecycleState by com.resqhunt.citizen.service.AppLifecycleStateTracker.currentState.collectAsState()
        val tracker = remember { com.resqhunt.citizen.service.AppLifecycleStateTracker }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lifecycle State Diagnostics", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (lifecycleState) {
                            com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.FOREGROUND -> TealDark.copy(alpha = 0.15f)
                            com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.BACKGROUND -> NavyPrimary.copy(alpha = 0.15f)
                            com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.PROCESS_KILLED_RECOVERED -> WarningAmber.copy(alpha = 0.15f)
                            com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.FORCE_STOPPED_BLOCKED -> EmergencyRed.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = lifecycleState.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (lifecycleState) {
                                com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.FOREGROUND -> TealDark
                                com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.BACKGROUND -> NavyPrimary
                                com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.PROCESS_KILLED_RECOVERED -> WarningAmber
                                com.resqhunt.citizen.service.AppLifecycleStateTracker.ProcessLifecycleState.FORCE_STOPPED_BLOCKED -> EmergencyRed
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text("PID: ${tracker.processId}  •  Uptime: ${tracker.getUptimeSeconds()}s  •  Revival: ${tracker.revivalSource ?: "Normal Launch"}", fontSize = 11.sp, color = MutedGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CanvasBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Lifecycle Test States & Trigger Support:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("1. [BACKGROUND] Active: Lock-Screen Notification Action, Quick Settings Tile, In-App SOS, and Foreground Service Beacon are 100% operational.", fontSize = 10.sp, color = InkText, lineHeight = 14.sp)
                        Text("2. [PROCESS_KILLED] Recoverable: Quick Settings Tile binds directly even if process is dead; START_STICKY restores foreground beacon; BootReceiver restores on reboot.", fontSize = 10.sp, color = InkText, lineHeight = 14.sp)
                        Text("3. [FORCE_STOPPED] Blocked by OS: Android assigns FLAG_EXCLUDE_STOPPED_PACKAGES. OS halts all background triggers, broadcasts, and receivers until user taps app launcher.", fontSize = 10.sp, color = EmergencyRed, lineHeight = 14.sp)
                    }
                }
            }
        }

        // Action Buttons Row: Restart Advertising / Discovery
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    (context as? MainActivity)?.requestRequiredPermissions()
                    nearbyManager.startAdvertising()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Beacon", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    (context as? MainActivity)?.requestRequiredPermissions()
                    nearbyManager.startDiscovery()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Discovery", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text("Discovered Ad-Hoc Nodes (${peers.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyPrimary)

        if (peers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(36.dp))
                    Text("Searching for compatible Android devices nearby...", fontSize = 12.sp, color = MutedGray)
                    Text("Keep both devices in range with Bluetooth & Location enabled.", fontSize = 11.sp, color = MutedGray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(peers) { peer ->
                    val isConnected = connectedEndpoints.contains(peer.endpointId) || peer.isConnected
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) TealAccent.copy(alpha = 0.2f) else CanvasBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isConnected) TealDark else MutedGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(peer.deviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkText)
                                    Text("Endpoint: ${peer.endpointId}", fontSize = 11.sp, color = MutedGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnected) TealAccent.copy(alpha = 0.15f) else CanvasBg
                            ) {
                                Text(
                                    text = if (isConnected) "Connected" else "Discovered",
                                    color = if (isConnected) TealDark else MutedGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.resqhunt.citizen.ui.screens.mesh

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.ui.theme.*

@Composable
fun NearbyDeviceStatusScreen(
    database: AppDatabase,
    nearbyManager: NearbyConnectionsManager,
    onBack: () -> Unit
) {
    val peers by database.peerDao().getAllPeersFlow().collectAsState(initial = emptyList())
    val connectedEndpoints by nearbyManager.connectedEndpoints.collectAsState()
    val isAdvertising by nearbyManager.isAdvertising.collectAsState()
    val isDiscovering by nearbyManager.isDiscovering.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                nearbyManager.startDiscovery()
                nearbyManager.startAdvertising()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart Scan", tint = NavyPrimary)
            }
        }

        // Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google Nearby Connections (Cluster Mode)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Advertising Beacon:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(if (isAdvertising) "ACTIVE (BLE)" else "IDLE", color = if (isAdvertising) TealAccent else Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Peer Discovery:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(if (isDiscovering) "SCANNING (P2P)" else "IDLE", color = if (isDiscovering) TealAccent else Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Text("Discovered Ad-Hoc Nodes (${peers.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NavyPrimary)

        if (peers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(36.dp))
                    Text("Searching for compatible Android devices nearby...", fontSize = 12.sp, color = MutedGray)
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

package com.resqhunt.citizen.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.ui.theme.*

@Composable
fun CitizenHomeScreen(
    database: AppDatabase,
    onNavigateToOneTapSos: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToMyRequests: () -> Unit,
    onNavigateToNearbyDevices: () -> Unit,
    onNavigateToOfflineQueue: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val latestSos by database.sosDao().getLatestSosFlow().collectAsState(initial = null)
    val peers by database.peerDao().getAllPeersFlow().collectAsState(initial = emptyList())
    val messages by database.relayMessageDao().getAllMessagesFlow().collectAsState(initial = emptyList())

    val pendingCount = messages.count { it.status == "PENDING_FORWARD" }
    val connectedPeerCount = peers.count { it.isConnected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ResQhunT",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = NavyPrimary
                )
                Text(
                    text = "Citizen Emergency Terminal",
                    fontSize = 12.sp,
                    color = MutedGray,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NavyPrimary)
            }
        }

        // Network Status Chip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NavyPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TealAccent)
                    )
                    Text(
                        text = "Store-and-Forward Mesh Engine Ready",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$connectedPeerCount Peer(s)",
                    color = TealAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Active Emergency SOS Card (if latest SOS is active)
        latestSos?.let { sos ->
            if (sos.deliveryState != "RESOLVED") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetails(sos.requestId) }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE EMERGENCY",
                                color = EmergencyRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NavyPrimary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = sos.deliveryState.replace("_", " "),
                                    color = NavyPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = sos.description,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText,
                            maxLines = 2
                        )

                        Text(
                            text = "Tap to inspect delivery timeline & nearby relay nodes →",
                            fontSize = 11.sp,
                            color = TealDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Big ONE-TAP EMERGENCY SOS BUTTON
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = EmergencyRed),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToOneTapSos() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ONE-TAP EMERGENCY SOS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Persists locally and transmits to nearby peers without cell service.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Quick Action Grid (2x2)
        Text(
            text = "Emergency Management",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NavyPrimary
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionTile(
                title = "Nearby Peers",
                subtitle = "$connectedPeerCount connected",
                icon = Icons.Default.Bluetooth,
                iconColor = NavyPrimary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToNearbyDevices
            )
            ActionTile(
                title = "Offline Outbox",
                subtitle = "$pendingCount pending",
                icon = Icons.Default.Send,
                iconColor = TealDark,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToOfflineQueue
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionTile(
                title = "GPS & Location",
                subtitle = "Position settings",
                icon = Icons.Default.LocationOn,
                iconColor = EmergencyRed,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLocation
            )
            ActionTile(
                title = "Contacts",
                subtitle = "ICE family list",
                icon = Icons.Default.Phone,
                iconColor = NavyPrimary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToContacts
            )
        }

        // Past Requests button
        OutlinedButton(
            onClick = onNavigateToMyRequests,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View All My Emergency Requests", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkText)
            Text(subtitle, fontSize = 11.sp, color = MutedGray)
        }
    }
}

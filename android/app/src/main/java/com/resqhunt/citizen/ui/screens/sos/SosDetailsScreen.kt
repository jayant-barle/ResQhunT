package com.resqhunt.citizen.ui.screens.sos

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SosDetailsScreen(
    requestId: String,
    database: AppDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ResQhunTApp
    val syncClient = app.syncClient
    val nearbyManager = app.nearbyManager
    val coroutineScope = rememberCoroutineScope()

    var sos by remember { mutableStateOf<SosEntity?>(null) }
    val isSyncing by syncClient.isSyncing.collectAsState()
    val lastSyncError by syncClient.lastSyncError.collectAsState()
    val lastSyncSuccessTime by syncClient.lastSyncSuccessTime.collectAsState()
    val connectedEndpoints by nearbyManager.connectedEndpoints.collectAsState()
    val nearbyError by nearbyManager.lastError.collectAsState()

    var syncFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val refreshData: () -> Unit = {
        coroutineScope.launch {
            sos = database.sosDao().getSosById(requestId)
            // If online, query backend for any updated status from coordinators
            if (syncClient.isOnline()) {
                val updatedState = syncClient.checkIncidentStatus(requestId)
                if (updatedState != null) {
                    sos = database.sosDao().getSosById(requestId)
                }
            }
        }
    }

    LaunchedEffect(requestId) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Text("Emergency Beacon Tracker", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            IconButton(onClick = { refreshData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NavyPrimary)
            }
        }

        if (sos == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
            return
        }

        val currentSos = sos!!
        val currentState = currentSos.deliveryState

        // Status Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentSos.category,
                        color = EmergencyLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Priority Score: ${currentSos.priorityScore.toInt()}/100",
                        color = TealAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = currentSos.description,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID: ${currentSos.requestId.take(16)}...",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (currentState) {
                            "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED" -> TealAccent.copy(alpha = 0.2f)
                            "RELAYED_TO_PEER", "RECEIVED_BY_PEER" -> TealAccent.copy(alpha = 0.2f)
                            "TRANSFER_IN_PROGRESS" -> Color.Yellow.copy(alpha = 0.2f)
                            "RELAY_FAILED" -> EmergencyRed.copy(alpha = 0.2f)
                            else -> CanvasBg.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = currentState.replace("_", " "),
                            color = when (currentState) {
                                "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED" -> TealAccent
                                "RELAYED_TO_PEER", "RECEIVED_BY_PEER" -> TealAccent
                                "TRANSFER_IN_PROGRESS" -> Color.Yellow
                                "RELAY_FAILED" -> EmergencyLight
                                else -> Color.White
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Stepper: Honest Delivery State Machine
        Text(
            text = "Delivery State Machine",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NavyPrimary
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. Saved locally
                StepItem(
                    title = "1. SAVED LOCALLY",
                    subtitle = "Persisted to Room SQLite database (zero-data-loss guarantee)",
                    isCompleted = true,
                    isCurrent = currentState in listOf("CREATED", "STORED_LOCALLY")
                )

                // 2. Searching
                StepItem(
                    title = "2. SEARCHING FOR PEERS",
                    subtitle = "Nearby cluster BLE beacon & Wi-Fi Direct scanning active",
                    isCompleted = currentState !in listOf("CREATED", "STORED_LOCALLY", "RELAY_PENDING"),
                    isCurrent = currentState == "RELAY_PENDING"
                )

                // 3. Transfer in progress
                StepItem(
                    title = "3. TRANSFER IN PROGRESS",
                    subtitle = "Payload currently transmitting to connected adjacent peer",
                    isCompleted = currentState in listOf("RELAYED_TO_PEER", "RECEIVED_BY_PEER", "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState == "TRANSFER_IN_PROGRESS"
                )

                // 4. Relayed to peer / Received by peer (ACK Confirmed)
                StepItem(
                    title = "4. CONFIRMED BY PEER (ACK)",
                    subtitle = if (currentState == "RECEIVED_BY_PEER") "Received from mesh peer and persisted to local Room DB" else "Application-level ACK received from adjacent physical Android phone",
                    isCompleted = currentState in listOf("RELAYED_TO_PEER", "RECEIVED_BY_PEER", "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState in listOf("RELAYED_TO_PEER", "RECEIVED_BY_PEER")
                )

                // 5. Server Received
                StepItem(
                    title = "5. SERVER RECEIVED",
                    subtitle = "Uploaded to central cloud backend by gateway node (Confirmed by API)",
                    isCompleted = currentState in listOf("SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState == "SERVER_RECEIVED"
                )

                // 6. Coordinator Acknowledged
                StepItem(
                    title = "6. COORDINATOR ACKNOWLEDGED",
                    subtitle = "Rescue coordinator triaged and acknowledged incident in dashboard",
                    isCompleted = currentState in listOf("COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState == "COORDINATOR_ACKNOWLEDGED"
                )

                // 7. Assigned / In Progress
                StepItem(
                    title = "7. ASSIGNED & IN PROGRESS",
                    subtitle = "Disaster volunteer team deployed with equipment",
                    isCompleted = currentState in listOf("ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState in listOf("ASSIGNED", "IN_PROGRESS")
                )

                // 8. Resolved
                StepItem(
                    title = "8. RESOLVED",
                    subtitle = "Emergency situation resolved by emergency services",
                    isCompleted = currentState == "RESOLVED",
                    isCurrent = currentState == "RESOLVED"
                )
            }
        }

        // Diagnostics Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sync & Mesh Diagnostics", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)

                DiagnosticRow(label = "Connected Peers:", value = "${connectedEndpoints.size} active node(s)")
                DiagnosticRow(label = "Internet State:", value = if (syncClient.isOnline()) "ONLINE" else "OFFLINE (Mesh Relay Active)")
                DiagnosticRow(label = "Gateway Target:", value = syncClient.baseUrl)

                val lastSyncTime = lastSyncSuccessTime
                if (lastSyncTime != null) {
                    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    DiagnosticRow(label = "Last Successful Sync:", value = sdf.format(Date(lastSyncTime)))
                }

                if (!nearbyError.isNullOrBlank()) {
                    Text(
                        text = "Nearby Note: $nearbyError",
                        fontSize = 11.sp,
                        color = EmergencyRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!lastSyncError.isNullOrBlank()) {
                    Text(
                        text = "Gateway Status: $lastSyncError",
                        fontSize = 11.sp,
                        color = EmergencyRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Direct Gateway Upload Button
        Button(
            onClick = {
                coroutineScope.launch {
                    syncFeedbackMessage = null
                    val result = syncClient.syncPendingWithServer()
                    syncFeedbackMessage = result.message
                    refreshData()
                }
            },
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Syncing with Cloud Gateway...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attempt Direct Gateway Sync", fontWeight = FontWeight.Bold)
            }
        }

        syncFeedbackMessage?.let { msg ->
            Text(
                text = msg,
                fontSize = 12.sp,
                color = if (msg.contains("Success", ignoreCase = true)) TealDark else EmergencyRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MutedGray)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkText)
    }
}

@Composable
fun StepItem(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isCompleted) TealAccent
                    else if (isCurrent) EmergencyRed
                    else Color.LightGray.copy(alpha = 0.4f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else if (isCurrent) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        }

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isCurrent) EmergencyRed else if (isCompleted) NavyPrimary else MutedGray
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MutedGray,
                lineHeight = 15.sp
            )
        }
    }
}

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
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.data.local.entity.SosEntity
import com.resqhunt.citizen.data.remote.ResqHuntSyncClient
import com.resqhunt.citizen.domain.model.DeliveryState
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SosDetailsScreen(
    requestId: String,
    database: AppDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncClient = remember { ResqHuntSyncClient(context, database) }

    var sos by remember { mutableStateOf<SosEntity?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val refreshData = {
        coroutineScope.launch {
            sos = database.sosDao().getSosById(requestId)
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
            return
        }

        val currentSos = sos!!

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
                        text = "Score ${currentSos.priorityScore.toInt()}/100",
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

                Text(
                    text = "Request ID: ${currentSos.requestId}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Stepper: Delivery Lifecycle
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
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val currentState = currentSos.deliveryState

                StepItem(
                    title = "1. CREATED",
                    subtitle = "Emergency request initialized on device",
                    isCompleted = true,
                    isCurrent = false
                )
                StepItem(
                    title = "2. STORED LOCALLY",
                    subtitle = "Persisted to Room SQLite database (zero-data-loss guarantee)",
                    isCompleted = true,
                    isCurrent = currentState == "STORED_LOCALLY"
                )
                StepItem(
                    title = "3. RELAY PENDING",
                    subtitle = "Searching nearby cluster for BLE & Wi-Fi Direct peers",
                    isCompleted = currentState !in listOf("CREATED", "STORED_LOCALLY"),
                    isCurrent = currentState == "RELAY_PENDING"
                )
                StepItem(
                    title = "4. RELAYED TO PEER",
                    subtitle = "Transferred to adjacent physical Android node in mesh",
                    isCompleted = currentState in listOf(
                        "RELAYED_TO_PEER", "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"
                    ),
                    isCurrent = currentState == "RELAYED_TO_PEER"
                )
                StepItem(
                    title = "5. SERVER RECEIVED",
                    subtitle = "Uploaded to central ResQhunT cloud backend by gateway node",
                    isCompleted = currentState in listOf(
                        "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"
                    ),
                    isCurrent = currentState == "SERVER_RECEIVED"
                )
                StepItem(
                    title = "6. COORDINATOR ACKNOWLEDGED",
                    subtitle = "Rescue command triaged and acknowledged incident",
                    isCompleted = currentState in listOf("COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState == "COORDINATOR_ACKNOWLEDGED"
                )
                StepItem(
                    title = "7. ASSIGNED & IN PROGRESS",
                    subtitle = "Field volunteer assigned with required equipment",
                    isCompleted = currentState in listOf("ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                    isCurrent = currentState in listOf("ASSIGNED", "IN_PROGRESS")
                )
                StepItem(
                    title = "8. RESOLVED",
                    subtitle = "All rescue operations completed successfully",
                    isCompleted = currentState == "RESOLVED",
                    isCurrent = currentState == "RESOLVED"
                )
            }
        }

        // Direct Gateway Upload Button
        Button(
            onClick = {
                coroutineScope.launch {
                    isSyncing = true
                    syncMessage = null
                    val count = syncClient.syncPendingWithServer()
                    isSyncing = false
                    if (count > 0) {
                        syncMessage = "Successfully synced $count request(s) to cloud!"
                        refreshData()
                    } else {
                        syncMessage = if (!syncClient.isOnline()) {
                            "Device is offline. Mesh store-and-forward relay remains active."
                        } else {
                            "No pending requests or server unreachable."
                        }
                    }
                }
            },
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSyncing) "Syncing with Cloud..." else "Attempt Direct Gateway Sync", fontWeight = FontWeight.Bold)
        }

        syncMessage?.let { msg ->
            Text(
                text = msg,
                fontSize = 12.sp,
                color = if (msg.contains("Success")) TealDark else EmergencyRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
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

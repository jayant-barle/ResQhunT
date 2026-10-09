package com.resqhunt.citizen.ui.screens.sos

import android.content.Intent
import android.net.Uri
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
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class SimpleDeliveryStatus(val label: String, val level: Int) {
    SAVED("Saved", 1),
    SEARCHING("Searching", 2),
    SENT_TO_NEARBY("Sent to nearby device", 3),
    DELIVERED_TO_SERVER("Delivered to rescue server", 4);

    companion object {
        fun fromDeliveryState(deliveryState: String): SimpleDeliveryStatus {
            return when (deliveryState) {
                "CREATED", "STORED_LOCALLY" -> SAVED
                "RELAY_PENDING", "TRANSFER_IN_PROGRESS", "RELAY_FAILED" -> SEARCHING
                "RELAYED_TO_PEER", "RECEIVED_BY_PEER" -> SENT_TO_NEARBY
                "SERVER_RECEIVED", "COORDINATOR_ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED" -> DELIVERED_TO_SERVER
                else -> SAVED
            }
        }
    }
}

@Composable
fun SosDetailsScreen(
    requestId: String,
    database: AppDatabase,
    onBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToNewSos: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as ResQhunTApp
    val syncClient = app.syncClient
    val relayEngine = app.relayEngine
    val coroutineScope = rememberCoroutineScope()

    val sos by remember(requestId) { database.sosDao().getSosByIdFlow(requestId) }.collectAsState(initial = null)
    val isSyncing by syncClient.isSyncing.collectAsState()
    var isRetryingMesh by remember { mutableStateOf(false) }
    var syncFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val refreshData: () -> Unit = {
        coroutineScope.launch {
            if (syncClient.isOnline()) {
                syncClient.checkIncidentStatus(requestId)
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
        // App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyPrimary)
            }
            Text("Emergency SOS Status", fontSize = 17.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Row {
                IconButton(onClick = onNavigateToDiagnostics) {
                    Icon(Icons.Default.Build, contentDescription = "Developer Diagnostics", tint = NavyPrimary)
                }
                IconButton(onClick = { refreshData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NavyPrimary)
                }
            }
        }

        if (sos == null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyPrimary)
            }
            return
        }

        val currentSos = sos!!
        val currentStatus = SimpleDeliveryStatus.fromDeliveryState(currentSos.deliveryState)

        // 1. Emergency Message, Type, and Priority Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Emergency Type & Priority
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmergencyRed.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = currentSos.category,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Priority: ${currentSos.priorityCategory} (${currentSos.priorityScore.toInt()}/100)",
                        color = TealAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Emergency Message
                Text(
                    text = currentSos.description,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                Text(
                    text = "ID: ${currentSos.requestId.take(16)}...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // 2. Simple Delivery Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delivery Status", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MutedGray)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (currentStatus) {
                            SimpleDeliveryStatus.DELIVERED_TO_SERVER -> TealAccent.copy(alpha = 0.2f)
                            SimpleDeliveryStatus.SENT_TO_NEARBY -> TealAccent.copy(alpha = 0.2f)
                            SimpleDeliveryStatus.SEARCHING -> Color(0xFFFFB300).copy(alpha = 0.2f)
                            SimpleDeliveryStatus.SAVED -> NavyPrimary.copy(alpha = 0.1f)
                        }
                    ) {
                        Text(
                            text = currentStatus.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = when (currentStatus) {
                                SimpleDeliveryStatus.DELIVERED_TO_SERVER -> TealDark
                                SimpleDeliveryStatus.SENT_TO_NEARBY -> TealDark
                                SimpleDeliveryStatus.SEARCHING -> Color(0xFFD87A00)
                                SimpleDeliveryStatus.SAVED -> NavyPrimary
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Clean 4-Stage Horizontal Delivery Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statuses = SimpleDeliveryStatus.values()
                    statuses.forEachIndexed { index, status ->
                        val isReached = currentStatus.level >= status.level
                        val isCurrent = currentStatus == status

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCurrent -> EmergencyRed
                                            isReached -> TealDark
                                            else -> Color.LightGray.copy(alpha = 0.4f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isReached && !isCurrent) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                } else if (isCurrent) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = status.label,
                                fontSize = 9.sp,
                                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isCurrent) EmergencyRed else if (isReached) NavyPrimary else MutedGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 11.sp
                            )
                        }

                        if (index < statuses.size - 1) {
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .weight(0.4f)
                                    .background(if (currentStatus.level > status.level) TealDark else Color.LightGray.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }
        }

        // 3. Last-Known Location and Timestamp Card
        val lat = currentSos.latitude
        val lon = currentSos.longitude
        val hasValidLocation = lat != null && lon != null &&
                (lat != 0.0 || lon != 0.0) &&
                !lat.isNaN() && !lon.isNaN() &&
                currentSos.locationSource != "UNAVAILABLE"

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (hasValidLocation) EmergencyRed else MutedGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Last-Known Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NavyPrimary
                        )
                    }

                    if (hasValidLocation) {
                        val sourceLabel = when (currentSos.locationSource) {
                            "FRESH_GPS" -> "FRESH GPS"
                            "LAST_KNOWN" -> "LAST KNOWN"
                            "MANUAL" -> "MANUAL NOTE"
                            else -> "GPS FIX"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TealAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = sourceLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = TealDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (hasValidLocation && lat != null && lon != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Coordinates:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = String.format(Locale.US, "%.6f°, %.6f°", lat, lon),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }

                    val locTime = currentSos.locationTimestamp ?: currentSos.createdAt
                    val timeSdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fix Timestamp:", fontSize = 12.sp, color = MutedGray)
                        Text(
                            text = timeSdf.format(Date(locTime)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                    }

                    if (!currentSos.locationAddress.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Landmark / Note:", fontSize = 12.sp, color = MutedGray)
                            Text(
                                text = currentSos.locationAddress!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkText,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Emergency+Beacon)")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lon")
                                val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(webIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Coordinates in Maps", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Location unavailable. GPS lock was not acquired at the time of transmission.",
                        fontSize = 12.sp,
                        color = MutedGray
                    )
                }
            }
        }

        // 4. Primary Actions & SOS Controls
        if (currentStatus == SimpleDeliveryStatus.SEARCHING) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isRetryingMesh = true
                        relayEngine.retrySosTransmission(requestId)
                        isRetryingMesh = false
                    }
                },
                enabled = !isRetryingMesh,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isRetryingMesh) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcasting to Nearby Devices...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Search for Nearby Devices", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Gateway Sync Button
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    syncFeedbackMessage = null
                    val result = syncClient.syncPendingWithServer()
                    syncFeedbackMessage = result.message
                    refreshData()
                }
            },
            enabled = !isSyncing,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Syncing with Rescue Server...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attempt Cloud Server Upload", fontWeight = FontWeight.Bold)
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

        // Trigger New Emergency Button
        Button(
            onClick = onNavigateToNewSos,
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trigger New Emergency SOS", fontWeight = FontWeight.Bold)
        }

        // Link to Separate Developer/Debug Screen
        TextButton(
            onClick = onNavigateToDiagnostics,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = MutedGray)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Developer & Mesh Diagnostics", color = MutedGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

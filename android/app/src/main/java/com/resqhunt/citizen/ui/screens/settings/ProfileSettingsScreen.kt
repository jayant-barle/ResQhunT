package com.resqhunt.citizen.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.ResQhunTApp
import com.resqhunt.citizen.mesh.BatteryAwareRouter
import com.resqhunt.citizen.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ResQhunTApp
    val syncClient = app.syncClient
    val coroutineScope = rememberCoroutineScope()

    val batteryRouter = remember { BatteryAwareRouter(context) }
    val batteryStatus = remember { batteryRouter.getBatteryStatus() }

    var gatewayUrl by remember { mutableStateOf(syncClient.baseUrl) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var pingMessage by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBg)
            .verticalScroll(rememberScrollState())
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
            Text("Settings & Diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NavyPrimary)
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Gateway Endpoint Configuration
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backend Gateway Host URL", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                Text(
                    "Set to your backend URL (e.g., http://10.0.2.2:5000 in Emulator, or http://<YOUR_PC_WIFI_IP>:5000 on real Android phones).",
                    fontSize = 11.sp,
                    color = MutedGray
                )

                OutlinedTextField(
                    value = gatewayUrl,
                    onValueChange = { gatewayUrl = it },
                    label = { Text("Gateway Base URL") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            syncClient.baseUrl = gatewayUrl.trim()
                            saveMessage = "Gateway URL saved: ${syncClient.baseUrl}"
                            pingMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save URL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isPinging = true
                                pingMessage = null
                                val testUrl = "${gatewayUrl.trim().trimEnd('/')}/api/health"
                                try {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(5, TimeUnit.SECONDS)
                                        .readTimeout(5, TimeUnit.SECONDS)
                                        .build()
                                    val req = Request.Builder().url(testUrl).get().build()
                                    val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                                    if (res.isSuccessful) {
                                        pingMessage = "SUCCESS: Backend is reachable (${res.code} OK)"
                                    } else {
                                        pingMessage = "FAILED: Server returned HTTP ${res.code}"
                                    }
                                } catch (e: Exception) {
                                    pingMessage = "FAILED: ${e.message ?: e.javaClass.simpleName}"
                                } finally {
                                    isPinging = false
                                }
                            }
                        },
                        enabled = !isPinging,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Ping", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                saveMessage?.let {
                    Text(it, fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                }

                pingMessage?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = if (it.startsWith("SUCCESS")) TealDark else EmergencyRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Local Device Mesh Identity
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mesh Identity", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                Text("Device ID: ${app.nearbyManager.localDeviceId}", fontSize = 11.sp, color = InkText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Beacon Name: ${app.nearbyManager.localDeviceName}", fontSize = 11.sp, color = InkText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text("Nearby Strategy: P2P_CLUSTER (BLE & Wi-Fi Direct)", fontSize = 11.sp, color = MutedGray)
            }
        }

        // Battery-Aware Routing Status
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = TealDark)
                    Text("Battery-Aware Mesh Throttler", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Battery Level:", fontSize = 12.sp, color = MutedGray)
                    Text("${batteryStatus.percentage}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Charging State:", fontSize = 12.sp, color = MutedGray)
                    Text(if (batteryStatus.isCharging) "Charging (AC/USB)" else "On Battery", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Power Profile:", fontSize = 12.sp, color = MutedGray)
                    Text(if (batteryStatus.isLowBattery) "Power Saver (Duty Cycle 45s)" else "Normal Operational Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (batteryStatus.isLowBattery) EmergencyRed else TealDark)
                }
            }
        }
    }
}

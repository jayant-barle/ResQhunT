package com.resqhunt.citizen.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqhunt.citizen.data.remote.ResqHuntSyncClient
import com.resqhunt.citizen.mesh.BatteryAwareRouter
import com.resqhunt.citizen.ui.theme.*

@Composable
fun ProfileSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val batteryRouter = remember { BatteryAwareRouter(context) }
    val batteryStatus = remember { batteryRouter.getBatteryStatus() }

    var gatewayUrl by remember { mutableStateOf(ResqHuntSyncClient.baseUrl) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

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
                    "Set to your local server IP (e.g. http://10.0.2.2:5000 in Android Emulator, or http://192.168.x.x:5000 on physical Wi-Fi).",
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

                Button(
                    onClick = {
                        ResqHuntSyncClient.baseUrl = gatewayUrl.trim()
                        saveMessage = "Gateway URL updated to ${ResqHuntSyncClient.baseUrl}"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Gateway URL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                saveMessage?.let {
                    Text(it, fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                }
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

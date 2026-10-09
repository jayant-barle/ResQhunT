package com.resqhunt.citizen.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
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

        // Emergency Alert & Alarm Testing
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = EmergencyRed)
                    Text("Emergency Alarm & Haptic Alert", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                }

                Text(
                    "Test the loud audio alarm and haptic vibration used when an incoming nearby emergency SOS arrives over the mesh.",
                    fontSize = 11.sp,
                    color = MutedGray,
                    lineHeight = 15.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            com.resqhunt.citizen.alert.EmergencyAlertManager.testAlarm(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.resqhunt.citizen.alert.EmergencyAlertManager.silenceAlert(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Silence Alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CanvasBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Honest System Note: Audible alerts rely on Android Alarm audio and system notification channels. System Do Not Disturb (DND), media volume settings, notification permission denials (Android 13+), or OEM background battery optimizations (e.g., Xiaomi MIUI, Samsung OneUI) cannot be overridden universally without user approval in Android App Settings.",
                        fontSize = 10.sp,
                        color = MutedGray,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // Foreground Volume Up + Volume Down SOS Shortcut
        val activationManager = remember { com.resqhunt.citizen.service.EmergencyActivationManager.getInstance(context) }
        val detector = remember { activationManager.volumeKeyDetector }
        var isTriggerEnabled by remember { mutableStateOf(detector.isEnabled) }
        var isTestModeActive by remember { mutableStateOf(detector.isTestMode) }
        var simulationFeedback by remember { mutableStateOf<String?>(null) }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = EmergencyRed)
                        Text("Volume Up + Down SOS Shortcut", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                    }
                    Switch(
                        checked = isTriggerEnabled,
                        onCheckedChange = {
                            isTriggerEnabled = it
                            detector.isEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmergencyRed, checkedTrackColor = EmergencyRed.copy(alpha = 0.3f))
                    )
                }

                Text(
                    "Press and hold both Volume Up and Volume Down keys simultaneously for 3 seconds while ResQhunT is open in the foreground to trigger emergency SOS.",
                    fontSize = 11.sp,
                    color = MutedGray,
                    lineHeight = 15.sp
                )

                // Guard settings summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Hold Target", fontSize = 10.sp, color = MutedGray)
                        Text("3.0s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                    Column {
                        Text("Scope", fontSize = 10.sp, color = MutedGray)
                        Text("Foreground", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                    Column {
                        Text("Cooldown", fontSize = 10.sp, color = MutedGray)
                        Text("10s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                    Column {
                        Text("Status", fontSize = 10.sp, color = MutedGray)
                        Text(
                            if (!isTriggerEnabled) "Disabled" else if (detector.isInCooldown()) "In Cooldown" else "Ready",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isTriggerEnabled) MutedGray else if (detector.isInCooldown()) WarningAmber else TealDark
                        )
                    }
                }

                HorizontalDivider(color = CanvasBg, thickness = 1.dp)

                // Test Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safe Test Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NavyPrimary)
                        Text("Plays audio siren and haptics safely without broadcasting distress to mesh.", fontSize = 10.sp, color = MutedGray)
                    }
                    Switch(
                        checked = isTestModeActive,
                        onCheckedChange = {
                            isTestModeActive = it
                            detector.isTestMode = it
                        }
                    )
                }

                // Simulate 3s Volume Hold Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            simulationFeedback = "Simulating Volume Up + Down 3s hold..."
                            val res = activationManager.executeEmergencyActivation(
                                source = "SIMULATION_TEST",
                                isTestOverride = true
                            )
                            simulationFeedback = if (res != null) {
                                "✅ Test successful! Audio siren and haptic alert triggered in test mode."
                            } else {
                                "⚠️ Trigger ignored: Cooldown active (${detector.remainingCooldownMs() / 1000}s remaining)."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simulate Volume Up + Down (3s Test Trigger)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                simulationFeedback?.let {
                    Text(it, fontSize = 11.sp, color = if (it.startsWith("✅")) TealDark else WarningAmber, fontWeight = FontWeight.Bold)
                }

                // Foreground Behavior & Privacy Callout
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CanvasBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Foreground-Only Volume Shortcut Details:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text("• Foreground Operation: Active only while ResQhunT is open, visible, and the focused window. Keys are never intercepted in the background or when the screen is locked.", fontSize = 9.sp, color = MutedGray, lineHeight = 13.sp)
                        Text("• Normal Volume Control: Pressing Volume Up or Volume Down individually adjusts system volume normally.", fontSize = 9.sp, color = MutedGray, lineHeight = 13.sp)
                        Text("• Safe Cancellation: Releasing either volume key before 3 seconds immediately cancels the countdown.", fontSize = 9.sp, color = MutedGray, lineHeight = 13.sp)
                        Text("• Supported Fallbacks: The regular on-screen SOS button, Quick Settings SOS tile, and ongoing notification action remain available as fallbacks.", fontSize = 9.sp, color = InkText, lineHeight = 13.sp)
                    }
                }
            }
        }

        // Cross-Brand Battery Optimization & Background Execution
        val oemHelper = remember { com.resqhunt.citizen.service.OemBatteryOptimizationHelper }
        val oemInfo = remember { oemHelper.getOemGuidance(context) }
        var isBatteryExempt by remember { mutableStateOf(oemHelper.isBatteryOptimizationIgnored(context)) }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Background Battery Optimization", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)
                        Text("Detected Device: ${oemInfo.brandName}", fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBatteryExempt) TealDark.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBatteryExempt) "Unrestricted" else "Optimized",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBatteryExempt) TealDark else WarningAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    "Aggressive OEM task managers can kill background mesh beacons when the screen is locked. Granting unrestricted battery prevents device-to-device relay drops.",
                    fontSize = 11.sp,
                    color = MutedGray,
                    lineHeight = 15.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            oemHelper.requestIgnoreBatteryOptimization(context)
                            isBatteryExempt = oemHelper.isBatteryOptimizationIgnored(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Request Unrestricted", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            oemHelper.openAppDetailsSettings(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open App Settings", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                // Manufacturer-Specific Step-by-Step Guidance
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CanvasBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Steps for ${oemInfo.brandName}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        oemInfo.guidanceSteps.forEachIndexed { idx, step ->
                            Text("${idx + 1}. $step", fontSize = 10.sp, color = InkText, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }

        // Phone Manufacturer Built-In Emergency SOS Guide
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Manufacturer Built-In Emergency SOS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NavyPrimary)

                Text(
                    "All major Android phones include an official native Emergency SOS feature (usually activated by pressing the power button 5 times). We recommend configuring both:",
                    fontSize = 11.sp,
                    color = MutedGray,
                    lineHeight = 15.sp
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CanvasBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Official Setup Guide (${oemInfo.brandName}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                        Text(oemInfo.nativeSosGuide, fontSize = 10.sp, color = InkText, lineHeight = 14.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyPrimary.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Honest Platform Notice: Android OS intentionally prohibits third-party apps from intercepting or overriding the phone manufacturer's native Emergency SOS. Setting up your phone's built-in SOS enables cellular emergency calls/SMS when cell networks are reachable, while ResQhunT provides offline mesh relay and Quick Settings one-tap broadcast when cellular towers are down or overloaded.",
                        fontSize = 10.sp,
                        color = MutedGray,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
